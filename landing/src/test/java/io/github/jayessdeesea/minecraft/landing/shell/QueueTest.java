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
