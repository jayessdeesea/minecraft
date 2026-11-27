package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.JournalCodec;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.Sha;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One test's world, all under its own directory: a bare origin, a clone {@code a} with a second worktree {@code b},
 * the integrator's landing worktree, the state directory (journal, lock, verify log), git's config kept from the
 * machine's, a clock that moves when told and a fake verify that writes its BUILD line to the log, never stdout. The
 * journal is cut over at the first commit, so the audit keeps every invariant.
 */
final class ScratchRepo {

    /** Verified unless the candidate holds a file {@code fail}. */
    static final String VERIFY = "if [ -e fail ]; then echo '[INFO] BUILD FAILURE' > \"$VERIFY_LOG\"; exit 1; fi; "
            + "echo '[INFO] BUILD SUCCESS' > \"$VERIFY_LOG\"";

    /**
     * A long-running child of a test's sh, started in the background: a native Windows program, as Maven's java is - an
     * MSYS program forked and exec'd from sh has a fork stub that is gone for its Windows parent, so no tree finds it.
     */
    static final String NATIVE_CHILD = "\"${SystemRoot:-$SYSTEMROOT}/System32/PING.EXE\" -n 120 127.0.0.1 > /dev/null &";

    final Path root;
    final Path origin;
    final Path a;
    final Path b;
    final Path landingTree;
    final Path state;
    final Map<String, String> env;
    final TestClock clock = new TestClock(Instant.parse("2026-11-26T12:00:00Z"));
    final Random random;
    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();
    final PrintStream out = new PrintStream(printed, true, StandardCharsets.UTF_8);
    final Sha init;
    String verify = VERIFY;
    Duration verifyTimeout = Duration.ofMinutes(1);
    Duration poll = Duration.ofSeconds(15);
    /** git as the tool runs it: git itself, unless a row stands a hanging one in. */
    List<String> gitProgram = List.of("git");
    Duration gitTimeout = Duration.ofMinutes(2);
    /** What ends a timed-out call's tree: by its parent chain in this JVM, shared by four tests at once. */
    Children children = new ProcessTree();
    Steps steps = new Steps() {
    };
    private final java.util.concurrent.atomic.AtomicInteger messages = new java.util.concurrent.atomic.AtomicInteger();

    ScratchRepo(Path root, long seed) {
        this.root = root;
        this.random = new Random(seed);
        this.origin = root.resolve("origin.git");
        this.a = root.resolve("a");
        this.b = root.resolve("b");
        this.landingTree = root.resolve("landing-tree");
        this.state = root.resolve("state");
        Path gitconfig = root.resolve("gitconfig");
        write(gitconfig, "[user]\n\tname = t\n\temail = t@t\n[core]\n\tautocrlf = false\n[init]\n\tdefaultBranch = main\n");
        this.env = Map.of("GIT_CONFIG_GLOBAL", gitconfig.toString(), "GIT_CONFIG_NOSYSTEM", "1");
        git(root).ok("init", "-q", "--bare", "-b", "main", origin.toString());
        git(root).ok("clone", "-q", origin.toString(), a.toString());
        write(a.resolve("x"), "1\n2\n3\n");
        write(a.resolve("y"), "y\n");
        git(a).ok("add", ".");
        git(a).ok("commit", "-q", "-m", "init");
        git(a).ok("push", "-q", "origin", "main");
        git(a).ok("worktree", "add", "-q", "-b", "feat-b", b.toString(), "origin/main");
        this.init = git(a).resolve("HEAD").orElseThrow();
        write(root.resolve("pre-push"), "#!/bin/sh\nexit 0\n");
        if (run(a, Poke.NONE, "cutover", init.value()) != 0) {
            throw new IllegalStateException("the scratch journal was not cut over: " + printed());
        }
    }

    Git git(Path dir) {
        return new Git(dir, Git.Tool.git(env, new ProcessTree()));
    }

    /** The tool; a stand-in git run by sh is told not to glob its arguments, which MSYS does to a Windows caller's. */
    Git.Tool tool() {
        Map<String, String> all = new java.util.HashMap<>(env);
        if (!gitProgram.equals(List.of("git"))) {
            all.put("MSYS", "noglob");
        }
        return new Git.Tool(gitProgram, all, gitTimeout, gitTimeout, children);
    }

    void write(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The config; the verify a script file of its own - Windows quoting mangles quotes in a {@code sh -c} argument. */
    synchronized Config config() {
        Path hook = root.resolve("pre-push");
        Path script = root.resolve("verify-" + Integer.toHexString(verify.hashCode()) + ".sh");
        if (!Files.exists(script)) {
            write(script, verify + "\n");
        }
        return new Config(a, "origin", landingTree, state.resolve("journal.jsonl"), state.resolve("integrator.lock"),
                List.of("sh", script.toString()), verifyTimeout, state.resolve("verify.log"), List.of(), poll, Optional.of(hook),
                sha256(hook), Optional.of(init), "landing", "landing@localhost", gitTimeout, gitTimeout);
    }

    /** This world's config as the tool reads it from a file, for a landing run in a JVM of its own; {@code verify} its command. */
    Path configFile(String verify) {
        Config c = config();
        String text = String.join("\n",
                "repo=" + slash(c.repo()), "worktree=" + slash(c.worktree()), "journal=" + slash(c.journal()),
                "lock=" + slash(c.lock()), "verify=" + verify, "verify.log=" + slash(c.verifyLog()), "poke=",
                "hook=" + slash(c.hook().orElseThrow()), "hook.hash=" + c.hookHash(),
                "jar.landing-change=" + init.value()) + "\n";
        Path file = root.resolve("config");
        write(file, text);
        return file;
    }

    /** A path as a properties file holds it: a backslash there is an escape. */
    static String slash(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/');
    }

    Landing cli(Path cwd, Poke poke) {
        return new Landing(config(), tool(), clock, random, out, cwd, clock::advance, steps, poke);
    }

    int run(Path cwd, Poke poke, String... args) {
        return cli(cwd, poke).run(args);
    }

    int integrate() {
        return run(a, Poke.NONE, "integrate");
    }

    /** The poke that runs the integrator at once, in this thread. */
    Poke inline() {
        return this::integrate;
    }

    /** {@code land} from {@code worktree} with {@code message}, poked by {@code poke}, with {@code more} arguments. */
    int land(Path worktree, String message, Poke poke, String... more) {
        Path file = root.resolve("message-" + messages.incrementAndGet());
        write(file, message + "\n");
        List<String> args = new ArrayList<>(List.of("land", file.toString()));
        args.addAll(List.of(more));
        return run(worktree, poke, args.toArray(String[]::new));
    }

    /** {@code land} with the message file's bytes exactly as given. */
    int landExact(Path worktree, String message, Poke poke) {
        Path file = root.resolve("message-" + messages.incrementAndGet());
        write(file, message);
        return run(worktree, poke, "land", file.toString());
    }

    /**
     * A request made by hand, as a submitter that gets it wrong would: refs/queue/{@code id} at a commit of a's HEAD's
     * tree with {@code parents} and a message carrying {@code trailers}.
     */
    void rawRequest(String id, List<Sha> parents, String trailers) {
        Git git = git(a);
        List<String> args = new ArrayList<>(List.of("commit-tree", git.tree(git.resolve("HEAD").orElseThrow()).value()));
        parents.forEach(p -> args.addAll(List.of("-p", p.value())));
        args.addAll(List.of("-F", "-"));
        Git.Result made = git.run(args, "a hand-made request\n\n" + trailers + "\n");
        if (!made.ok() || !git.createRef("refs/queue/" + id, Sha.parse(made.line()))) {
            throw new IllegalStateException("no hand-made request: " + made.err());
        }
    }

    /** {@code land} that only queues: no poke, no wait. */
    int queue(Path worktree, String message) {
        return land(worktree, message, Poke.NONE, "--timeout", "0");
    }

    /** The ids queued so far, in the order printed. */
    List<String> queuedIds() {
        List<String> ids = new ArrayList<>();
        Matcher m = Pattern.compile("queued (\\S+)").matcher(printed());
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    String lastQueued() {
        return queuedIds().getLast();
    }

    /** A file of main as origin has it, or none. */
    Optional<String> onMain(String path) {
        Git.Result r = git(origin).run(List.of("show", "main:" + path));
        return r.ok() ? Optional.of(r.out()) : Optional.empty();
    }

    List<String> subjects() {
        return git(origin).ok("log", "--format=%s", "main").lines().toList();
    }

    Sha mainTip() {
        return git(origin).resolve("main").orElseThrow();
    }

    List<String> requestRefs() {
        return git(a).ok("for-each-ref", "--format=%(refname)", "refs/queue", "refs/queue-withdraw").lines().toList();
    }

    List<JournalRecord> journal() {
        return new Journal(state.resolve("journal.jsonl"), new JournalCodec()).read();
    }

    long faults(io.github.jayessdeesea.minecraft.landing.FaultKind kind) {
        return journal().stream().filter(r -> r instanceof JournalRecord.Fault f && f.kind() == kind).count();
    }

    int audit() {
        return run(a, Poke.NONE, "audit");
    }

    String printed() {
        return printed.toString(StandardCharsets.UTF_8);
    }

    /** A commit pushed to origin from outside the queue, from a clone of its own; its sha. */
    Sha outsidePush(String file) {
        return outsidePush(file, "outside " + file);
    }

    /** The same, with {@code message} as the commit's. */
    Sha outsidePush(String file, String message) {
        Path c = root.resolve("outside");
        if (!Files.isDirectory(c)) {
            git(root).ok("clone", "-q", origin.toString(), c.toString());
        }
        git(c).ok("pull", "-q", "--ff-only");
        write(c.resolve(file), file + "\n");
        git(c).ok("add", file);
        write(root.resolve("outside-message"), message + "\n");
        git(c).ok("commit", "-q", "-F", root.resolve("outside-message").toString());
        git(c).ok("push", "-q", "origin", "main");
        return git(c).resolve("HEAD").orElseThrow();
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
