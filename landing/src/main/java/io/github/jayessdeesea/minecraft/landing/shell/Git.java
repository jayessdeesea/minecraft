package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Commit;
import io.github.jayessdeesea.minecraft.landing.EntryId;
import io.github.jayessdeesea.minecraft.landing.Sha;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * git, run in one directory: an argument list, never a command line; its exit code and what it printed. State is
 * observed by asking git again - a ref resolved, an ancestry tested - never read from an error's text, which is kept
 * only to be said.
 */
public final class Git {

    /** How long git's output may take to close once git has exited: past it, a child holds it, and the answer is not whole. */
    private static final long DRAINED_SECONDS = 10;
    /** A commit for {@link #log}: sha, parents, Landing-Id and Landing-Source trailers - fields and values separated. */
    private static final String FORMAT = "%H%x1f%P%x1f%(trailers:key=Landing-Id,valueonly,separator=%x1d)%x1f"
            + "%(trailers:key=Landing-Source,valueonly,separator=%x1d)%x1e";

    /**
     * How git is run: its program ({@code git}, or a test's stand-in), {@code env} over the environment's, how long a
     * local call and a call to the remote (fetch, push) may take, and what ends a call's process tree at its time.
     */
    public record Tool(List<String> program, Map<String, String> env, Duration local, Duration remote, Children children) {
        /** Ten minutes for the remote, two for the rest. */
        public static final Duration LOCAL = Duration.ofMinutes(2);
        public static final Duration REMOTE = Duration.ofMinutes(10);

        public Tool {
            program = List.copyOf(program);
            env = Map.copyOf(env);
        }

        /** git as installed, with {@code env}, its calls ended by {@code children}. */
        public static Tool git(Map<String, String> env, Children children) {
            return new Tool(List.of("git"), env, LOCAL, REMOTE, children);
        }
    }

    private final Path dir;
    private final Tool tool;
    private final Duration timeout;

    /** git in {@code dir}, run as {@code tool} says, local calls' time. */
    public Git(Path dir, Tool tool) {
        this(dir, tool, tool.local());
    }

    private Git(Path dir, Tool tool, Duration timeout) {
        this.dir = dir;
        this.tool = tool;
        this.timeout = timeout;
    }

    public Path dir() {
        return dir;
    }

    public Tool tool() {
        return tool;
    }

    /** The same git in another directory. */
    public Git at(Path other) {
        return new Git(other, tool, timeout);
    }

    /** The same git with {@code more} over its environment (an author for a commit). */
    public Git with(Map<String, String> more) {
        Map<String, String> all = new HashMap<>(tool.env());
        all.putAll(more);
        return new Git(dir, new Tool(tool.program(), all, tool.local(), tool.remote(), tool.children()), timeout);
    }

    /** The same git given the remote's time, for a fetch or a push. */
    public Git remote() {
        return new Git(dir, tool, tool.remote());
    }

    /** What git said: its exit code (-1 if killed at its time), its output and its errors. */
    public record Result(int exit, String out, String err, boolean timedOut) {
        public boolean ok() {
            return exit == 0;
        }

        /** The output's one line, its ending stripped. */
        public String line() {
            return out.strip();
        }
    }

    /** A git that should have worked and did not: its arguments, exit and what it said. */
    public static final class Failed extends RuntimeException {
        Failed(String message) {
            super(message);
        }
    }

    public Result run(List<String> args) {
        return run(args, null);
    }

    /**
     * git with {@code args}, given {@code input} on its standard input if any. Never asks: no terminal prompt, no
     * credential window. An answer is whole or a failure: past its time, or with its output held open ten seconds after
     * git exited, its process tree is ended and it says it timed out. A tree not proven gone throws
     * {@link Children.NotGone} - not a {@link Failed}, so nothing takes it for a step's ordinary failure.
     */
    public Result run(List<String> args, String input) {
        List<String> command = new ArrayList<>(tool.program());
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(dir.toFile());
        builder.environment().putAll(tool.env());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GCM_INTERACTIVE", "never");
        try {
            Process p = builder.start();
            CompletableFuture<byte[]> out = drain(p.getInputStream());
            CompletableFuture<byte[]> err = drain(p.getErrorStream());
            try (OutputStream in = p.getOutputStream()) {
                if (input != null) {
                    in.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                end(p, args, "past its " + timeout);
                return new Result(-1, "", "killed after " + timeout, true);
            }
            String said = text(out);
            String complained = text(err);
            if (said == null || complained == null) {
                end(p, args, "its output held open after it exited");
                return new Result(-1, "", "output held open after git exited", true);
            }
            return new Result(p.exitValue(), said, complained, false);
        } catch (IOException e) {
            throw new UncheckedIOException("git " + args + " in " + dir + " did not run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted waiting on git " + args, e);
        }
    }

    /** git with {@code args}, which must work: its output. */
    public String ok(String... args) {
        Result r = run(List.of(args));
        if (!r.ok()) {
            throw failed(List.of(args), r);
        }
        return r.out();
    }

    /** The object {@code rev} names, or none. */
    public Optional<Sha> resolve(String rev) {
        Result r = run(List.of("rev-parse", "--verify", "--quiet", rev));
        if (r.exit() == 0) {
            return Optional.of(Sha.parse(r.line()));
        }
        if (r.exit() == 1) {
            return Optional.empty();
        }
        throw failed(List.of("rev-parse", rev), r);
    }

    /**
     * Whether {@code commit} names a commit in the repository: {@code rev-parse --verify --quiet} says 0 present, 1
     * absent, and anything else - a timeout, a broken repository - throws. ({@code cat-file -e} exits 128 for both an
     * absent commit and a broken repository, so it cannot be asked.)
     */
    public boolean exists(String commit) {
        return resolve(commit + "^{commit}").isPresent();
    }

    /** Whether {@code ancestor} is an ancestor of {@code of} (a commit its own). */
    public boolean isAncestor(Sha ancestor, Sha of) {
        Result r = run(List.of("merge-base", "--is-ancestor", ancestor.value(), of.value()));
        if (r.exit() == 0 || r.exit() == 1) {
            return r.exit() == 0;
        }
        throw failed(List.of("merge-base", "--is-ancestor", ancestor.value(), of.value()), r);
    }

    public Sha tree(Sha commit) {
        return resolve(commit.value() + "^{tree}").orElseThrow(() -> new Failed("no tree for " + commit));
    }

    /** The commits {@code revs} name (a range, or one commit and its history), oldest first. */
    public List<Commit> log(String... revs) {
        List<String> args = new ArrayList<>(List.of("log", "--reverse", "--format=" + FORMAT));
        args.addAll(Arrays.asList(revs));
        args.add("--");
        Result r = run(args);
        if (!r.ok()) {
            throw failed(args, r);
        }
        List<Commit> commits = new ArrayList<>();
        for (String record : r.out().split("\u001e")) {
            // only the newline git puts between records: strip() would take the separators too, which Java counts as space
            String fields = record.replace("\n", "");
            if (!fields.isEmpty()) {
                commits.add(parsed(fields));
            }
        }
        return commits;
    }

    /** The commit {@code rev} names, if it names one. */
    public Optional<Commit> commit(String rev) {
        Optional<Sha> sha = resolve(rev + "^{commit}");
        return sha.map(s -> log("-1", s.value()).getFirst());
    }

    private static Commit parsed(String record) {
        String[] f = record.split("\u001f", -1);
        List<Sha> parents = f[1].isBlank() ? List.of() : Arrays.stream(f[1].trim().split(" ")).map(Sha::parse).toList();
        return new Commit(Sha.parse(f[0]), parents, values(f[2]), values(f[3]));
    }

    private static List<String> values(String field) {
        return field.isBlank() ? List.of() : Arrays.stream(field.split("\u001d")).map(String::strip).toList();
    }

    /** The ids of the refs under {@code prefix} ({@code refs/queue/}); a ref there that names no entry is skipped. */
    public Set<EntryId> refs(String prefix) {
        Set<EntryId> ids = new LinkedHashSet<>();
        for (String ref : refNames(prefix)) {
            entry(ref.substring(prefix.length())).ifPresent(ids::add);
        }
        return ids;
    }

    /** The refs under {@code prefix} that name no entry: never touched, named by the audit. */
    public List<String> strays(String prefix) {
        return refNames(prefix).stream().filter(ref -> entry(ref.substring(prefix.length())).isEmpty()).toList();
    }

    private List<String> refNames(String prefix) {
        return ok("for-each-ref", "--format=%(refname)", prefix).lines().filter(ref -> !ref.isBlank()).toList();
    }

    private static Optional<EntryId> entry(String name) {
        try {
            return Optional.of(EntryId.parse(name));
        } catch (IllegalArgumentException notAnEntry) {
            return Optional.empty();
        }
    }

    /** Makes {@code ref} point at {@code sha} if no such ref is; whether it made it. */
    public boolean createRef(String ref, Sha sha) {
        Result r = run(List.of("update-ref", ref, sha.value(), ""));
        if (r.ok()) {
            return true;
        }
        if (resolve(ref).isPresent()) {
            return false;
        }
        throw failed(List.of("update-ref", ref, sha.value(), ""), r);
    }

    /** Takes {@code ref} away, if it is there. */
    public void deleteRef(String ref) {
        if (resolve(ref).isPresent()) {
            ok("update-ref", "-d", ref);
        }
    }

    private Failed failed(List<String> args, Result r) {
        return new Failed("git " + String.join(" ", args) + " in " + dir + " exited " + r.exit() + ": " + r.err().strip());
    }

    /** The stream read to its end, or none: not closed ten seconds after git exited, or its read failed. */
    private static String text(CompletableFuture<byte[]> bytes) {
        try {
            return new String(bytes.get(DRAINED_SECONDS, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted reading git", e);
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    private void end(Process p, List<String> args, String why) {
        if (!tool.children().kill(p.toHandle())) {
            throw new Children.NotGone("git " + String.join(" ", args) + " " + why + ": its processes not proven gone");
        }
    }

    private static CompletableFuture<byte[]> drain(InputStream stream) {
        CompletableFuture<byte[]> bytes = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                bytes.complete(stream.readAllBytes());
            } catch (IOException e) {
                bytes.completeExceptionally(e);
            }
        });
        return bytes;
    }
}
