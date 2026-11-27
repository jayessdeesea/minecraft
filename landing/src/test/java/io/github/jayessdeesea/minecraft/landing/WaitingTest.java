package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.RUN;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.T0;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.attempt;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.id;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.journal;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.verified;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a waiter says of its entry (design sections 3 and 3b), from the journal, the progress file and the queue's refs:
 * one line, said when its key changes and every minute otherwise. The key is the state, the attempt, the module, the
 * newest fault, the number ahead and the quiet and last-looked warnings - no counts, percents or ages. Entry 1 was
 * submitted at 12:00:01; it is 12:31 unless a row says otherwise; the stall limit is 10m, the cap 90m.
 */
class WaitingTest {

    private static final Instant NOW = T0.plus(Duration.ofMinutes(31));
    private static final String ID = id(1).value();
    private static final String OTHER = id(2).value();

    private static Instant at(int minutes, int seconds) {
        return T0.plus(Duration.ofMinutes(minutes)).plusSeconds(seconds);
    }

    private static MavenProgress app(int done, int failing) {
        return new MavenProgress(Optional.of("app"), 7, 7, done, failing);
    }

    /** A verify of {@code entry} begun at 12:08, the integrator's look at {@code look}, the log grown at {@code grew}. */
    private static VerifyProgress progress(int entry, Instant look, Instant grew, MavenProgress maven,
                                           Optional<VerifyEstimate> estimate) {
        return new VerifyProgress(id(entry), sha(101), at(8, 0), look, grew, 4096, Duration.ofMinutes(10),
                Duration.ofMinutes(90), maven, estimate);
    }

    private static final Optional<VerifyEstimate> ON_PACE = Optional.of(new VerifyEstimate(68, Duration.ofSeconds(11 * 60 + 4),
            Optional.empty(), Duration.ofMinutes(34), 4, 221));
    private static final Optional<VerifyEstimate> LATE = Optional.of(new VerifyEstimate(97, Duration.ofSeconds(50),
            Optional.of(Duration.ofSeconds(6 * 60 + 3)), Duration.ofMinutes(34), 4, 221));

    private static Waiting.Said said(EntryState state, List<JournalRecord> journal, Optional<VerifyProgress> progress,
                                     Optional<Step> next, int ahead, Instant now) {
        // the entries ahead, for a row: 2, 3, ... - which are older is the submitter's to read from the refs
        return Waiting.line(id(1), state, journal, progress, next,
                java.util.stream.IntStream.rangeClosed(2, ahead + 1).mapToObj(Fixtures::id).toList(), now);
    }

    /**
     * Ahead, a verify whose integrator has not looked for over a minute is said so - it may be gone - as the verifying
     * line says it (review round 1); a warning is a change of key.
     */
    @Test
    void queuedBehindAVerifyNotLookedAtForMinutes() {
        Waiting.Said s = said(new EntryState.Queued(), journal(attempt(2)),
                Optional.of(progress(2, at(26, 0), at(25, 58), new MavenProgress(Optional.of("bot"), 5, 7, 61, 0), ON_PACE)),
                Optional.of(new Step.Reconcile(id(2), 1)), 1, NOW);
        assertEquals(ID + " queued 30m59s, 1 ahead: " + OTHER + " verifying 23m, 68%, about 11m left - bot [5/7]; this one "
                + "about 45m; the integrator last looked 5m ago - it may be gone; the task starts one within 5m", s.text());
        Waiting.Said fresh = said(new EntryState.Queued(), journal(attempt(2)),
                Optional.of(progress(2, at(30, 55), at(30, 50), new MavenProgress(Optional.of("bot"), 5, 7, 61, 0), ON_PACE)),
                Optional.of(new Step.Reconcile(id(2), 1)), 1, NOW);
        assertNotEquals(fresh.key(), s.key());
    }

    /** A progress file whose entry is not one of those ahead - one settled, or newer - is not said as the one ahead. */
    @Test
    void aProgressFileOfAnEntryNotAheadIsIgnored() {
        Waiting.Said s = said(new EntryState.Queued(), journal(), Optional.of(progress(9, at(30, 55), at(30, 50), app(3, 0),
                ON_PACE)), Optional.of(new Step.Attempt(id(2))), 1, NOW);
        assertEquals(ID + " queued 30m59s, 1 ahead", s.text());
    }

    @Test
    void verifyingOnPace() {
        Waiting.Said s = said(new EntryState.Verifying(2), journal(attempt(1), attempt(1)),
                Optional.of(progress(1, at(30, 55), at(30, 48), app(147, 0), ON_PACE)), Optional.empty(), 0, NOW);
        assertEquals(ID + " verifying, attempt 2, 23m: 68%, about 11m left - app [7/7], 147 of 221 test classes; last output "
                + "12s ago", s.text());
    }

    @Test
    void verifyingPastItsEstimate() {
        Waiting.Said s = said(new EntryState.Verifying(1), journal(attempt(1)),
                Optional.of(progress(1, at(30, 55), at(30, 48), app(220, 1), LATE)), Optional.empty(), 0, NOW);
        assertEquals(ID + " verifying, attempt 1, 23m: 97%, about 50s left, past the 34m estimate by 6m - app [7/7], 220 of "
                + "221 test classes, 1 failing; last output 12s ago", s.text());
    }

    @Test
    void verifyingWithNoHistory() {
        Waiting.Said s = said(new EntryState.Verifying(1), journal(attempt(1)),
                Optional.of(progress(1, at(30, 55), at(30, 48), app(47, 0), Optional.empty())), Optional.empty(), 0, NOW);
        assertEquals(ID + " verifying, attempt 1, 23m: app [7/7], 47 test classes done, 0 failing (no estimate: no verify has "
                + "passed yet); last output 12s ago", s.text());
    }

    /** Quiet past half the stall limit: the quiet is said, and when it ends the verify. */
    @Test
    void quietPastHalfTheStallLimit() {
        Waiting.Said s = said(new EntryState.Verifying(1), journal(attempt(1)),
                Optional.of(progress(1, at(30, 55), at(25, 50), app(47, 0), Optional.empty())), Optional.empty(), 0, NOW);
        assertTrue(s.text().endsWith("; last output 5m10s ago; quiet 5m10s - ended at 10m"), s.text());
    }

    /** The integrator's last look over a minute old: it may be gone, and the task starts another. */
    @Test
    void theIntegratorLastLookedLongAgo() {
        Waiting.Said s = said(new EntryState.Verifying(1), journal(attempt(1)),
                Optional.of(progress(1, at(28, 0), at(27, 58), app(47, 0), Optional.empty())), Optional.empty(), 0, NOW);
        assertTrue(s.text().endsWith("; the integrator last looked 3m ago - it may be gone; the task starts one within 5m"),
                s.text());
    }

    @Test
    void queuedWithAnotherVerifyingAhead() {
        Waiting.Said s = said(new EntryState.Queued(), journal(attempt(2)),
                Optional.of(progress(2, at(30, 55), at(30, 50), new MavenProgress(Optional.of("bot"), 5, 7, 61, 0), ON_PACE)),
                Optional.of(new Step.Reconcile(id(2), 1)), 1, NOW);
        assertEquals(ID + " queued 30m59s, 1 ahead: " + OTHER + " verifying 23m, 68%, about 11m left - bot [5/7]; this one "
                + "about 45m", s.text());
        Waiting.Said none = said(new EntryState.Queued(), journal(attempt(2)),
                Optional.of(progress(2, at(30, 55), at(30, 50), new MavenProgress(Optional.of("bot"), 5, 7, 61, 0),
                        Optional.empty())), Optional.of(new Step.Reconcile(id(2), 1)), 1, NOW);
        assertEquals(ID + " queued 30m59s, 1 ahead: " + OTHER + " verifying 23m, bot [5/7], 61 test classes done, 0 failing", none.text());
    }

    @Test
    void queuedWhileTheIntegratorBacksOff() {
        List<JournalRecord> j = journal(new JournalRecord.Fault(RUN, at(20, 0), FaultKind.CANDIDATE, id(2), "commit-tree failed"));
        Waiting.Said s = said(new EntryState.Queued(), j, Optional.empty(), Optional.of(new Step.Idle(Optional.of(at(25, 0)))),
                0, NOW.minus(Duration.ofMinutes(10)));
        assertEquals(ID + " queued 20m59s; the integrator backs off until 12:25Z after CANDIDATE: commit-tree failed",
                s.text());
    }

    @Test
    void queuedOnAHaltedQueue() {
        Waiting.Said s = said(new EntryState.Queued(), journal(Fixtures.halted()), Optional.empty(),
                Optional.of(new Step.Halt("three running", false)), 0, NOW);
        assertEquals(ID + " queued 30m59s; the queue is halted: three running", s.text());
    }

    @Test
    void queuedNextToGo() {
        Waiting.Said s = said(new EntryState.Queued(), journal(new JournalRecord.SeenMain(RUN, at(24, 0), sha(1))),
                Optional.empty(), Optional.of(new Step.Attempt(id(1))), 0, NOW);
        assertEquals(ID + " queued 30m59s; next to be attempted; the journal's last line is 7m old", s.text());
        assertEquals(ID + " queued 30m59s; next to be attempted; the journal is empty", said(new EntryState.Queued(), journal(),
                Optional.empty(), Optional.of(new Step.Attempt(id(1))), 0, NOW).text());
    }

    /** An Attempt journaled and no verify of it running yet - the progress file absent, or still another's. */
    @Test
    void attemptedAndNoVerifyYet() {
        List<JournalRecord> j = journal(attempt(2), new JournalRecord.Attempt(RUN, at(13, 0), id(1), sha(101), sha(1)),
                new JournalRecord.Attempt(RUN, at(19, 30), id(1), sha(101), sha(1)));
        String expected = ID + " attempt 2 since 12:19Z; no verify running yet";
        assertEquals(expected, said(new EntryState.Verifying(2), j, Optional.empty(), Optional.empty(), 0, NOW).text());
        assertEquals(expected, said(new EntryState.Verifying(2), j, Optional.of(progress(2, at(30, 55), at(30, 50), app(1, 0),
                Optional.empty())), Optional.empty(), 0, NOW).text());
    }

    @Test
    void aNewFaultAboutTheEntry() {
        List<JournalRecord> j = journal(attempt(1), new JournalRecord.Fault(RUN, at(20, 0), FaultKind.VERIFY_STALLED, id(1),
                "the verify wrote nothing for 10m"));
        assertEquals(ID + " [Fault] VERIFY_STALLED: the verify wrote nothing for 10m; 1 of 2", said(new EntryState.Verifying(1),
                j, Optional.empty(), Optional.empty(), 0, NOW).text());
        List<JournalRecord> two = journal(attempt(1), new JournalRecord.Fault(RUN, at(20, 0), FaultKind.VERIFY_TIMEOUT, id(1),
                "ran 90m"), attempt(1), new JournalRecord.Fault(RUN, at(25, 0), FaultKind.VERIFY_STALLED, id(1), "quiet"));
        assertEquals(ID + " [Fault] VERIFY_STALLED: quiet; 2 of 2", said(new EntryState.Verifying(2), two, Optional.empty(),
                Optional.empty(), 0, NOW).text());
        List<JournalRecord> moved = journal(attempt(1), new JournalRecord.Fault(RUN, at(20, 0), FaultKind.OUTSIDE_PUSH, id(1),
                "main moved"));
        assertEquals(ID + " [Fault] OUTSIDE_PUSH: main moved; 1 of 3", said(new EntryState.Verifying(1), moved, Optional.empty(),
                Optional.empty(), 0, NOW).text());
        List<JournalRecord> theirs = journal(attempt(1), new JournalRecord.Fault(RUN, at(20, 0), FaultKind.VERIFY_TAMPERED,
                id(1), "HEAD moved"));
        assertEquals(ID + " [Fault] VERIFY_TAMPERED: HEAD moved", said(new EntryState.Verifying(1), theirs, Optional.empty(),
                Optional.empty(), 0, NOW).text());
    }

    @Test
    void verifiedNotYetOnMain() {
        assertEquals(ID + " verified; being pushed", said(new EntryState.Verifying(1), journal(attempt(1), verified(1, 101)),
                Optional.empty(), Optional.empty(), 0, NOW).text());
    }

    /** Ages, counts, percents and time left are no change of key; a module, a warning, the number ahead are. */
    @Test
    void theKeyIsWhatChangesNotWhatCounts() {
        List<JournalRecord> j = journal(attempt(1));
        Waiting.Said first = said(new EntryState.Verifying(1), j, Optional.of(progress(1, at(30, 55), at(30, 48), app(147, 0),
                ON_PACE)), Optional.empty(), 0, NOW);
        Waiting.Said later = said(new EntryState.Verifying(1), j, Optional.of(progress(1, at(31, 55), at(31, 50), app(160, 0),
                LATE)), Optional.empty(), 0, NOW.plusSeconds(60));
        assertEquals(first.key(), later.key());
        assertNotEquals(first.text(), later.text());
        Waiting.Said bot = said(new EntryState.Verifying(1), j, Optional.of(progress(1, at(30, 55), at(30, 48),
                new MavenProgress(Optional.of("bot"), 5, 7, 61, 0), ON_PACE)), Optional.empty(), 0, NOW);
        assertNotEquals(first.key(), bot.key());
        Waiting.Said quiet = said(new EntryState.Verifying(1), j, Optional.of(progress(1, at(30, 55), at(25, 50), app(147, 0),
                ON_PACE)), Optional.empty(), 0, NOW);
        assertNotEquals(first.key(), quiet.key());
        Waiting.Said oneAhead = said(new EntryState.Queued(), journal(), Optional.empty(), Optional.of(new Step.Attempt(id(0))), 1,
                NOW);
        Waiting.Said twoAhead = said(new EntryState.Queued(), journal(), Optional.empty(), Optional.of(new Step.Attempt(id(0))), 2,
                NOW);
        assertNotEquals(oneAhead.key(), twoAhead.key());
    }

    /** Said when the key changes, and every minute otherwise. */
    @Test
    void whenALineIsDue() {
        Waiting.Said a = new Waiting.Said("a", "a 1");
        Waiting.Said a2 = new Waiting.Said("a", "a 2");
        Waiting.Said b = new Waiting.Said("b", "b");
        Duration every = Duration.ofSeconds(60);
        assertTrue(Waiting.due(Optional.empty(), T0, a, T0, every), "nothing said yet");
        assertTrue(Waiting.due(Optional.of(a), T0, b, T0.plusSeconds(1), every), "the key changed");
        assertFalse(Waiting.due(Optional.of(a), T0, a2, T0.plusSeconds(59), every), "neither");
        assertTrue(Waiting.due(Optional.of(a), T0, a2, T0.plusSeconds(60), every), "the minute reached");
    }
}
