package io.github.jayessdeesea.minecraft.landing.shell;

import io.github.jayessdeesea.minecraft.landing.FaultKind;
import io.github.jayessdeesea.minecraft.landing.JournalRecord;
import io.github.jayessdeesea.minecraft.landing.Sha;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seven fault classes of the first implementation, as rows (design section 6), each end to end. The push refused
 * with main unmoved is QueueTest's case 10.
 */
@Tag("slow")
class FaultClassTest {

    @TempDir
    Path dir;

    /** The push's reply lost: the push went through, the integrator was told it failed - main says landed. */
    @Test
    void aPushReplyLostIsLanded() {
        ScratchRepo s = new ScratchRepo(dir, 21);
        s.steps = new Steps() {
            @Override
            public boolean pushReported(boolean ok) {
                return false;
            }
        };
        s.write(s.a.resolve("x"), "1\nL\n3\n");
        assertEquals(0, s.land(s.a, "A edits x", s.inline()), s.printed());
        assertEquals("A edits x", s.subjects().getFirst());
        assertEquals(0, s.journal().stream().filter(r -> r instanceof JournalRecord.Fault).count());
        assertEquals(0, s.audit(), s.printed());
    }

    /** Main moved under the push three times: the entry's own, attempted again on each new main, FAILED at the third. */
    @Test
    void mainMovedThreeTimesFails() {
        ScratchRepo s = new ScratchRepo(dir, 22);
        List<Sha> outside = new ArrayList<>();
        s.steps = new Steps() {
            @Override
            public void reached(int step) {
                if (step == 7) {
                    outside.add(s.outsidePush("o" + outside.size()));
                }
            }
        };
        s.write(s.a.resolve("x"), "1\nM\n3\n");
        assertEquals(1, s.land(s.a, "A edits x", s.inline()), s.printed());
        assertEquals(3, s.faults(FaultKind.OUTSIDE_PUSH));
        assertTrue(s.printed().contains("MAIN_MOVED"), s.printed());
        assertEquals(Optional.of("1\n2\n3\n"), s.onMain("x"));
        for (Sha sha : outside) {
            assertEquals(0, s.run(s.a, Poke.NONE, "adopt", sha.value(), "a test's outside push"), s.printed());
        }
        assertEquals(0, s.audit(), s.printed());
    }

    /** A verify past its time: its tree killed, a fault against the entry; FAILED at the second. */
    @Test
    void aVerifyTimedOutTwiceFails() {
        ScratchRepo s = new ScratchRepo(dir, 23);
        Path child = s.root.resolve("sleep.pid");
        s.verify = ScratchRepo.NATIVE_CHILD + " cat /proc/$!/winpid > '" + ScratchRepo.slash(child) + "'; wait; "
                + "echo '[INFO] BUILD SUCCESS' > \"$VERIFY_LOG\"";
        s.verifyTimeout = Duration.ofSeconds(2);
        s.write(s.a.resolve("x"), "1\nT\n3\n");
        long before = System.nanoTime();
        assertEquals(1, s.land(s.a, "A edits x", s.inline()), s.printed());
        assertTrue(Duration.ofNanos(System.nanoTime() - before).toSeconds() < 40, "each verify killed at its time");
        assertEquals(2, s.faults(FaultKind.VERIFY_TIMEOUT));
        long sleep = Long.parseLong(readString(child).strip());
        assertFalse(ProcessHandle.of(sleep).map(ProcessHandle::isAlive).orElse(false), "the verify's child sleep killed");
        assertTrue(s.printed().contains("TIMED_OUT"), s.printed());
        assertEquals(List.of("init"), s.subjects());
        assertEquals(0, s.audit(), s.printed());
    }

    /** HEAD moved during the verify: a fault, nothing pushed; with the verify put right, after the back-off, it lands. */
    @Test
    void headMovedDuringTheVerifyPushesNothing() {
        ScratchRepo s = new ScratchRepo(dir, 24);
        s.verify = "git commit -q --allow-empty -m moved; echo '[INFO] BUILD SUCCESS' > \"$VERIFY_LOG\"";
        s.write(s.a.resolve("x"), "1\nH\n3\n");
        assertEquals(5, s.queue(s.a, "A edits x"));
        String id = s.lastQueued();
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(1, s.faults(FaultKind.VERIFY_TAMPERED));
        assertEquals(List.of("init"), s.subjects());
        s.verify = ScratchRepo.VERIFY;
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(List.of("init"), s.subjects(), "backing off: nothing attempted");
        s.clock.advance(Duration.ofMinutes(5));
        assertEquals(0, s.integrate(), s.printed());
        assertEquals(0, s.run(s.a, Poke.NONE, "state", id), s.printed());
        assertEquals(0, s.audit(), s.printed());
    }

    private static String readString(Path file) {
        try {
            return java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** A branch carrying a change that landed and was then reverted bounces "sync": landing it would bring it back. */
    @Test
    void aBranchCarryingALandedThenRevertedChangeBounces() {
        ScratchRepo s = new ScratchRepo(dir, 25);
        s.write(s.a.resolve("r"), "r\n");
        s.git(s.a).ok("add", "r");
        assertEquals(0, s.land(s.a, "A adds r", s.inline()));
        String source = s.git(s.origin).ok("log", "-1", "--format=%(trailers:key=Landing-Source,valueonly)", "main").strip();
        s.git(s.a).ok("revert", "--no-edit", "HEAD");
        assertEquals(0, s.land(s.a, "A reverts r", s.inline()), s.printed());
        assertEquals(Optional.empty(), s.onMain("r"));
        s.git(s.b).ok("reset", "-q", "--hard", source);
        s.write(s.b.resolve("q"), "q\n");
        s.git(s.b).ok("add", "q");
        assertEquals(3, s.land(s.b, "B adds q", s.inline()), s.printed());
        assertTrue(s.printed().contains("CARRIES_LANDED"), s.printed());
        assertEquals(Optional.empty(), s.onMain("r"));
        assertEquals(0, s.audit(), s.printed());
    }
}
