package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scratch queue-test.sh's cases, ported to the queue as designed (design section 6): a scratch bare origin, a
 * clone and its second worktree, a fake verify. Two answers changed: the same change from two branches bounces
 * "nothing to land", and the stale lock is two JVMs contending (ShellPartsTest). Case 10 follows amendment 1's R2: three
 * refusals running halt the queue. A failed entry's commit stays where it is (operator's decision 6).
 */
@Tag("slow")
class QueueTest {

    @TempDir
    Path dir;

    /** 1: a change lands, and its writer's HEAD is main. */
    @Test
    void aChangeLands() {
        ScratchRepo s = new ScratchRepo(dir, 1);
        s.write(s.a.resolve("x"), "1\nA\n3\n");
        assertEquals(0, s.land(s.a, "A edits x", s.inline()), s.printed());
        assertEquals("A edits x", s.subjects().getFirst());
        assertEquals(s.mainTip(), s.git(s.a).resolve("HEAD").orElseThrow());
        assertEquals(List.of(), s.requestRefs());
        assertEquals(0, s.audit(), s.printed());
    }

    /**
     * 2: two submitters waiting at once, in two threads, each poking the integrator - one integrator takes the lock and
     * the other poke finds it held - both land, main linear and the refs cleared.
     */
    @Test
    void twoWaitingBothLandLinear() throws Exception {
        ScratchRepo s = new ScratchRepo(dir, 2);
        s.write(s.a.resolve("z"), "z\n");
        s.git(s.a).ok("add", "z");
        s.write(s.b.resolve("y"), "yb\n");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> s.land(s.a, "A adds z", s.inline()));
            var b = pool.submit(() -> s.land(s.b, "B edits y", s.inline()));
            assertEquals(0, a.get(3, java.util.concurrent.TimeUnit.MINUTES), s.printed());
            assertEquals(0, b.get(3, java.util.concurrent.TimeUnit.MINUTES), s.printed());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(2, s.queuedIds().size());
        assertEquals(3, s.subjects().size());
        assertEquals("", s.git(s.origin).ok("rev-list", "--merges", "main"));
        assertEquals(List.of(), s.requestRefs());
        assertEquals(0, s.audit(), s.printed());
    }

    /** 3 and 4: an edit of the line just landed, from a stale base, bounces naming the file; rebased, it lands. */
    @Test
    void aConflictBouncesThenTheRebasedChangeLands() {
        ScratchRepo s = new ScratchRepo(dir, 3);
        s.write(s.a.resolve("x"), "1\nA\n3\n");
        assertEquals(0, s.land(s.a, "A edits x", s.inline()));
        s.write(s.b.resolve("x"), "1\nB\n3\n");
        assertEquals(3, s.land(s.b, "B edits x", s.inline()), s.printed());
        assertTrue(s.printed().contains("CONFLICT") && s.printed().contains("[x]"), s.printed());
        assertEquals(2, s.subjects().size());
        Git b = s.git(s.b);
        assertFalse(b.run(List.of("rebase", "-q", "origin/main")).ok(), "the rebase meets the conflict");
        s.write(s.b.resolve("x"), "1\nB\n3\n");
        b.ok("add", "x");
        b.with(Map.of("GIT_EDITOR", "true")).ok("rebase", "--continue");
        assertEquals(0, s.land(s.b, "B edits x again", s.inline()), s.printed());
        assertEquals(Optional.of("1\nB\n3\n"), s.onMain("x"));
        assertEquals("B edits x again", s.subjects().getFirst());
        assertEquals(3, s.subjects().size(), "one commit for B's branch");
        assertEquals(0, s.audit(), s.printed());
    }

    /** 5: a failing verify fails the entry; main is unchanged and the commit stays (operator's decision 6). */
    @Test
    void aFailingVerifyFailsAndTheCommitStays() {
        ScratchRepo s = new ScratchRepo(dir, 5);
        s.write(s.a.resolve("fail"), "\n");
        s.git(s.a).ok("add", "fail");
        assertEquals(1, s.land(s.a, "A breaks it", s.inline()), s.printed());
        assertEquals(List.of("init"), s.subjects());
        assertEquals("A breaks it", s.git(s.a).ok("log", "-1", "--format=%s").strip());
        assertEquals(0, s.audit(), s.printed());
    }

    /** 7: nothing changed since main: nothing to land, exit 2. */
    @Test
    void nothingToLand() {
        ScratchRepo s = new ScratchRepo(dir, 7);
        assertEquals(2, s.land(s.a, "nothing", s.inline()), s.printed());
        assertEquals(List.of(), s.requestRefs());
    }

    /** 8: a branch left unmoved after its change landed replays only what is new onto the landed commit. */
    @Test
    void aBranchLeftUnmovedReplaysOnlyWhatIsNew() {
        ScratchRepo s = new ScratchRepo(dir, 8);
        s.write(s.a.resolve("w"), "w1\n");
        s.git(s.a).ok("add", "w");
        assertEquals(0, s.land(s.a, "A adds w", s.inline()));
        String source = s.git(s.origin).ok("log", "-1", "--format=%(trailers:key=Landing-Source,valueonly)", "main").strip();
        s.git(s.a).ok("reset", "-q", "--hard", source);
        s.git(s.b).ok("reset", "-q", "--hard", "origin/main");
        s.git(s.b).ok("rm", "-q", "w");
        assertEquals(0, s.land(s.b, "B removes w", s.inline()), s.printed());
        s.write(s.a.resolve("v"), "v\n");
        s.git(s.a).ok("add", "v");
        assertEquals(0, s.land(s.a, "A adds v", s.inline()), s.printed());
        assertTrue(s.printed().contains("replayed"), s.printed());
        assertEquals(Optional.empty(), s.onMain("w"));
        assertEquals(Optional.of("v\n"), s.onMain("v"));
        assertEquals(0, s.audit(), s.printed());
    }

    /** 9 (answer changed): the same change from two branches - the second's tree is main's, so nothing to land. */
    @Test
    void theSameChangeFromTwoBranchesIsNothingToLand() {
        ScratchRepo s = new ScratchRepo(dir, 9);
        s.write(s.a.resolve("s"), "same\n");
        s.git(s.a).ok("add", "s");
        assertEquals(0, s.land(s.a, "A adds s", s.inline()));
        s.write(s.b.resolve("s"), "same\n");
        s.git(s.b).ok("add", "s");
        assertEquals(2, s.land(s.b, "B adds s", s.inline()), s.printed());
        assertTrue(s.printed().contains("NOTHING_TO_LAND"), s.printed());
        assertEquals(2, s.subjects().size());
        assertEquals(0, s.audit(), s.printed());
    }

    /**
     * 10 (amendment 1, R2): a push refused with main unmoved is the integrator's fault - backed off, never charged to
     * the entry - and the third running halts the queue: every waiter exits 4. Resumed, it lands.
     */
    @Test
    void threeRefusalsWithMainUnmovedHaltThenResumeLands() {
        ScratchRepo s = new ScratchRepo(dir, 10);
        s.poll = Duration.ofMinutes(1);
        s.write(s.origin.resolve("hooks/pre-receive"), "#!/bin/sh\nexit 1\n");
        s.write(s.a.resolve("u"), "u\n");
        s.git(s.a).ok("add", "u");
        assertEquals(4, s.land(s.a, "A adds u", s.inline()), s.printed());
        assertEquals(3, s.faults(FaultKind.PUSH_REFUSED));
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Halted));
        assertEquals(Optional.empty(), s.onMain("u"));
        String id = s.lastQueued();
        assertEquals(4, s.run(s.a, Poke.NONE, "state", id));
        assertTrue(new java.io.File(s.origin.resolve("hooks/pre-receive").toString()).delete());
        assertEquals(0, s.run(s.a, Poke.NONE, "resume", "the hook taken out"), s.printed());
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertEquals(Optional.of("u\n"), s.onMain("u"));
        assertEquals(0, s.audit(), s.printed());
    }

    /** 11: a second integrator, in this JVM or another, finds the lock held and exits 4. */
    @Test
    void aSecondIntegratorExitsFour() {
        ScratchRepo s = new ScratchRepo(dir, 11);
        try (IntegratorLock.Held held = new IntegratorLock(s.state.resolve("integrator.lock")).tryAcquire().orElseThrow()) {
            assertEquals(4, s.integrate(), s.printed());
        }
        assertEquals(0, s.integrate(), s.printed());
    }

    /** 12 (answer changed): an untracked file in the landing worktree is cleaned away; the change lands. */
    @Test
    void anUntrackedFileInTheLandingWorktreeIsCleanedAway() {
        ScratchRepo s = new ScratchRepo(dir, 12);
        assertEquals(0, s.integrate(), s.printed());
        s.write(s.landingTree.resolve("t"), "junk\n");
        s.write(s.a.resolve("t"), "t\n");
        s.git(s.a).ok("add", "t");
        assertEquals(0, s.land(s.a, "A adds t", s.inline()), s.printed());
        assertEquals(Optional.of("t\n"), s.onMain("t"));
    }

    /**
     * 13: a wait cut short by --timeout exits 5 with the entry still queued, re-poking each minute while it waits; the
     * entry lands when the integrator next runs, and its state says so.
     */
    @Test
    void aWaitCutShortLeavesTheEntryQueued() {
        ScratchRepo s = new ScratchRepo(dir, 13);
        AtomicInteger pokes = new AtomicInteger();
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(5, s.land(s.a, "A adds k", pokes::incrementAndGet, "--timeout", "3"), s.printed());
        assertEquals(3, pokes.get(), "poked at once, then at one and two minutes; at three the wait is over");
        assertEquals(1, s.requestRefs().size());
        String id = s.lastQueued();
        assertEquals(5, s.run(s.a, Poke.NONE, "state", id));
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertEquals(Optional.of("k\n"), s.onMain("k"));
    }

    /** A withdrawn entry is settled by the integrator, its refs gone; its waiter exits 3. */
    @Test
    void aWithdrawnEntryIsSettled() {
        ScratchRepo s = new ScratchRepo(dir, 14);
        s.write(s.a.resolve("x"), "1\nW\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        assertEquals(0, s.run(s.a, Poke.NONE, "withdraw", id), s.printed());
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(3, s.run(s.a, Poke.NONE, "state", id));
        assertEquals(List.of(), s.requestRefs());
        assertEquals(List.of("init"), s.subjects());
        assertEquals(0, s.audit(), s.printed());
    }

    /**
     * A fake verify that says it builds app and has done one test class, then writes nothing until the test opens
     * {@code gate}, then passes with a Reactor Summary - or fails, the candidate holding {@code fail}.
     */
    private static String gated(Path gate) {
        return "if [ -e fail ]; then echo '[INFO] BUILD FAILURE' > \"$VERIFY_LOG\"; exit 1; fi; "
                + "echo '[INFO] Building app 0.1.0-SNAPSHOT [7/7]' > \"$VERIFY_LOG\"; "
                + "echo '[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.1 s -- in x.ATest' >> \"$VERIFY_LOG\"; "
                + "while [ ! -e '" + ScratchRepo.slash(gate) + "' ]; do sleep 0.1; done; "
                + "echo '[INFO] Reactor Summary for app 0.1.0-SNAPSHOT:' >> \"$VERIFY_LOG\"; "
                + "echo '[INFO] app ................................................ SUCCESS [  1.000 s]' >> \"$VERIFY_LOG\"; "
                + "echo '[INFO] BUILD SUCCESS' >> \"$VERIFY_LOG\"";
    }

    /**
     * Lands {@code file} from a, the integrator on a thread of its own and the waiter really waiting, so the waiter sees
     * the verify running; once it has said a verifying line, {@code during} runs and the gate opens. Its exit.
     */
    private static int landWatched(ScratchRepo s, String file, Path gate, Runnable during) throws Exception {
        s.write(s.a.resolve(file), file + "\n");
        s.git(s.a).ok("add", file);
        Path message = s.root.resolve("message-" + file);
        s.write(message, "A adds " + file + "\n");
        int seen = s.printed().split(" verifying, attempt 1, ", -1).length;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        boolean answered = false;
        try {
            Poke background = () -> pool.submit(s::integrate);
            var waiter = pool.submit(() -> s.cli(s.a, background, d -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).run("land", message.toString()));
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(2);
            while (s.printed().split(" verifying, attempt 1, ", -1).length == seen && System.nanoTime() < until) {
                Thread.sleep(50);
            }
            during.run();
            java.nio.file.Files.writeString(gate, "go");
            int exit = waiter.get(3, java.util.concurrent.TimeUnit.MINUTES);
            answered = true;
            return exit;
        } finally {
            // a waiter that answered - one that gave up included - leaves its integrator to finish before the test's
            // directory goes; one that threw or timed out, or a {@code during} that threw, is interrupted at once
            if (answered) {
                pool.shutdown();
                if (!pool.awaitTermination(3, java.util.concurrent.TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } else {
                pool.shutdownNow();
            }
        }
    }

    /**
     * A waiter on a slow verify says where it is - verifying, the module, the classes done, how old the last output is -
     * before it lands; with no verify passed yet there is no estimate; `state` says the same line under its own; the
     * progress file is there only while the verify runs; a pass is kept in the history.
     */
    @Test
    void aWaiterSaysWhereItsVerifyIs() throws Exception {
        ScratchRepo s = new ScratchRepo(dir, 16);
        Path gate = s.root.resolve("gate");
        s.verify = gated(gate);
        Path progress = s.state.resolve("verify-progress.json");
        Path history = s.state.resolve("verify-history.json");
        boolean[] during = new boolean[2];
        String[] state = new String[1];
        int exit = landWatched(s, "q", gate, () -> {
            during[0] = java.nio.file.Files.exists(progress);
            var said = new java.io.ByteArrayOutputStream();
            String id = s.lastQueued();
            new Landing(s.config(), s.tool(), s.clock, s.random, new java.io.PrintStream(said, true,
                    java.nio.charset.StandardCharsets.UTF_8), s.a, s.clock::advance, s.steps, Poke.NONE).run("state", id);
            state[0] = said.toString(java.nio.charset.StandardCharsets.UTF_8);
        });
        assertEquals(0, exit, s.printed());
        String printed = s.printed();
        String id = s.lastQueued();
        int verifying = printed.indexOf(id + " verifying, attempt 1, ");
        assertTrue(verifying >= 0, printed);
        String line = printed.substring(verifying, printed.indexOf('\n', verifying));
        assertTrue(line.contains("app [7/7], 1 test class done, 0 failing (no estimate: no verify has passed yet); last output ")
                && line.contains("s ago"), line);
        assertTrue(verifying < printed.indexOf(id + " landed as "), printed);
        assertTrue(during[0], "the progress file there while the verify runs");
        assertFalse(java.nio.file.Files.exists(progress), "gone after");
        assertTrue(state[0].startsWith(id + ": Verifying"), state[0]);
        assertTrue(state[0].lines().toList().get(1).startsWith(id + " verifying, attempt 1, "), state[0]);
        assertTrue(java.nio.file.Files.readString(history).contains("\"app\""), "the pass kept");
        assertTrue(printed.contains("verify took ") && printed.contains("(no estimate: no verify has passed yet), longest quiet"),
                printed);
    }

    /** A second landing is measured against the first: its waiter says a percent and the time left. */
    @Test
    void aSecondLandingShowsAPercent() throws Exception {
        ScratchRepo s = new ScratchRepo(dir, 17);
        Path gate = s.root.resolve("gate");
        s.verify = gated(gate);
        assertEquals(0, landWatched(s, "q", gate, () -> { }), s.printed());
        java.nio.file.Files.delete(gate);
        assertEquals(0, landWatched(s, "r", gate, () -> { }), s.printed());
        String printed = s.printed();
        String id = s.lastQueued();
        int verifying = printed.indexOf(id + " verifying, attempt 1, ");
        assertTrue(verifying >= 0, printed);
        String line = printed.substring(verifying, printed.indexOf('\n', verifying)).strip();
        assertTrue(line.matches(".* verifying, attempt 1, \\S+: \\d+%, about \\S+ left.* - app \\[7/7], 1 of 1 test classes; .*"),
                line);
        assertTrue(printed.contains("against ") && printed.contains(" expected (1 pass)"), printed);
    }

    /**
     * A history whose 2 x expected is past what the run budget leaves, and a clock that moves while the run starts: the
     * cap is held to what is left at the check, said, and the attempt starts - never refused for ever (review round 1).
     * Here the budget is 3h and a remote call 2m, so 2 x 88m = 176m is held below 170m50s.
     */
    @Test
    void aHeldCapStillAttempts() {
        ScratchRepo s = new ScratchRepo(dir, 20);
        var codec = new io.github.jayessdeesea.minecraft.landing.WatchCodec();
        s.write(s.state.resolve("verify-history.json"), codec.write(io.github.jayessdeesea.minecraft.landing.VerifyHistory.NONE
                .with(new io.github.jayessdeesea.minecraft.landing.VerifyPass(s.clock.instant(), s.init, Duration.ofMinutes(88),
                        List.of()))));
        s.clock.tick();
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(0, s.land(s.a, "A adds k", s.inline(), "--timeout", "5"), s.printed());
        assertTrue(s.printed().contains("the verify cap 2 x 88m = 176m is held to "), s.printed());
        assertEquals(Optional.of("k\n"), s.onMain("k"));
    }

    /**
     * A test that prints a Reactor Summary line no verify could take - 10^17 minutes - is display, not outcome (review
     * round 2, A): the entry lands and is pushed, the history keeps the pass without that module, and the verify's time
     * is said.
     */
    @Test
    void aTestPrintingASummaryLineNoVerifyCouldTakeStillLands() {
        ScratchRepo s = new ScratchRepo(dir, 21);
        s.verify = "echo '[INFO] app ................................................ SUCCESS [100000000000000000:00 min]' "
                + "> \"$VERIFY_LOG\"; echo '[INFO] Reactor Summary for bot 0.1.0-SNAPSHOT:' >> \"$VERIFY_LOG\"; "
                + "echo '[INFO] bot ................................................ SUCCESS [  1.000 s]' >> \"$VERIFY_LOG\"; "
                + "echo '[INFO] BUILD SUCCESS' >> \"$VERIFY_LOG\"";
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(0, s.land(s.a, "A adds k", s.inline(), "--timeout", "3"), s.printed());
        assertEquals(Optional.of("k\n"), s.onMain("k"));
        String history = readHistory(s);
        assertTrue(history.contains("\"bot\"") && !history.contains("\"app\""), history);
        assertTrue(s.printed().contains("verify took "), s.printed());
    }

    private static String readHistory(ScratchRepo s) {
        try {
            return java.nio.file.Files.readString(s.state.resolve("verify-history.json"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /**
     * A log that goes and comes back, at the same size, is no output (review round 2, B): moved aside and back between
     * looks with nothing new, the verify ends as a stall at the stall limit - not, each move taken for growth, at its cap.
     */
    @Test
    void aLogThatComesAndGoesIsAStall() {
        ScratchRepo s = new ScratchRepo(dir, 22);
        String pause = "\"${SystemRoot:-$SYSTEMROOT}/System32/PING.EXE\" -n 1 -w 100 192.0.2.1 > /dev/null";
        s.verify = "echo '[INFO] Building app 0.1.0-SNAPSHOT [7/7]' > \"$VERIFY_LOG\"; while true; do "
                + "mv \"$VERIFY_LOG\" \"$VERIFY_LOG.aside\"; " + pause + "; mv \"$VERIFY_LOG.aside\" \"$VERIFY_LOG\"; " + pause
                + "; done";
        s.verifyLimits = new io.github.jayessdeesea.minecraft.landing.VerifyWatch.Limits(Duration.ofSeconds(2),
                Duration.ofSeconds(20), Duration.ofMillis(100));
        s.clock.tick();
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(1, s.land(s.a, "A adds k", s.inline()), s.printed());
        assertEquals(2, s.faults(FaultKind.VERIFY_STALLED), s.printed());
        assertEquals(0, s.faults(FaultKind.VERIFY_TIMEOUT), s.printed());
    }

    /**
     * git, except that while {@code reads} holds a count its look at main's shared ref answers absent - exit 1, as
     * {@code rev-parse --verify --quiet} does when the integrator's fetch has the ref mid-rewrite - one fewer each time.
     */
    private static void mainReadsAbsent(ScratchRepo s, Path reads) {
        Path fake = s.root.resolve("git-absent.sh");
        String f = ScratchRepo.slash(reads);
        s.write(fake, "if [ \"$1 $2 $3 $4\" = 'rev-parse --verify --quiet refs/remotes/origin/main' ] && [ -e '" + f + "' ]; then\n"
                + "  n=$(cat '" + f + "'); if [ \"$n\" -gt 1 ]; then echo $((n - 1)) > '" + f + "'; else rm -f '" + f + "'; fi\n"
                + "  exit 1\nfi\nexec git \"$@\"\n");
        s.gitProgram = List.of("sh", fake.toString());
    }

    /** {@code n} absent reads to come, written whole before the shim can see the file: a move, never a write in place. */
    private static void absentFor(ScratchRepo s, Path reads, int n) {
        Path part = reads.resolveSibling(reads.getFileName() + ".part");
        s.write(part, n + "\n");
        try {
            java.nio.file.Files.move(part, reads, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** While the verify runs, {@code n} absent reads of main, waited on until the waiter has made them all. */
    private static Runnable absentReads(ScratchRepo s, Path reads, int n) {
        return () -> {
            absentFor(s, reads, n);
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            while (java.nio.file.Files.exists(reads) && System.nanoTime() < until) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        };
    }

    /** A read of main that finds the ref absent, the integrator mid-rewrite, is read again: said with its count, it lands. */
    @Test
    void anAbsentReadOfMainIsReadAgainAndTheEntryLands() throws Exception {
        ScratchRepo s = new ScratchRepo(dir, 23);
        Path gate = s.root.resolve("gate");
        Path reads = s.root.resolve("absent-reads");
        s.verify = gated(gate);
        mainReadsAbsent(s, reads);
        assertEquals(0, landWatched(s, "q", gate, absentReads(s, reads, 1)), s.printed());
        assertTrue(s.printed().contains(s.lastQueued() + " main's ref read absent (1 of 5); reading again"), s.printed());
        assertEquals(Optional.of("q\n"), s.onMain("q"));
    }

    /** Five absent reads in a row is the ref gone: the waiter gives up, 6, naming the count; its entry still lands. */
    @Test
    void fiveAbsentReadsOfMainInARowIsSix() throws Exception {
        ScratchRepo s = new ScratchRepo(dir, 24);
        Path gate = s.root.resolve("gate");
        Path reads = s.root.resolve("absent-reads");
        s.verify = gated(gate);
        mainReadsAbsent(s, reads);
        assertEquals(6, landWatched(s, "q", gate, absentReads(s, reads, 5)), s.printed());
        String id = s.lastQueued();
        assertTrue(s.printed().contains(id + " main's ref read absent (4 of 5); reading again"), s.printed());
        assertTrue(s.printed().contains("landing itself failed: ") && s.printed().contains(id + " main's ref read absent 5 of 5 in a row"),
                s.printed());
        assertEquals(Optional.of("q\n"), s.onMain("q"), "the waiter gave up; the integrator did not");
    }

    /** {@code landing state} reads main as a waiter does: one absent read is read again and the state answered. */
    @Test
    void stateReadsMainAgainAfterAnAbsentRead() {
        ScratchRepo s = new ScratchRepo(dir, 25);
        Path reads = s.root.resolve("absent-reads");
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(5, s.queue(s.a, "A adds k"));
        String id = s.lastQueued();
        mainReadsAbsent(s, reads);
        absentFor(s, reads, 1);
        assertEquals(5, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertTrue(s.printed().contains(id + " main's ref read absent (1 of 5); reading again"), s.printed());
        absentFor(s, reads, 5);
        assertEquals(6, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertTrue(s.printed().contains(id + " main's ref read absent 5 of 5 in a row"), s.printed());
    }

    /** An audit that finds the lock held reads main as a waiter does: one absent read is read again, five is 6. */
    @Test
    void anUnlockedAuditReadsMainAgainAfterAnAbsentRead() {
        ScratchRepo s = new ScratchRepo(dir, 26);
        Path reads = s.root.resolve("absent-reads");
        mainReadsAbsent(s, reads);
        try (IntegratorLock.Held held = new IntegratorLock(s.state.resolve("integrator.lock")).tryAcquire().orElseThrow()) {
            absentFor(s, reads, 1);
            assertEquals(0, s.audit(), s.printed());
            assertTrue(s.printed().contains("main's ref read absent (1 of 5); reading again")
                    && s.printed().contains("I7 not checked"), s.printed());
            absentFor(s, reads, 5);
            assertEquals(6, s.audit(), s.printed());
            assertTrue(s.printed().contains("main's ref read absent 5 of 5 in a row"), s.printed());
        }
    }

    /**
     * The integrator's own reads under the lock are read again too - another worktree's fetch rewrites the ref as well:
     * one absent read and the entry lands; five, and the run ends as any failed git call does, a REPO fault and 6.
     */
    @Test
    void theIntegratorReadsMainAgainAfterAnAbsentRead() {
        ScratchRepo s = new ScratchRepo(dir, 27);
        Path reads = s.root.resolve("absent-reads");
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(5, s.queue(s.a, "A adds k"));
        mainReadsAbsent(s, reads);
        absentFor(s, reads, 5);
        assertEquals(6, s.integrate(), s.printed());
        assertTrue(s.journal().stream().anyMatch(r -> r instanceof JournalRecord.Fault f && f.kind() == FaultKind.REPO
                && f.detail().contains("main's ref read absent 5 of 5 in a row")), s.printed());
        s.clock.advance(Duration.ofMinutes(5));
        absentFor(s, reads, 1);
        assertEquals(0, s.integrate(), s.printed());
        assertTrue(s.printed().contains("main's ref read absent (1 of 5); reading again"), s.printed());
        assertEquals(Optional.of("k\n"), s.onMain("k"));
    }

    /** A failed verify is no pass: nothing kept in the history. */
    @Test
    void aFailedVerifyIsNotKept() {
        ScratchRepo s = new ScratchRepo(dir, 18);
        s.verify = gated(s.root.resolve("gate"));
        s.write(s.a.resolve("fail"), "\n");
        s.git(s.a).ok("add", "fail");
        assertEquals(1, s.land(s.a, "A breaks it", s.inline()), s.printed());
        assertFalse(java.nio.file.Files.exists(s.state.resolve("verify-history.json")));
    }

    /** A history that cannot be read is said once and the verify goes on with no estimate: the entry lands. */
    @Test
    void anUnreadableHistoryIsSaidOnceAndTheEntryLands() throws Exception {
        ScratchRepo s = new ScratchRepo(dir, 19);
        Path gate = s.root.resolve("gate");
        java.nio.file.Files.writeString(gate, "go");
        s.verify = gated(gate);
        s.write(s.state.resolve("verify-history.json"), "{not json");
        s.write(s.a.resolve("k"), "k\n");
        s.git(s.a).ok("add", "k");
        assertEquals(0, s.land(s.a, "A adds k", s.inline()), s.printed());
        assertEquals(1, s.printed().split("the verify history ", -1).length - 1, s.printed());
        assertTrue(java.nio.file.Files.readString(s.state.resolve("verify-history.json")).contains("\"app\""),
                "written again with this pass");
    }

    /** The hook's question: may this push go? Only the verified candidate, on its parent, while the lock is held. */
    @Test
    void mayPushAllowsOnlyTheVerifiedCandidateUnderTheLock() {
        ScratchRepo s = new ScratchRepo(dir, 15);
        s.write(s.a.resolve("x"), "1\nP\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        int[] answers = new int[3];
        s.steps = new Steps() {
            @Override
            public void reached(int step) {
                if (step == 7) {
                    JournalRecord.Verified v = (JournalRecord.Verified) s.journal().getLast();
                    answers[0] = s.run(s.a, Poke.NONE, "may-push", v.candidate().value(), v.parent().value());
                    answers[1] = s.run(s.a, Poke.NONE, "may-push", v.parent().value(), v.parent().value());
                }
            }
        };
        assertEquals(0, s.integrate(), s.printed());
        Sha tip = s.mainTip();
        answers[2] = s.run(s.a, Poke.NONE, "may-push", tip.value(), s.init.value());
        assertEquals(List.of(0, 1, 1), List.of(answers[0], answers[1], answers[2]), s.printed());
    }
}
