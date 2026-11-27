package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.Audit;
import io.github.jayessdeesea.minecraft.landing.EntryId;
import io.github.jayessdeesea.minecraft.landing.EntryState;
import io.github.jayessdeesea.minecraft.landing.Entries;
import io.github.jayessdeesea.minecraft.landing.ExitCode;
import io.github.jayessdeesea.minecraft.landing.Integrator;
import io.github.jayessdeesea.minecraft.landing.JournalCodec;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.RunToken;
import io.github.jayessdeesea.minecraft.landing.Sha;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * The {@code landing} tool: {@code land <message file> [--timeout <minutes>]} (or {@code submit}), {@code integrate},
 * {@code state <id>}, {@code audit}, {@code withdraw <id>}, {@code resume <reason>}, {@code may-push <sha> <remote-sha>},
 * {@code adopt <sha> <reason>} and {@code cutover <sha>}. Exits as {@link ExitCode} says: 2 for arguments it cannot
 * read, 6 when landing itself failed - never 1, which is an entry's FAILED. Whatever writes the journal takes the lock
 * first, and finding it held exits 4.
 */
public final class Landing {

    private final Config config;
    private final Git.Tool tool;
    private final Clock clock;
    private final RandomGenerator random;
    private final PrintStream out;
    private final Path cwd;
    private final Sleeper sleeper;
    private final Steps steps;
    private final Poke poke;
    private final Journal journal;
    private final IntegratorLock lock;
    private final Optional<Path> taskLog;

    public Landing(Config config, Git.Tool tool, Clock clock, RandomGenerator random, PrintStream out, Path cwd,
                   Sleeper sleeper, Steps steps, Poke poke) {
        this(config, tool, clock, random, out, cwd, sleeper, steps, poke, Optional.empty());
    }

    /** With the task's log, which the integrator that takes the lock keeps to its size. */
    public Landing(Config config, Git.Tool tool, Clock clock, RandomGenerator random, PrintStream out, Path cwd,
                   Sleeper sleeper, Steps steps, Poke poke, Optional<Path> taskLog) {
        this.taskLog = taskLog;
        this.config = config;
        this.tool = tool;
        this.clock = clock;
        this.random = random;
        this.out = out;
        this.cwd = cwd;
        this.sleeper = sleeper;
        this.steps = steps;
        this.poke = poke;
        this.journal = new Journal(config.journal(), new JournalCodec());
        this.lock = new IntegratorLock(config.lock());
    }

    /** The environment, the working directory and the jar this runs from, read once, here; everything after is passed in. */
    public static void main(String[] args) {
        System.exit(launch(args, System.getenv(), System.out));
    }

    /** The working directory and the jar's place read inside a try of their own: a failure there is 6, as any other. */
    static int launch(String[] args, Map<String, String> env, PrintStream out) {
        Path cwd;
        Optional<Path> jar;
        try {
            cwd = Path.of("").toAbsolutePath();
            jar = home();
        } catch (Throwable t) {
            out.println("landing itself failed: " + t + " - an entry, if any, may still be queued: landing state <id>");
            return ExitCode.LANDING_FAILED;
        }
        return launch(args, env, cwd, out, jar);
    }

    /** The jar this process runs from, if it runs from one: the installed tool's home, where its config and log are. */
    private static Optional<Path> home() {
        try {
            Path code = Path.of(Landing.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(code) && code.toString().endsWith(".jar") ? Optional.of(code) : Optional.empty();
        } catch (java.net.URISyntaxException | SecurityException | NullPointerException e) {
            // no jar to name: the config must then be LANDING_CONFIG's, or the launch is 6, "no config named"
            return Optional.empty();
        }
    }

    /**
     * The one {@code try}: this process into its job before anything is read or started, the config, the command. Any
     * {@code Throwable} is landing itself failing: 6, never the JVM's own 1, which reads as FAILED. With a home (the jar),
     * integrate and job-check also write what they print to {@code landing-task.log} beside it.
     */
    public static int launch(String[] args, Map<String, String> env, Path cwd, PrintStream out, Optional<Path> jar) {
        return launch(args, env, cwd, out, jar, Job::enter);
    }

    static int launch(String[] args, Map<String, String> env, Path cwd, PrintStream out, Optional<Path> jar,
                      Supplier<Children> enter) {
        Optional<Path> taskLog = jar.filter(j -> args.length > 0 && List.of("integrate", "job-check").contains(args[0]))
                .map(j -> j.resolveSibling("landing-task.log"));
        PrintStream said = taskLog.map(log -> TaskLog.tee(out, log, System.err)).orElse(out);
        try {
            Children job = enter.get();
            Path file = Config.file(Optional.ofNullable(env.get("LANDING_CONFIG")), jar);
            Config config = Config.load(file);
            if (args.length > 0 && args[0].equals("install")) {
                return Installer.install(config, file, Arrays.asList(args).subList(1, args.length), said);
            }
            Map<String, String> children = childrensEnvironment(config, env);
            Git.Tool git = new Git.Tool(List.of(Programs.resolve("git", config.toolPath(), Files::isRegularFile)), children,
                    config.gitTimeout(), config.gitRemoteTimeout(), job);
            if (args.length == 2 && args[0].equals("job-check")) {
                return JobCheck.run(Path.of(args[1]), config, git, children, job, said);
            }
            return new Landing(config, git, Clock.systemUTC(), RandomGenerator.getDefault(), said, cwd, Landing::sleep,
                    new Steps() {
                    }, new CommandPoke(config.poke(), said, job, CommandPoke.WAIT), taskLog).run(args);
        } catch (Throwable t) {
            said.println("landing itself failed: " + t + " - an entry, if any, may still be queued: landing state <id>");
            return ExitCode.LANDING_FAILED;
        }
    }

    /**
     * What git and the verify are started with: PATH the config's tool.path, the running JVM's bin, then the inherited
     * PATH; JAVA_HOME the running JVM's home - so the verify builds on the JDK the task names, whatever the user's own.
     */
    private static Map<String, String> childrensEnvironment(Config config, Map<String, String> env) {
        String inherited = env.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("PATH")).map(Map.Entry::getValue)
                .findFirst().orElse("");
        Path javaHome = Path.of(System.getProperty("java.home"));
        List<String> path = new java.util.ArrayList<>(config.toolPath().stream().map(Path::toString).toList());
        path.add(javaHome.resolve("bin").toString());
        if (!inherited.isEmpty()) {
            path.add(inherited);
        }
        return Map.of("PATH", String.join(java.io.File.pathSeparator, path), "JAVA_HOME", javaHome.toString());
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", e);
        }
    }

    /** The command's exit: its arguments read first - one it cannot read is usage - then run, anything thrown a 6. */
    public int run(String... args) {
        IntSupplier command;
        try {
            command = command(args);
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
            return usage();
        }
        try {
            return command.getAsInt();
        } catch (RuntimeException e) {
            out.println("landing itself failed: " + e + " - an entry, if any, may still be queued: landing state <id>");
            return ExitCode.LANDING_FAILED;
        }
    }

    private IntSupplier command(String... args) {
        if (args.length == 0) {
            return this::usage;
        }
        List<String> rest = Arrays.asList(args).subList(1, args.length);
        return switch (args[0]) {
            case "land", "submit" -> land(rest);
            case "integrate" -> rest.isEmpty() ? () -> integrator().integrate() : this::usage;
            case "state" -> {
                EntryId id = one(rest, EntryId::parse);
                yield () -> state(id);
            }
            case "audit" -> rest.isEmpty() ? this::audit : this::usage;
            case "withdraw" -> {
                EntryId id = one(rest, EntryId::parse);
                yield () -> withdraw(id);
            }
            case "resume" -> {
                String reason = one(rest, r -> r);
                yield () -> write(token -> new JournalRecord.Resumed(token, clock.instant(), reason));
            }
            case "adopt" -> rest.size() == 2 ? () -> writeFor(rest.getFirst(), (token, sha) ->
                    new JournalRecord.Adopted(token, clock.instant(), sha, rest.get(1))) : this::usage;
            case "cutover" -> rest.size() == 1 ? () -> writeFor(rest.getFirst(), (token, sha) ->
                    new JournalRecord.Cutover(token, clock.instant(), sha)) : this::usage;
            case "may-push" -> {
                if (rest.size() != 2) {
                    yield this::usage;
                }
                Sha sha = Sha.parse(rest.getFirst());
                Sha remoteSha = Sha.parse(rest.get(1));
                yield () -> mayPush(sha, remoteSha);
            }
            default -> this::usage;
        };
    }

    private static <T> T one(List<String> rest, Function<String, T> read) {
        if (rest.size() != 1) {
            throw new IllegalArgumentException("one argument, not " + rest.size());
        }
        return read.apply(rest.getFirst());
    }

    private int usage() {
        out.println("usage: landing land <message file> [--timeout <minutes>] | integrate | state <id> | audit | install ..."
                + " | withdraw <id> | resume <reason> | may-push <sha> <remote-sha> | adopt <sha> <reason> | cutover <sha>");
        return ExitCode.USAGE;
    }

    private Git repo() {
        return new Git(config.repo(), tool);
    }

    private IntegratorRun integrator() {
        List<String> verify = Programs.command(config.verify(), config.toolPath(), Files::isRegularFile);
        return new IntegratorRun(config, repo(), journal, lock, new Verifier(verify, config.verifyTimeout(),
                config.verifyLog(), tool.env(), tool.children()), clock, random, steps, out, taskLog);
    }

    private IntSupplier land(List<String> rest) {
        if (rest.size() != 1 && !(rest.size() == 3 && rest.get(1).equals("--timeout"))) {
            return this::usage;
        }
        Optional<Duration> timeout = rest.size() == 3 ? Optional.of(Duration.ofMinutes(Long.parseLong(rest.get(2))))
                : Optional.empty();
        Path message = cwd.resolve(rest.getFirst());
        return () -> new Submitter(new Git(cwd, tool), config.remote(), journal, poke, sleeper, clock, random, config.poll(),
                out).land(message, timeout);
    }

    /**
     * The entry's state as a waiter's exit, main as the integrator last fetched it - or, the entry verified and
     * unsettled, as this worktree fetches it once for itself, as a waiter does.
     */
    private int state(EntryId id) {
        Git git = repo();
        List<JournalRecord> records = journal.read();
        Gather gather = new Gather(git, config.remote(), clock);
        Sha tip = gather.mainTip();
        if (Entries.pushed(id, records)) {
            // verified, maybe pushed: the integrator's fetch may be what failed, so look once, into this worktree's own
            // FETCH_HEAD - the shared ref is the lock holder's to write
            Git here = new Git(cwd, tool);
            if (here.remote().run(List.of("fetch", "-q", "--refmap=", config.remote(), "refs/heads/main")).ok()) {
                tip = here.resolve("FETCH_HEAD").orElse(tip);
            }
        }
        EntryState state = Entries.state(id, records, gather.landings(tip));
        out.println(id + ": " + state);
        return ExitCode.of(state, Integrator.halted(records).isPresent());
    }

    /**
     * A watch's audit takes the lock for its fetch and gather, so refs and journal are one moment, and journals what is
     * new; with the lock held by an integrator mid-step - it keeps main fetched - I7 is not checked and nothing is
     * written. 0 clean, 1 a violation.
     */
    private int audit() {
        Git git = repo();
        Gather gather = new Gather(git, config.remote(), clock);
        Auditor auditor = new Auditor(gather, journal, config, clock, out);
        Optional<IntegratorLock.Held> held = lock.tryAcquire();
        Audit.Report report;
        if (held.isPresent()) {
            try (IntegratorLock.Held h = held.get()) {
                if (!fetch(git)) {
                    out.println("note: the fetch failed - audited as main was last fetched");
                }
                report = auditor.audit(Optional.of(RunToken.next(random)), true);
            }
        } else {
            report = auditor.audit(Optional.empty(), false);
        }
        return report.violations().isEmpty() ? ExitCode.OK : ExitCode.REFUSED;
    }

    private boolean fetch(Git git) {
        return git.remote().run(List.of("fetch", "-q", config.remote(), "+refs/heads/main:refs/remotes/" + config.remote()
                + "/main")).ok();
    }

    /** A withdrawal request ({@code refs/queue-withdraw/<id>}), for the integrator to settle; its one writer stays. */
    private int withdraw(EntryId id) {
        Git git = repo();
        Sha at = git.resolve(Gather.QUEUE + id).or(() -> git.resolve("HEAD")).orElseThrow();
        git.createRef(Gather.WITHDRAW + id, at);
        out.println("withdrawal of " + id + " requested");
        poke.poke();
        return ExitCode.OK;
    }

    /**
     * An operator's record about a commit - adopt, cutover - its commit resolved under the lock, fetching if it must:
     * the shared ref has one writer, the lock's holder. Held by another, 4 and nothing touched; no such commit, usage.
     */
    private int writeFor(String rev, java.util.function.BiFunction<RunToken, Sha, JournalRecord> record) {
        Optional<IntegratorLock.Held> held = lock.tryAcquire();
        if (held.isEmpty()) {
            out.println("an integrator holds " + config.lock() + ": try again when it is done");
            return ExitCode.HALTED;
        }
        try (IntegratorLock.Held h = held.get()) {
            Optional<Sha> sha = commit(rev);
            if (sha.isEmpty()) {
                return usage();
            }
            JournalRecord written = record.apply(RunToken.next(random), sha.get());
            journal.append(written);
            out.println("journaled " + written);
            return ExitCode.OK;
        }
    }

    /** An operator's record - resume, adopt, cutover - written under the lock. */
    private int write(Function<RunToken, JournalRecord> record) {
        Optional<IntegratorLock.Held> held = lock.tryAcquire();
        if (held.isEmpty()) {
            out.println("an integrator holds " + config.lock() + ": try again when it is done");
            return ExitCode.HALTED;
        }
        try (IntegratorLock.Held h = held.get()) {
            JournalRecord written = record.apply(RunToken.next(random));
            journal.append(written);
            out.println("journaled " + written);
            return ExitCode.OK;
        }
    }

    /** The commit {@code rev} names, main fetched first when it is not here yet - an outside push is on origin's main. */
    private Optional<Sha> commit(String rev) {
        Git git = repo();
        Optional<Sha> sha = git.resolve(rev + "^{commit}").or(() -> {
            fetch(git);
            return git.resolve(rev + "^{commit}");
        });
        if (sha.isEmpty()) {
            out.println("no commit " + rev);
        }
        return sha;
    }

    /**
     * The pre-push hook's question: an update of main may go only while an integrator holds the lock and the journal's
     * last record is Verified for that sha on that parent. 0 yes, 1 no.
     */
    private int mayPush(Sha sha, Sha remoteSha) {
        Optional<IntegratorLock.Held> held = lock.tryAcquire();
        if (held.isPresent()) {
            held.get().close();
            out.println("refused: no integrator holds the lock - land through the queue (landing land <message file>)");
            return ExitCode.REFUSED;
        }
        List<JournalRecord> records = journal.read();
        boolean verified = !records.isEmpty() && records.getLast() instanceof JournalRecord.Verified v
                && v.candidate().equals(sha) && v.parent().equals(remoteSha);
        if (!verified) {
            out.println("refused: " + sha + " on " + remoteSha + " is not the candidate last verified");
        }
        return verified ? ExitCode.OK : ExitCode.REFUSED;
    }
}
