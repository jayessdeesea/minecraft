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
        Config defaults = Config.of(none);
        assertEquals(Path.of("C:/mc-state/landing/journal.jsonl"), defaults.journal());
        assertEquals(Path.of("C:/mc-state/landing/integrator.lock"), defaults.lock());
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
        Config set = Config.of(some);
        assertEquals(Path.of("D:/j.jsonl"), set.journal());
        assertEquals(List.of("sh", "-c", "true"), set.verify());
        assertEquals(Duration.ofMinutes(2), set.verifyTimeout());
        assertEquals(Optional.empty(), set.jarLandingChange());
        assertThrows(IllegalArgumentException.class, () -> Config.of(new Properties()), "the repo is required");
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

    @Test
    void anAuditFaultIsJournaledWithNoEntry() {
        Journal journal = new Journal(dir.resolve("j.jsonl"), new JournalCodec());
        journal.append(new JournalRecord.Fault(RUN, T0, FaultKind.AUDIT, null, "[Fault] I1: x"));
        assertEquals(java.util.Optional.empty(), journal.read().getFirst().entry());
    }
}
