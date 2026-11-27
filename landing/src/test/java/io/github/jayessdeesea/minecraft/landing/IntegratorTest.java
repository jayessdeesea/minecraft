package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.FaultKind.AUDIT;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.CANDIDATE;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.FETCH_FAILED;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.MERGE_TREE;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.OUTSIDE_PUSH;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.REPO;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.VERIFY_TAMPERED;
import static io.github.jayessdeesea.minecraft.landing.FaultKind.VERIFY_TIMEOUT;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.at;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.attempt;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.bounced;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.failed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.fault;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.halted;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.journal;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.landed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.landing;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.refused;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.resumed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.verified;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The integrator's next step (design sections 3 and 5, amendment 1's R2): halt, then settle what is settled or capped,
 * then wait out an integrator fault's back-off, then reconcile an unfinished attempt, then attempt the oldest request,
 * else idle. An entry is charged only for its own: five tries (Attempts less those an integrator's fault ended), a
 * verify timed out twice, main moved under its push three times - each FAILED. The integrator's own faults are bounded
 * by a halt: three running, fetch failures aside, write Halted; a fetch failure only backs off. {@code Resumed}
 * restarts both counts. "refused" is a try - Attempt, Verified - whose push was refused with main unmoved. Unless a row
 * says otherwise it is 12:00, nothing withdrawn, and main holds no landing.
 */
class IntegratorTest {

    private static final Landings NONE = landed();

    static Stream<Arguments> table() {
        return Stream.of(
                // journal, where entries landed, queued, withdrawn, minutes past noon -> step
                Arguments.of(journal(), NONE, ids(), ids(), 0, "Idle"),
                Arguments.of(journal(), NONE, ids(1), ids(), 0, "Attempt 1"),
                // the oldest request first, whatever the order its refs were read
                Arguments.of(journal(), NONE, ids(3, 1, 2), ids(), 0, "Attempt 1"),
                // an unfinished attempt (a crash, a timeout, main moved) goes again before a new one
                Arguments.of(journal(attempt(2)), NONE, ids(1, 2), ids(), 0, "Reconcile 2 after 1"),
                Arguments.of(journal(attempts(1, 4)), NONE, ids(1), ids(), 0, "Reconcile 1 after 4"),
                // reconciled: an attempt on main is landed, its ref deleted and nothing written
                Arguments.of(journal(attempt(1), verified(1, 3)), landed(landing(3, 1)), ids(1, 2), ids(), 0,
                        "Settle 1 Landed 3"),
                Arguments.of(journal(), landed(landing(3, 1)), ids(1), ids(), 0, "Settle 1 Landed 3"),
                // a settling record written and its ref not yet deleted (a crash at step 10)
                Arguments.of(journal(bounced(1, BounceReason.CONFLICT)), NONE, ids(1), ids(), 0,
                        "Settle 1 Bounced CONFLICT"),
                Arguments.of(journal(attempt(1), failed(1)), NONE, ids(1), ids(), 0, "Settle 1 Failed BUILD_FAILED"),
                // the entry's caps: five tries, two timeouts, main moved three times
                Arguments.of(journal(attempts(1, 5)), NONE, ids(1), ids(), 0, "Settle 1 Failed ATTEMPTS, written"),
                Arguments.of(journal(attempts(1, 5)), landed(landing(3, 1)), ids(1), ids(), 0, "Settle 1 Landed 3"),
                Arguments.of(journal(attempts(1, 4), refused(1, 0)), NONE, ids(1), ids(), 10, "Reconcile 1 after 5"),
                Arguments.of(journal(attempt(1), fault(VERIFY_TIMEOUT, 1, 0)), NONE, ids(1), ids(), 0,
                        "Reconcile 1 after 1"),
                Arguments.of(journal(attempt(1), fault(VERIFY_TIMEOUT, 1, 0), attempt(1), fault(VERIFY_TIMEOUT, 1, 1)),
                        NONE, ids(1), ids(), 2, "Settle 1 Failed TIMED_OUT, written"),
                Arguments.of(journal(attempt(1), fault(OUTSIDE_PUSH, 1, 0), attempt(1), fault(OUTSIDE_PUSH, 1, 1)),
                        NONE, ids(1), ids(), 2, "Reconcile 1 after 2"),
                Arguments.of(journal(attempt(1), fault(OUTSIDE_PUSH, 1, 0), attempt(1), fault(OUTSIDE_PUSH, 1, 1),
                        attempt(1), fault(OUTSIDE_PUSH, 1, 2)), NONE, ids(1), ids(), 3, "Settle 1 Failed MAIN_MOVED, written"),
                // the timeouts are the entry's: another entry's do not count against it
                Arguments.of(journal(attempt(2), fault(VERIFY_TIMEOUT, 2, 0), attempt(2), fault(VERIFY_TIMEOUT, 2, 1),
                        attempt(1)), NONE, ids(1), ids(), 2, "Reconcile 1 after 1"),
                // the integrator's faults are never the entry's: three running halt, whatever kind but a fetch's
                Arguments.of(journal(fault(MERGE_TREE, 1, 0), fault(MERGE_TREE, 1, 10)), NONE, ids(1), ids(), 30,
                        "Attempt 1"),
                Arguments.of(journal(merges(1, 3)), NONE, ids(1), ids(), 30, "Halt, written"),
                Arguments.of(journal(merges(1, 5)), NONE, ids(1), ids(), 60, "Halt, written"),
                Arguments.of(journal(attempt(1), fault(VERIFY_TAMPERED, 1, 0), fault(MERGE_TREE, 1, 10),
                        fault(CANDIDATE, 2, 20)), NONE, ids(1, 2), ids(), 30, "Halt, written"),
                // a bounce, a settle, an entry's fault do not break the run
                Arguments.of(journal(fault(MERGE_TREE, 1, 0), fault(MERGE_TREE, 1, 10), bounced(2, BounceReason.CONFLICT),
                        fault(MERGE_TREE, 1, 20)), NONE, ids(1), ids(), 30, "Halt, written"),
                Arguments.of(journal(refused(1, 0), fault(FETCH_FAILED, 10), refused(1, 20), refused(1, 30)), NONE, ids(1),
                        ids(), 60, "Halt, written"),
                Arguments.of(journal(refused(1, 0), refused(1, 10), attempt(1), fault(OUTSIDE_PUSH, 1, 15), refused(1, 20)),
                        NONE, ids(1), ids(), 60, "Halt, written"),
                // a landing does
                Arguments.of(journal(refused(1, 0), refused(1, 10), attempt(1), verified(1, 3), refused(2, 20)),
                        landed(landing(3, 1)), ids(2), ids(), 60, "Reconcile 2 after 1"),
                // a fetch failure never halts: it heals itself, backing off at 20 minutes for as long as it fails
                Arguments.of(journal(fetches(6)), NONE, ids(1), ids(), 69, "Idle until 13:10"),
                Arguments.of(journal(fetches(6)), NONE, ids(1), ids(), 70, "Attempt 1"),
                // halted: nothing more until the operator resumes; the halt comes before any settling
                Arguments.of(journal(halted()), landed(landing(3, 1)), ids(1, 2), ids(), 0, "Halt"),
                Arguments.of(journal(refused(1, 0), refused(1, 10), refused(1, 20), halted()), NONE, ids(1), ids(), 90,
                        "Halt"),
                // Resumed means go: both counts restart at it, so the next step is an attempt, not a wait
                Arguments.of(journal(halted(), resumed()), NONE, ids(1), ids(), 0, "Attempt 1"),
                Arguments.of(journal(halted(), resumed(), halted()), NONE, ids(1), ids(), 0, "Halt"),
                Arguments.of(journal(refused(1, 0), refused(1, 10), refused(1, 20), halted(), resumed()), NONE, ids(1),
                        ids(), 21, "Reconcile 1 after 3"),
                Arguments.of(journal(refused(1, 0), refused(1, 10), refused(1, 20), halted(), resumed(), refused(1, 30),
                        refused(1, 40)), NONE, ids(1), ids(), 100, "Reconcile 1 after 5"),
                Arguments.of(journal(refused(1, 0), refused(1, 10), refused(1, 20), halted(), resumed(), refused(1, 30),
                        refused(1, 40), refused(1, 50)), NONE, ids(1), ids(), 100, "Halt, written"),
                // withdrawn: the request ref (refs/queue-withdraw/<id>) the integrator settles
                Arguments.of(journal(), NONE, ids(1), ids(1), 0, "Settle 1 Withdrawn, written"),
                Arguments.of(journal(attempt(1)), NONE, ids(1), ids(1), 0, "Settle 1 Withdrawn, written"),
                Arguments.of(journal(), NONE, ids(), ids(1), 0, "Settle 1 Withdrawn, written"),
                // landed or settled first: the withdrawal comes too late, and only its ref goes
                Arguments.of(journal(), landed(landing(3, 1)), ids(1), ids(1), 0, "Settle 1 Landed 3"),
                Arguments.of(journal(failed(1)), NONE, ids(), ids(1), 0, "Settle 1 Failed BUILD_FAILED"),
                // back-off after integrator faults: 5, 10, then 20 minutes from the last
                Arguments.of(journal(fault(FETCH_FAILED, 0)), NONE, ids(1), ids(), 4, "Idle until 12:05"),
                Arguments.of(journal(fault(FETCH_FAILED, 0)), NONE, ids(1), ids(), 5, "Attempt 1"),
                Arguments.of(journal(fault(FETCH_FAILED, 0), fault(MERGE_TREE, 1, 5)), NONE, ids(1), ids(), 14,
                        "Idle until 12:15"),
                Arguments.of(journal(fault(FETCH_FAILED, 0), fault(MERGE_TREE, 1, 5), fault(FETCH_FAILED, 15)), NONE,
                        ids(1), ids(), 34, "Idle until 12:35"),
                Arguments.of(journal(attempt(1), fault(VERIFY_TAMPERED, 1, 0)), NONE, ids(1), ids(), 3, "Idle until 12:05"),
                // settling goes on through a back-off: it needs nothing the fault may have broken
                Arguments.of(journal(fault(FETCH_FAILED, 0)), landed(landing(3, 1)), ids(1), ids(), 1, "Settle 1 Landed 3"),
                // an outcome since the fault ends the back-off
                Arguments.of(journal(fault(FETCH_FAILED, 0), bounced(2, BounceReason.CONFLICT)), NONE, ids(1, 2), ids(),
                        1, "Settle 2 Bounced CONFLICT"),
                Arguments.of(journal(fault(FETCH_FAILED, 0), bounced(2, BounceReason.CONFLICT)), NONE, ids(1), ids(), 1,
                        "Attempt 1"),
                // an entry's own fault is no integrator fault: no back-off
                Arguments.of(journal(attempt(1), fault(VERIFY_TIMEOUT, 1, 0)), NONE, ids(1), ids(), 1,
                        "Reconcile 1 after 1"),
                // an audit's fault: the queue carries on (operator's decision 5)
                Arguments.of(journal(fault(AUDIT, 0)), NONE, ids(1), ids(), 1, "Attempt 1"),
                // an empty queue while backing off is still idle with nothing to wait for
                Arguments.of(journal(fault(FETCH_FAILED, 0)), NONE, ids(), ids(), 1, "Idle until 12:05"));
    }

    /**
     * Whether a run should fetch at all (C2 review F9): not while the journal ends in failed fetches still inside their
     * back-off - an offline machine's every scheduled start would otherwise journal another.
     */
    static Stream<Arguments> fetchesDue() {
        return Stream.of(
                // journal, minutes past noon -> fetch due
                Arguments.of(journal(), 0, true),
                Arguments.of(journal(fault(FETCH_FAILED, 0)), 4, false),
                Arguments.of(journal(fault(FETCH_FAILED, 0)), 5, true),
                Arguments.of(journal(fault(FETCH_FAILED, 0), fault(FETCH_FAILED, 5)), 14, false),
                Arguments.of(journal(fault(FETCH_FAILED, 0), fault(FETCH_FAILED, 5)), 15, true),
                Arguments.of(journal(fetches(4)), 49, false),
                Arguments.of(journal(fetches(4)), 50, true),
                // the journal ends in something else: the last run fetched, or never failed to
                Arguments.of(journal(fault(FETCH_FAILED, 0), Fixtures.seen(1)), 1, true),
                Arguments.of(journal(fault(MERGE_TREE, 1, 0)), 1, true),
                // a fetch failed after a push, about its entry: the same back-off
                Arguments.of(journal(attempt(1), fault(FETCH_FAILED, 1, 0)), 4, false),
                Arguments.of(journal(halted(), resumed(), fault(FETCH_FAILED, 0)), 3, false),
                // a pass's own git failure backs off the whole run as a fetch's does (amendment 2, R8)
                Arguments.of(journal(fault(REPO, 0)), 4, false),
                Arguments.of(journal(fault(REPO, 0)), 5, true),
                Arguments.of(journal(fault(REPO, 0), fault(FETCH_FAILED, 5)), 14, false),
                Arguments.of(journal(fault(REPO, 1, 0)), 4, false));
    }

    /**
     * The halt a pass's own failure may bring (amendment 2, R8), read from the journal alone: three integrator faults
     * running, a fetch's aside, since a Verified or a Resumed - each Verified taken as a landing, so it can only halt
     * later than the core's own next step would, never sooner.
     */
    static Stream<Arguments> haltsDue() {
        return Stream.of(
                Arguments.of(journal(fault(REPO, 0), fault(REPO, 5)), false),
                Arguments.of(journal(fault(REPO, 0), fault(REPO, 5), fault(REPO, 15)), true),
                Arguments.of(journal(fault(REPO, 0), fault(FETCH_FAILED, 1), fault(REPO, 5), fault(REPO, 15)), true),
                Arguments.of(journal(fault(REPO, 0), fault(REPO, 5), resumed(), fault(REPO, 15)), false),
                Arguments.of(journal(fault(REPO, 0), attempt(1), verified(1, 3), fault(REPO, 5), fault(REPO, 15)), false),
                Arguments.of(journal(halted(), fault(REPO, 0), fault(REPO, 5), fault(REPO, 15)), false));
    }

    @ParameterizedTest
    @MethodSource("haltsDue")
    void whetherAPasssOwnFailureHalts(java.util.List<JournalRecord> journal, boolean due) {
        assertEquals(due, Integrator.haltDue(journal).isPresent());
    }

    @ParameterizedTest
    @MethodSource("fetchesDue")
    void whetherAFetchIsDue(java.util.List<JournalRecord> journal, int minutes, boolean due) {
        assertEquals(due, Integrator.runDue(journal, at(minutes)));
    }

    @ParameterizedTest
    @MethodSource("table")
    void theNextStep(java.util.List<JournalRecord> journal, Landings landed, Set<EntryId> queued, Set<EntryId> withdrawn,
                     int minutes, String expected) {
        assertEquals(expected, line(Integrator.next(new Snapshot(journal, landed, queued, withdrawn, at(minutes)))));
    }

    private static Set<EntryId> ids(int... ns) {
        return IntStream.of(ns).mapToObj(Fixtures::id).collect(Collectors.toSet());
    }

    private static JournalRecord[] attempts(int id, int n) {
        return IntStream.range(0, n).mapToObj(i -> attempt(id)).toArray(JournalRecord[]::new);
    }

    /** {@code n} merge-tree faults about entry {@code id}, at 0, 10, 20... minutes past noon, with no Attempt. */
    private static JournalRecord[] merges(int id, int n) {
        return IntStream.range(0, n).mapToObj(i -> fault(MERGE_TREE, id, 10 * i)).toArray(JournalRecord[]::new);
    }

    /** {@code n} failed fetches, at 0, 10, 20... minutes past noon. */
    private static JournalRecord[] fetches(int n) {
        return IntStream.range(0, n).mapToObj(i -> fault(FETCH_FAILED, 10 * i)).toArray(JournalRecord[]::new);
    }

    private static String line(Step step) {
        return switch (step) {
            case Step.Halt h -> "Halt" + (h.record() ? ", written" : "");
            case Step.Settle s -> "Settle " + number(s.id()) + " " + EntriesTest.line(s.outcome())
                    + (s.record() ? ", written" : "");
            case Step.Reconcile r -> "Reconcile " + number(r.id()) + " after " + r.attempts();
            case Step.Attempt a -> "Attempt " + number(a.id());
            case Step.Idle i -> "Idle" + i.until().map(t -> " until " + clock(t)).orElse("");
        };
    }

    private static int number(EntryId id) {
        return Integer.parseInt(id.value().substring(id.value().length() - 8), 16);
    }

    private static String clock(Instant t) {
        long minutes = Duration.between(Fixtures.T0, t).toMinutes();
        return String.format("%02d:%02d", 12 + minutes / 60, minutes % 60);
    }
}
