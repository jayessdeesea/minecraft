package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shell's rows from C2's first review, end to end: each the case a finding named, and its answer. */
@Tag("slow")
class ReviewRowsTest {

    @TempDir
    Path dir;

    /** F1: a message with no final newline, or a {@code ---} line in its body, still carries its trailers and lands. */
    @ParameterizedTest
    @ValueSource(strings = {"A edits x", "A edits x\n\nbody\n---\nmore\n", "A edits x\n\nbody\n--- \nmore\n"})
    void anyMessageCarriesItsTrailers(String message) {
        ScratchRepo s = new ScratchRepo(dir, 31);
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(0, s.landExact(s.a, message, s.inline()), s.printed());
        assertEquals("A edits x", s.subjects().getFirst());
    }

    /**
     * F2: one landing in flight a stream - a second land while the last is unsettled waits on it again, squashing
     * nothing; the new commits wait for the land after, which replays them onto the landed commit.
     */
    @Test
    void aSecondLandWaitsAgainAndTheThirdReplays() {
        ScratchRepo s = new ScratchRepo(dir, 32);
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        s.write(s.a.resolve("z"), "z\n");
        s.git(s.a).ok("add", "z");
        s.git(s.a).ok("commit", "-q", "-m", "new work");
        // the entry lands, and this worktree holds a commit more: land again (amendment 2, R7)
        assertEquals(5, s.land(s.a, "again", s.inline()), s.printed());
        assertTrue(s.printed().contains("waiting again"), s.printed());
        assertEquals(List.of("A edits x", "init"), s.subjects());
        assertEquals(Optional.empty(), s.onMain("z"));
        assertEquals(0, s.land(s.a, "A adds z", s.inline()), s.printed());
        assertTrue(s.printed().contains("replayed"), s.printed());
        assertEquals(Optional.of("z\n"), s.onMain("z"));
        assertEquals(0, s.audit(), s.printed());
    }

    /** F3: refs under refs/queue that name no entry are skipped by every run, named by the audit, never touched. */
    @Test
    void aRefThatIsNoEntryIsSkippedAndNamed() {
        ScratchRepo s = new ScratchRepo(dir, 33);
        s.git(s.a).ok("update-ref", "refs/queue/foo", s.init.value());
        s.git(s.a).ok("update-ref", "refs/queue/20261126T120000Z-0000beef/x", s.init.value());
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(0, s.land(s.a, "A edits x", s.inline()), s.printed());
        assertEquals(1, s.audit());
        assertTrue(s.printed().contains("refs/queue/foo is not an entry")
                && s.printed().contains("refs/queue/20261126T120000Z-0000beef/x is not an entry"), s.printed());
        assertEquals(2, s.requestRefs().size());
    }

    /** F4: a Landing-Source that is no commit - here a tree - is MALFORMED. */
    @Test
    void aSourceThatIsNoCommitIsMalformed() {
        ScratchRepo s = new ScratchRepo(dir, 34);
        String id = "20261126T120000Z-0000f004";
        Sha tree = s.git(s.a).tree(s.init);
        s.rawRequest(id, List.of(s.init), "Landing-Id: " + id + "\nLanding-Source: " + tree);
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(3, s.run(s.a, Poke.NONE, "state", id));
        assertTrue(s.printed().contains("MALFORMED"), s.printed());
    }

    /** F7: bad arguments are usage. */
    @Test
    void aBadIdIsUsage() {
        ScratchRepo s = new ScratchRepo(dir, 35);
        assertEquals(2, s.run(s.a, Poke.NONE, "state", "garbage"));
        assertEquals(2, s.run(s.a, Poke.NONE, "land", "m", "--timeout", "soon"));
    }

    /** F7: landing itself failing - a journal it cannot read - exits 6, never 1 (FAILED). */
    @Test
    void landingItselfFailingExitsSix() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 36);
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        Files.writeString(s.state.resolve("journal.jsonl"), "garbage\n", StandardOpenOption.APPEND);
        assertEquals(6, s.land(s.a, "again", Poke.NONE, "--timeout", "0"), s.printed());
    }

    /** F8: the last submission landed, and HEAD no longer holds its source: sent back before any squash. */
    @Test
    void aLandedSourceGoneFromHeadIsSentBack() {
        ScratchRepo s = new ScratchRepo(dir, 37);
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(0, s.land(s.a, "A edits x", s.inline()));
        s.git(s.a).ok("reset", "-q", "--hard", s.init.value());
        s.write(s.a.resolve("v"), "v\n");
        s.git(s.a).ok("add", "v");
        assertEquals(3, s.land(s.a, "A adds v", s.inline()), s.printed());
        assertTrue(s.printed().contains("is not in HEAD's history"), s.printed());
        assertEquals(List.of(), s.requestRefs());
    }

    /** F9: offline, starts inside the fetch's back-off neither fetch nor write: one record, not one a start. */
    @Test
    void offlineStartsInsideTheBackOffWriteNothing() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 38);
        Path gone = s.root.resolve("origin.gone");
        Files.move(s.origin, gone);
        assertEquals(0, s.integrate(), s.printed());
        s.clock.advance(Duration.ofMinutes(2));
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(1, s.faults(FaultKind.FETCH_FAILED));
        s.clock.advance(Duration.ofMinutes(4));
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(2, s.faults(FaultKind.FETCH_FAILED), "past the back-off it fetches again");
        Files.move(gone, s.origin);
    }

    /** F11: a fetch that fails after the push is FETCH_FAILED about the entry; the next run finds it landed. */
    @Test
    void aFetchFailedAfterThePushCarriesItsEntry() throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 39);
        Path gone = s.root.resolve("origin.gone");
        s.steps = new Steps() {
            @Override
            public void reached(int step) {
                if (step == 8) {
                    move(s.origin, gone);
                }
            }
        };
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        assertEquals(0, s.integrate(), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.FETCH_FAILED
                && f.entry().map(e -> e.value().equals(id)).orElse(false)), s.printed());
        Files.move(gone, s.origin);
        s.steps = new Steps() {
        };
        s.clock.advance(Duration.ofMinutes(5));
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertEquals(0, s.audit(), s.printed());
    }

    /** F11: a verify that writes no BUILD line, or dirties the tracked tree, is the integrator's fault about the entry. */
    @ParameterizedTest
    @ValueSource(strings = {"true", "echo dirt >> x; echo '[INFO] BUILD SUCCESS' > \"$VERIFY_LOG\""})
    void aVerifyWithNoResultOrADirtyTreeIsAFaultAboutTheEntry(String verify) {
        ScratchRepo s = new ScratchRepo(dir, 40);
        s.verify = verify;
        s.write(s.a.resolve("x"), "1\nF\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        assertEquals(0, s.integrate(), s.printed());
        FaultKind kind = verify.equals("true") ? FaultKind.VERIFY_NO_RESULT : FaultKind.VERIFY_TAMPERED;
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == kind
                && f.entry().map(e -> e.value().equals(id)).orElse(false)), s.printed());
        assertEquals(List.of("init"), s.subjects());
    }

    /** F11: bounces through the shell - a request with two parents is MALFORMED; one carrying another's id, LANDING_ID. */
    @Test
    void handMadeRequestsBounce() {
        ScratchRepo s = new ScratchRepo(dir, 41);
        Sha head = s.init;
        String one = "20261126T120000Z-0000aaa1";
        String two = "20261126T120000Z-0000aaa2";
        Sha other = Sha.parse(s.git(s.a).run(List.of("commit-tree", s.git(s.a).tree(s.init).value(), "-p", s.init.value(),
                "-m", "another parent")).line());
        s.rawRequest(one, List.of(s.init, other), "Landing-Id: " + one + "\nLanding-Source: " + head);
        s.rawRequest(two, List.of(s.init), "Landing-Id: " + one + "\nLanding-Source: " + head);
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(3, s.run(s.a, Poke.NONE, "state", one));
        assertEquals(3, s.run(s.a, Poke.NONE, "state", two));
        assertTrue(s.printed().contains("MALFORMED") && s.printed().contains("LANDING_ID"), s.printed());
    }

    /** F11: a commit landed on main whose Landing-Source is not in the repo is "not in history", unasked; the next lands. */
    @Test
    void aLandedSourceAbsentFromTheRepoIsNotInHistory() {
        ScratchRepo s = new ScratchRepo(dir, 42);
        Sha outside = s.outsidePush("o", "outside o\n\nLanding-Id: 20261126T110000Z-0000dead\nLanding-Source: "
                + "1234567890123456789012345678901234567890");
        assertEquals(0, s.run(s.a, Poke.NONE, "adopt", outside.value(), "an outside commit with a stray source"), s.printed());
        s.write(s.b.resolve("x"), "1\nF\n3\n");
        assertEquals(0, s.land(s.b, "B edits x", s.inline()), s.printed());
        assertEquals(0, s.audit(), s.printed());
    }

    /**
     * F6, by amendment 2's R5: an integrator killed mid-verify leaves no verify - its job ends it with it - and the next
     * run, with no orphan to look for, reconciles the Attempt and lands.
     */
    @Test
    void anOrphanedVerifyIsKilledByTheNextRun() throws IOException, InterruptedException {
        ScratchRepo s = new ScratchRepo(dir, 43);
        Path second = s.root.resolve("second");
        Path sleeper = s.root.resolve("sleep.pid");
        Path script = s.root.resolve("verify.sh");
        s.write(script, "if [ -e '" + ScratchRepo.slash(second) + "' ]; then echo '[INFO] BUILD SUCCESS' > \"$VERIFY_LOG\"; exit 0; fi\n"
                + ScratchRepo.NATIVE_CHILD + "\ncat /proc/$!/winpid > '" + ScratchRepo.slash(sleeper) + ".tmp'\nmv '" + ScratchRepo.slash(sleeper)
                + ".tmp' '" + ScratchRepo.slash(sleeper) + "'\ntouch '" + ScratchRepo.slash(second) + "'\nwait\n");
        Path config = s.configFile("sh " + ScratchRepo.slash(script));
        s.write(s.a.resolve("x"), "1\nO\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        String java = ProcessHandle.current().info().command().orElseThrow();
        ProcessBuilder builder = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), Landing.class.getName(), "integrate").directory(s.a.toFile()).redirectErrorStream(true).redirectOutput(s.root.resolve("child.log").toFile());
        builder.environment().putAll(s.env);
        builder.environment().put("LANDING_CONFIG", config.toString());
        Process integrator = builder.start();
        long orphan = -1;
        try {
            Instant until = Instant.now().plusSeconds(90);
            while (!Files.exists(sleeper) && Instant.now().isBefore(until)) {
                Thread.sleep(200);
            }
            assertTrue(Files.exists(sleeper), "the verify never started: " + Files.readString(s.root.resolve("child.log")));
            orphan = Long.parseLong(Files.readString(sleeper).strip());
            integrator.destroyForcibly();
            integrator.waitFor();
            Instant gone = Instant.now().plusSeconds(5);
            while (ProcessHandle.of(orphan).map(ProcessHandle::isAlive).orElse(false) && Instant.now().isBefore(gone)) {
                Thread.sleep(100);
            }
            assertFalse(ProcessHandle.of(orphan).map(ProcessHandle::isAlive).orElse(false), "the verify died with its job");
            s.verify = ScratchRepo.VERIFY;
            assertEquals(0, s.integrate(), s.printed());
            assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
        } finally {
            integrator.destroyForcibly();
            if (orphan > 0) {
                ProcessHandle.of(orphan).ifPresent(ProcessHandle::destroyForcibly);
            }
        }
    }

    /**
     * F5 and F11: a git call that hangs (merge-tree, killed at its time with its children) or fails (commit-tree) is the
     * step's integrator fault about the entry - MERGE_TREE, CANDIDATE - and the run ends with the lock released.
     */
    @ParameterizedTest
    @ValueSource(strings = {"merge-tree", "commit-tree"})
    void aGitCallThatHangsOrFailsIsTheStepsFault(String call) throws IOException {
        ScratchRepo s = new ScratchRepo(dir, 44);
        s.write(s.a.resolve("x"), "1\nG\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        Path child = s.root.resolve("git-sleep.pid");
        Path fake = s.root.resolve("git.sh");
        s.write(fake, "case \"$1\" in\n  merge-tree) " + ScratchRepo.NATIVE_CHILD + " cat /proc/$!/winpid > '" + ScratchRepo.slash(child)
                + "'; wait;;\n  commit-tree) exit 1;;\nesac\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
        s.gitTimeout = Duration.ofSeconds(5);
        if (call.equals("commit-tree")) {
            s.write(fake, "case \"$1\" in\n  commit-tree) exit 1;;\nesac\nexec git \"$@\"\n");
        }
        assertEquals(0, s.integrate(), s.printed());
        FaultKind kind = call.equals("merge-tree") ? FaultKind.MERGE_TREE : FaultKind.CANDIDATE;
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == kind
                && f.entry().map(e -> e.value().equals(id)).orElse(false)), s.printed());
        new IntegratorLock(s.state.resolve("integrator.lock")).tryAcquire().orElseThrow().close();
        if (call.equals("merge-tree")) {
            long sleep = Long.parseLong(Files.readString(child).strip());
            assertFalse(ProcessHandle.of(sleep).map(ProcessHandle::isAlive).orElse(false), "the hung call's child killed");
        }
        assertEquals(List.of("init"), s.subjects());
    }

    private static void move(Path from, Path to) {
        try {
            Files.move(from, to);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
