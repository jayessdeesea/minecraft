package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.attempt;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.bounced;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.failed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.fault;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.journal;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.landing;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.landed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.plain;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.verified;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.withdrawn;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An entry's state as a fold of the journal and main (design section 1): a trailer on main wins over everything,
 * then the first settling record, then the attempts; never two states at once. Entry 1 is the one asked about.
 */
class EntriesTest {

    static Stream<Arguments> table() {
        return Stream.of(
                // journal, where entries landed on main -> state of entry 1
                Arguments.of(journal(), landed(), "Queued"),
                Arguments.of(journal(attempt(1)), landed(), "Verifying 1"),
                Arguments.of(journal(attempt(1), attempt(1)), landed(), "Verifying 2"),
                Arguments.of(journal(attempt(1), verified(1, 101)), landed(), "Verifying 1"),
                // a fault about the entry is not an outcome
                Arguments.of(journal(attempt(1), fault(FaultKind.VERIFY_TIMEOUT, 1, 0)), landed(), "Verifying 1"),
                Arguments.of(journal(attempt(1), failed(1)), landed(), "Failed BUILD_FAILED"),
                Arguments.of(journal(bounced(1, BounceReason.CONFLICT)), landed(), "Bounced CONFLICT"),
                Arguments.of(journal(bounced(1, BounceReason.NOTHING_TO_LAND)), landed(), "Bounced NOTHING_TO_LAND"),
                Arguments.of(journal(withdrawn(1)), landed(), "Withdrawn"),
                // landed is a fact of main: nothing in the journal says so
                Arguments.of(journal(attempt(1), verified(1, 101)), landed(plain(2), landing(3, 1)), "Landed 3"),
                Arguments.of(journal(), landed(landing(3, 1)), "Landed 3"),
                // main wins over a settling record: a corrupt journal is the audit's to name (I4)
                Arguments.of(journal(attempt(1), failed(1)), landed(landing(3, 1)), "Landed 3"),
                // an id on two commits (I3) reads as its first, the older
                Arguments.of(journal(), landed(landing(3, 1), landing(4, 1)), "Landed 3"),
                // the first settling record wins; an attempt after it does not reopen the entry
                Arguments.of(journal(failed(1), bounced(1, BounceReason.CONFLICT)), landed(), "Failed BUILD_FAILED"),
                Arguments.of(journal(withdrawn(1), attempt(1)), landed(), "Withdrawn"),
                // another entry's records and landing are not this one's
                Arguments.of(journal(attempt(2), failed(2)), landed(landing(3, 2)), "Queued"),
                Arguments.of(journal(attempt(1), attempt(2), failed(2)), landed(), "Verifying 1"));
    }

    @ParameterizedTest
    @MethodSource("table")
    void theStateOfEntryOne(List<JournalRecord> journal, Landings landed, String expected) {
        assertEquals(expected, line(Entries.state(Fixtures.id(1), journal, landed)));
    }

    /**
     * The tries the five-attempt cap counts (amendment 1, R2): the entry's Attempts, less each Attempt an integrator's
     * fault about the entry ended before its next Attempt. A crash (an Attempt with nothing after it) and the entry's
     * own faults still count; a fault before any Attempt is the integrator's and is no try.
     */
    static Stream<Arguments> tries() {
        return Stream.of(
                // journal, about entry 1 unless said -> tries
                Arguments.of(journal(), 0),
                Arguments.of(journal(attempt(1), attempt(1), attempt(1)), 3),
                Arguments.of(journal(attempt(1), fault(FaultKind.PUSH_REFUSED, 1, 0)), 0),
                Arguments.of(journal(attempt(1), fault(FaultKind.FETCH_FAILED, 1, 0)), 0),
                Arguments.of(journal(attempt(1), fault(FaultKind.FETCH_FAILED, 0)), 1),
                Arguments.of(journal(attempt(1), fault(FaultKind.VERIFY_TIMEOUT, 1, 0)), 1),
                Arguments.of(journal(attempt(1), fault(FaultKind.OUTSIDE_PUSH, 1, 0)), 1),
                Arguments.of(journal(attempt(1), fault(FaultKind.PUSH_REFUSED, 2, 0)), 1),
                Arguments.of(journal(attempt(1), attempt(1), fault(FaultKind.PUSH_REFUSED, 1, 0)), 1),
                Arguments.of(journal(attempt(1), fault(FaultKind.VERIFY_NO_RESULT, 1, 0), fault(FaultKind.FETCH_FAILED, 1, 1)),
                        0),
                Arguments.of(journal(fault(FaultKind.MERGE_TREE, 1, 0), fault(FaultKind.MERGE_TREE, 1, 1),
                        fault(FaultKind.MERGE_TREE, 1, 2), fault(FaultKind.MERGE_TREE, 1, 3), fault(FaultKind.MERGE_TREE, 1, 4)),
                        0),
                // a Verified does not end the try: its push is the rest of it
                Arguments.of(journal(attempt(1), verified(1, 101), fault(FaultKind.PUSH_REFUSED, 1, 5)), 0),
                // an entry's own fault ends its try charged; an integrator's fault after it takes nothing back
                Arguments.of(journal(attempt(1), fault(FaultKind.VERIFY_TIMEOUT, 1, 0), fault(FaultKind.MERGE_TREE, 1, 5)), 1),
                // a crashed Attempt, then the next try's fault at commit-tree, before its own Attempt: the integrator's
                Arguments.of(journal(attempt(1), fault(FaultKind.CANDIDATE, 1, 5)), 0));
    }

    /**
     * Whether the entry's last Attempt was verified and so maybe pushed, unsettled (amendment 2, R9): a waiter then
     * looks at main for itself, since the integrator's fetch may be what failed.
     */
    static Stream<Arguments> pushed() {
        return Stream.of(
                Arguments.of(journal(), false),
                Arguments.of(journal(attempt(1)), false),
                Arguments.of(journal(attempt(1), verified(1, 101)), true),
                Arguments.of(journal(attempt(1), verified(1, 101), fault(FaultKind.FETCH_FAILED, 1, 0)), true),
                Arguments.of(journal(attempt(1), verified(1, 101), attempt(1)), false),
                Arguments.of(journal(attempt(1), verified(2, 101)), false),
                Arguments.of(journal(attempt(1), verified(1, 101), failed(1)), false));
    }

    @ParameterizedTest
    @MethodSource("pushed")
    void whetherEntryOneMayBePushed(List<JournalRecord> journal, boolean expected) {
        assertEquals(expected, Entries.pushed(Fixtures.id(1), journal));
    }

    @ParameterizedTest
    @MethodSource("tries")
    void theTriesAtEntryOne(List<JournalRecord> journal, int expected) {
        assertEquals(expected, Entries.tries(Fixtures.id(1), journal));
    }

    static String line(EntryState state) {
        return switch (state) {
            case EntryState.Queued q -> "Queued";
            case EntryState.Verifying v -> "Verifying " + v.attempts();
            case EntryState.Landed l -> "Landed " + Integer.parseInt(l.sha().value(), 16);
            case EntryState.Failed f -> "Failed " + f.reason();
            case EntryState.Bounced b -> "Bounced " + b.reason();
            case EntryState.Withdrawn w -> "Withdrawn";
        };
    }
}
