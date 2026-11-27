package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.EntryId;
import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.JournalCodec;
import io.github.jayessdeesea.minecraft.landing.JournalFormatException;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.RunToken;
import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shell's parts one at a time: the journal, the lock, the verifier, git's reading of commits, the config. */
class ShellPartsTest {

    private static final RunToken RUN = new RunToken("00000000000000a1");
    private static final Instant T0 = Instant.parse("2026-11-26T12:00:00Z");

    @TempDir
    Path dir;

    private JournalRecord seen(int n) {
        return new JournalRecord.SeenMain(RUN, T0, new Sha(String.format("%040x", n)));
    }

    @Test
    void theJournalReadsBackWhatWasAppendedInOrder() {
        Journal journal = new Journal(dir.resolve("state/journal.jsonl"), new JournalCodec());
        assertEquals(List.of(), journal.read());
        journal.append(seen(1));
        journal.append(seen(2));
        assertEquals(List.of(seen(1), seen(2)), journal.read());
    }

    /** A crash mid-append leaves a line with no ending: it was never written, so it is not read, and is cut off. */
    @Test
    void aTornLastLineIsNotARecordAndTheNextAppendCutsItOff() throws IOException {
        Path file = dir.resolve("journal.jsonl");
        Journal journal = new Journal(file, new JournalCodec());
        journal.append(seen(1));
        Files.writeString(file, "{\"type\":\"SeenMain\",\"ru", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(List.of(seen(1)), journal.read());
        journal.append(seen(2));
        assertEquals(List.of(seen(1), seen(2)), journal.read());
        assertTrue(Files.readString(file).endsWith("}\n"));
    }

    /** A whole line that is not a record is corruption, and named, not skipped. */
    @Test
    void aCorruptWholeLineFailsLoud() throws IOException {
        Path file = dir.resolve("journal.jsonl");
        Files.writeString(file, "not a record\n");
        assertThrows(JournalFormatException.class, () -> new Journal(file, new JournalCodec()).read());
    }

    @Test
    void theLockIsHeldOnceInAJvmAndTheFileStays() {
        IntegratorLock lock = new IntegratorLock(dir.resolve("state/integrator.lock"));
        try (IntegratorLock.Held held = lock.tryAcquire().orElseThrow()) {
            assertTrue(lock.tryAcquire().isEmpty(), "a second take in the same JVM is held");
        }
        assertTrue(Files.exists(dir.resolve("state/integrator.lock")));
        lock.tryAcquire().orElseThrow().close();
    }

    /**
     * The stale-lock case: two JVMs contend; the second finds it held; the holder is killed hard and the next takes the
     * lock at once - no staleness, no breaking, no heartbeat.
     */
    @Test
    void aKilledHoldersLockIsTakenAtOnce() throws IOException, InterruptedException {
        Path file = dir.resolve("integrator.lock");
        String java = ProcessHandle.current().info().command().orElseThrow();
        Process holder = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), LockHolder.class.getName(),
                file.toString()).redirectErrorStream(true).start();
        try (BufferedReader said = new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8))) {
            assertEquals("held", said.readLine());
            IntegratorLock lock = new IntegratorLock(file);
            assertTrue(lock.tryAcquire().isEmpty(), "held by the other JVM");
            holder.destroyForcibly();
            holder.waitFor();
            long before = System.nanoTime();
            lock.tryAcquire().orElseThrow().close();
            assertTrue(Duration.ofNanos(System.nanoTime() - before).toMillis() < 1000, "taken at once");
        } finally {
            holder.destroyForcibly();
        }
    }

    /** The verify's output is the log Maven wrote and its exit; what it prints is nobody's. */
    @Test
    void theVerifierReadsTheLogAndTheExitAndEchoesNothing() {
        Verifier verifier = new Verifier(List.of("sh", "-c", "echo noise; echo '[INFO] BUILD FAILURE' > \"$VERIFY_LOG\"; exit 3"),
                Duration.ofSeconds(30), dir.resolve("state/verify.log"), Map.of(), new ProcessTree());
        Verifier.Run run = verifier.run(dir);
        assertEquals(List.of("[INFO] BUILD FAILURE"), run.lines());
        assertEquals(3, run.exit());
        assertFalse(run.timedOut());
    }

    @Test
    void aVerifyThatWritesNoLogHasNoLines() {
        Verifier.Run run = new Verifier(List.of("sh", "-c", "echo '[INFO] BUILD SUCCESS'"), Duration.ofSeconds(30),
                dir.resolve("verify.log"), Map.of(), new ProcessTree()).run(dir);
        assertEquals(List.of(), run.lines());
        assertEquals(0, run.exit());
    }

    /** Past its time the verify's whole process tree is killed - its child sleep's pid gone - and it says it timed out. */
    @Test
    void aVerifyPastItsTimeIsKilled() throws IOException {
        Path child = dir.resolve("sleep.pid");
        Path script = dir.resolve("verify.sh");
        Files.writeString(script, ScratchRepo.NATIVE_CHILD + " cat /proc/$!/winpid > '" + ScratchRepo.slash(child)
                + "'; wait; echo '[INFO] BUILD SUCCESS' > \"$VERIFY_LOG\"\n");
        Verifier.Run run = new Verifier(List.of("sh", script.toString()), Duration.ofSeconds(3), dir.resolve("verify.log"),
                Map.of(), new ProcessTree()).run(dir);
        assertTrue(run.timedOut());
        assertEquals(List.of(), run.lines());
        long sleep = Long.parseLong(Files.readString(child).strip());
        assertFalse(ProcessHandle.of(sleep).map(ProcessHandle::isAlive).orElse(false), "the child sleep killed");
    }

    /** A git call past its time is killed with its children and says so; a hang never holds the caller. */
    @Test
    void aGitCallPastItsTimeIsKilled() throws IOException {
        Path child = dir.resolve("git-sleep.pid");
        Path fake = dir.resolve("hang.sh");
        Files.writeString(fake, ScratchRepo.NATIVE_CHILD + " cat /proc/$!/winpid > '" + ScratchRepo.slash(child) + "'; wait\n");
        Git hanging = new Git(dir, new Git.Tool(List.of("sh", fake.toString()), Map.of(), Duration.ofSeconds(2),
                Duration.ofSeconds(2), new ProcessTree()));
        Git.Result r = hanging.run(List.of("status"));
        assertTrue(r.timedOut());
        assertFalse(r.ok());
        assertFalse(ProcessHandle.of(Long.parseLong(Files.readString(child).strip())).map(ProcessHandle::isAlive).orElse(false));
    }

    @Test
    void gitReadsACommitsParentsAndTrailers() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 1);
        String id = "20261126T120000Z-0000abcd";
        s.write(s.a.resolve("z"), "z\n");
        s.git(s.a).ok("add", "z");
        s.write(dir.resolve("msg"), "a subject\n\nbody\n\nLanding-Id: " + id + "\nLanding-Id: other\nLanding-Source: abc\n");
        s.git(s.a).ok("commit", "-q", "-F", dir.resolve("msg").toString());
        var commit = s.git(s.a).commit("HEAD").orElseThrow();
        assertEquals(List.of(s.init), commit.parents());
        assertEquals(List.of(id, "other"), commit.landingIds());
        assertEquals(List.of("abc"), commit.landingSources());
        assertEquals(Optional.empty(), s.git(s.a).commit("refs/queue/" + id));
        assertEquals(List.of(), s.git(s.a).log(s.init.value()).getFirst().landingIds());
    }

    /** A request ref is made create-only: the second submitter of an id finds it taken. */
    @Test
    void aRefIsCreatedOnlyOnce() {
        ScratchRepo s = new ScratchRepo(dir, 1);
        assertTrue(s.git(s.a).createRef("refs/queue/20261126T120000Z-0000abcd", s.init));
        assertFalse(s.git(s.a).createRef("refs/queue/20261126T120000Z-0000abcd", s.init));
        assertEquals(java.util.Set.of(EntryId.parse("20261126T120000Z-0000abcd")), s.git(s.a).refs("refs/queue/"));
        s.git(s.a).deleteRef("refs/queue/20261126T120000Z-0000abcd");
        s.git(s.a).deleteRef("refs/queue/20261126T120000Z-0000abcd");
        assertEquals(java.util.Set.of(), s.git(s.a).refs("refs/queue/"));
    }

    @Test
    void theConfigHasItsDefaultsAndEveryPathOverridable() {
        Properties none = new Properties();
        none.setProperty("repo", "C:/github/jayessdeesea/minecraft");
        none.setProperty("jar.landing-change", String.format("%040x", 7));
        Config defaults = Config.of(none, Path.of("D:/home"));
        // beside the config read: home is one place
        assertEquals(Path.of("D:/home/journal.jsonl"), defaults.journal());
        assertEquals(Path.of("D:/home/integrator.lock"), defaults.lock());
        assertEquals(Path.of("D:/home/verify.log"), defaults.verifyLog());
        assertEquals(Path.of("C:/mc-worktrees/landing"), defaults.worktree());
        assertEquals(List.of("schtasks", "/run", "/tn", "mc-landing"), defaults.poke());
        assertEquals(List.of("sh", "tools/ops/verify.sh"), defaults.verify());
        assertEquals(Duration.ofMinutes(45), defaults.verifyTimeout());
        assertEquals("origin", defaults.remote());
        Properties some = new Properties();
        some.setProperty("repo", "C:/r");
        some.setProperty("journal", "D:/j.jsonl");
        some.setProperty("verify", "sh  -c   true");
        some.setProperty("verify.timeout.minutes", "2");
        Config set = Config.of(some, Path.of("D:/home"));
        assertEquals(Path.of("D:/j.jsonl"), set.journal());
        assertEquals(List.of("sh", "-c", "true"), set.verify());
        assertEquals(Duration.ofMinutes(2), set.verifyTimeout());
        assertEquals(Optional.empty(), set.jarLandingChange());
        assertThrows(IllegalArgumentException.class, () -> Config.of(new Properties(), Path.of("D:/home")), "the repo is required");
    }

    @Test
    void aFailedPokeIsSaidAndNotFatal() {
        var said = new java.io.ByteArrayOutputStream();
        new CommandPoke(List.of("sh", "-c", "exit 1"), new java.io.PrintStream(said, true, StandardCharsets.UTF_8),
                new ProcessTree(), Duration.ofSeconds(30)).poke();
        assertTrue(said.toString(StandardCharsets.UTF_8).contains("the poke failed"), said.toString(StandardCharsets.UTF_8));
        new CommandPoke(List.of("no-such-command-anywhere"), new java.io.PrintStream(said, true, StandardCharsets.UTF_8),
                new ProcessTree(), Duration.ofSeconds(30)).poke();
    }

    /** S6: a poke past its time is ended through Children - its whole tree - and said; it is not fatal. */
    @Test
    void aPokePastItsTimeIsEndedThroughChildren() throws IOException {
        Path script = dir.resolve("poke.sh");
        Files.writeString(script, "exec " + ScratchRepo.NATIVE_CHILD.replace(" &", "") + "\n");
        java.util.List<ProcessHandle> ended = new java.util.ArrayList<>();
        Children recording = root -> {
            ended.add(root);
            return new ProcessTree().kill(root);
        };
        var said = new java.io.ByteArrayOutputStream();
        new CommandPoke(List.of("sh", script.toString()), new java.io.PrintStream(said, true, StandardCharsets.UTF_8), recording,
                Duration.ofSeconds(1)).poke();
        assertEquals(1, ended.size(), "ended through Children");
        assertFalse(ended.getFirst().isAlive());
        assertTrue(said.toString(StandardCharsets.UTF_8).contains("the poke failed"), said.toString(StandardCharsets.UTF_8));
    }

    private static int launch(String[] args, Map<String, String> env, Optional<Path> jar, java.io.PrintStream out, Path cwd) {
        return Landing.launch(args, env, cwd, out, jar, ProcessTree::new);
    }

    /**
     * landing install: the built jar copied to its installed place under the integrator's lock, the commit it was
     * built from, the last landing/ change there and the directories of git, sh and mvn (tool.path) recorded in the
     * config; held, 4 and nothing touched; bad, usage.
     */
    @Test
    void installCopiesTheJarUnderTheLockAndRecordsItsCommits() throws IOException {
        Path config = dir.resolve("state/config");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "repo=" + ScratchRepo.slash(dir) + "\nlock=" + ScratchRepo.slash(dir.resolve("state/lock"))
                + "\njournal=" + ScratchRepo.slash(dir.resolve("state/j.jsonl")) + "\n");
        Path built = dir.resolve("built.jar");
        Files.writeString(built, "a jar");
        Path installed = dir.resolve("state/landing.jar");
        String from = String.format("%040x", 7);
        String change = String.format("%040x", 5);
        var said = new java.io.ByteArrayOutputStream();
        var out = new java.io.PrintStream(said, true, StandardCharsets.UTF_8);
        Map<String, String> env = Map.of("LANDING_CONFIG", config.toString());
        String[] install = {"install", built.toString(), installed.toString(), from, change, "C:\\git\\bin", "C:\\git\\usr\\bin",
                "C:\\maven\\bin"};
        try (IntegratorLock.Held held = new IntegratorLock(dir.resolve("state/lock")).tryAcquire().orElseThrow()) {
            assertEquals(4, launch(install, env, Optional.empty(), out, dir), said.toString(StandardCharsets.UTF_8));
        }
        assertFalse(Files.exists(installed), "held: nothing touched");
        assertEquals(0, launch(install, env, Optional.empty(), out, dir), said.toString(StandardCharsets.UTF_8));
        assertEquals("a jar", Files.readString(installed));
        Config read = Config.load(config);
        assertEquals(Optional.of(Sha.parse(change)), read.jarLandingChange());
        assertEquals(List.of(Path.of("C:\\git\\bin"), Path.of("C:\\git\\usr\\bin"), Path.of("C:\\maven\\bin")), read.toolPath());
        Properties kept = new Properties();
        try (var in = Files.newBufferedReader(config)) {
            kept.load(in);
        }
        assertEquals(from, kept.getProperty("jar.built-from"));
        assertEquals(ScratchRepo.slash(dir), kept.getProperty("repo"), "the rest of the config kept");
        assertEquals(2, launch(new String[]{"install", built.toString(), installed.toString(), "abc", change, "a", "b", "c"}, env,
                Optional.empty(), out, dir));
        assertEquals(2, launch(new String[]{"install", built.toString(), installed.toString(), from, change}, env,
                Optional.empty(), out, dir));
    }

    /** R10: the config is LANDING_CONFIG's; else beside the jar this process runs from; else none, and that is 6. */
    @Test
    void theConfigIsTheEnvironmentsElseBesideTheJar() {
        assertEquals(Path.of("D:/elsewhere/config"), Config.file(Optional.of("D:/elsewhere/config"), Optional.of(Path.of("X/landing.jar"))));
        assertEquals(Path.of("X/config"), Config.file(Optional.empty(), Optional.of(Path.of("X/landing.jar"))));
        IllegalStateException none = assertThrows(IllegalStateException.class, () -> Config.file(Optional.empty(), Optional.empty()));
        assertTrue(none.getMessage().contains("no config named"), none.getMessage());
        var said = new java.io.ByteArrayOutputStream();
        assertEquals(6, launch(new String[]{"audit"}, Map.of(), Optional.empty(), new java.io.PrintStream(said, true,
                StandardCharsets.UTF_8), dir));
        assertTrue(said.toString(StandardCharsets.UTF_8).contains("no config named"), said.toString(StandardCharsets.UTF_8));
    }

    /** R12: a run budget with no room for one attempt - the verify, its kill, two remote calls, the margin - is refused. */
    @Test
    void aBudgetWithNoRoomForOneAttemptIsRefused() {
        Properties p = new Properties();
        p.setProperty("repo", "C:/r");
        p.setProperty("verify.timeout.minutes", "45");
        p.setProperty("git.remote-timeout.minutes", "10");
        p.setProperty("run.budget.minutes", "70");
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> Config.of(p, Path.of("D:/home")));
        assertTrue(refused.getMessage().contains("run.budget.minutes"), refused.getMessage());
        p.setProperty("run.budget.minutes", "71");
        assertEquals(Duration.ofMinutes(71), Config.of(p, Path.of("D:/home")).runBudget());
    }

    /** The directories git, sh and mvn are in on this machine, as an install would record them. */
    static List<String> toolDirs() throws IOException, InterruptedException {
        List<String> dirs = new java.util.ArrayList<>();
        for (String name : List.of("git", "sh", "mvn")) {
            // no quote inside an argument: Windows' command line would not carry it
            String found = answer("sh", "-c", "command -v $0", name);
            dirs.add(Path.of(answer("cygpath", "-w", found)).getParent().toString());
        }
        return dirs;
    }

    private static String answer(String... command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), () -> List.of(command) + ": " + out);
        return out;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            return e.toString();
        }
    }

    /**
     * R11, job-check: run through launch it enters its job, then runs git, sh's mvn and java as the task would find them,
     * through tool.path - every answer a line of its file; one missing, 6, and the file names it.
     */
    @Test
    void jobCheckAnswersForItsJobAndItsPrograms() throws IOException, InterruptedException {
        List<String> dirs = toolDirs();
        Path config = dir.resolve("config");
        Path said = dir.resolve("job-check.txt");
        Files.writeString(config, "repo=" + ScratchRepo.slash(dir) + "\ntool.path=" + String.join(";", dirs).replace("\\", "\\\\")
                + "\n");
        assertEquals(0, launch(new String[]{"job-check", said.toString()}, Map.of("LANDING_CONFIG", config.toString()),
                Optional.empty(), System.out, dir), () -> dirs + " " + read(said));
        List<String> lines = Files.readAllLines(said);
        assertEquals(4, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("entered its job, pid "), lines.toString());
        assertTrue(lines.get(1).startsWith("git: git version"), lines.toString());
        assertTrue(lines.get(2).startsWith("sh: ") && lines.get(2).contains("Maven"), lines.toString());
        assertTrue(lines.get(3).startsWith("java: "), lines.toString());
        Files.writeString(config, "repo=" + ScratchRepo.slash(dir) + "\ntool.path=" + dirs.getFirst().replace("\\", "\\\\") + "\n");
        assertEquals(6, launch(new String[]{"job-check", said.toString()}, Map.of("LANDING_CONFIG", config.toString()),
                Optional.empty(), System.out, dir));
        assertTrue(Files.readString(said).contains("sh"), Files.readString(said));
    }

    /**
     * R11, the log: with a home, integrate writes what it prints to landing-task.log there too; an integrate that takes
     * the lock moves a log over 1 MB to landing-task.log.1; one that does not get the lock never rotates; no home, no log.
     */
    @Test
    void theTasksLogIsBesideTheJarAndRotatedByTheLockHolder() throws IOException {
        ScratchRepo s = new ScratchRepo(dir.resolve("world"), 1);
        s.configFile("sh -c true");
        Optional<Path> home = Optional.of(s.root.resolve("landing.jar"));
        Path log = s.root.resolve("landing-task.log");
        Path earlier = s.root.resolve("landing-task.log.1");
        var quiet = new java.io.PrintStream(new java.io.ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        // a real start's environment: its PATH inherited
        Map<String, String> env = Map.of("PATH", System.getenv("PATH"));
        launch(new String[]{"integrate"}, env, Optional.empty(), quiet, s.a);
        assertFalse(Files.exists(log), "no home, no log");
        Files.writeString(log, "x".repeat(1024 * 1024) + "\n");
        try (IntegratorLock.Held held = new IntegratorLock(s.state.resolve("integrator.lock")).tryAcquire().orElseThrow()) {
            assertEquals(4, launch(new String[]{"integrate"}, env, home, quiet, s.a));
        }
        assertFalse(Files.exists(earlier), "a run without the lock never rotates");
        assertEquals(2, Files.readAllLines(log).size(), "its one line appended");
        launch(new String[]{"integrate"}, env, home, quiet, s.a);
        assertTrue(Files.size(earlier) > 1024 * 1024, "the full log moved aside");
        assertTrue(Files.readString(log).contains("the queue is empty"), Files.readString(log));
    }

    @Test
    void anAuditFaultIsJournaledWithNoEntry() {
        Journal journal = new Journal(dir.resolve("j.jsonl"), new JournalCodec());
        journal.append(new JournalRecord.Fault(RUN, T0, FaultKind.AUDIT, null, "[Fault] I1: x"));
        assertEquals(java.util.Optional.empty(), journal.read().getFirst().entry());
    }
}
