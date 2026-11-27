package io.github.jayessdeesea.minecraft.landing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.landing.Fixtures.RUN;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.RUN2;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.T0;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.adopted;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.attempt;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.bounced;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.cutover;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.failed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.id;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.landed;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.landing;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.main;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.mainSince;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.merge;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.plain;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.seen;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.sha;
import static io.github.jayessdeesea.minecraft.landing.Fixtures.verified;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audit (design section 4, amendment 1): each invariant a row that keeps it and rows that break it, everything
 * else as in the clean case - cut over at commit 1 (K, the journal's last Cutover), entry 1 attempted, verified as
 * commit 2 and landed there, main seen at 1 then 2, the queue empty, no integrator mid-step, the hook and the jar as
 * installed, 12:00. Unless a row says otherwise, main's history is K and the commits a row gives, so where entries
 * landed is read from those.
 */
class AuditTest {

    private static final List<JournalRecord> CLEAN = List.of(cutover(1), seen(1), attempt(1), verified(1, 2), seen(2));
    private static final Main ONE_LANDED = main(landing(2, 1));
    private static final Set<Audit.Edge> FORWARD = Set.of(edge(1, 2), edge(2, 3));
    private static final Audit.Install INSTALLED = new Audit.Install(Optional.of("h1"), "h1", sha(2), Optional.of(sha(2)));
    private static final boolean MID_STEP = true, BETWEEN_STEPS = false;
    private static final Main NOT_CUT_OVER = mainSince(Optional.empty());

    static Stream<Arguments> table() {
        return Stream.of(
                // journal, main since K, queued, ancestry the shell confirmed, an integrator mid-step -> broken
                Arguments.of(CLEAN, ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS, "[]"),
                // I1: main is linear
                Arguments.of(with(adopted(3)), main(landing(2, 1), merge(3)), ids(), FORWARD, BETWEEN_STEPS, "[I1]"),
                // I2: each commit lands one verified id, or is adopted
                Arguments.of(CLEAN, main(landing(2, 1), plain(3)), ids(), FORWARD, BETWEEN_STEPS, "[I2]"),
                Arguments.of(with(adopted(3)), main(landing(2, 1), plain(3)), ids(), FORWARD, BETWEEN_STEPS, "[]"),
                Arguments.of(CLEAN, main(landing(2, 1), merge(3)), ids(), FORWARD, BETWEEN_STEPS, "[I1, I2]"),
                Arguments.of(CLEAN, main(commit(2, id(1).value(), id(2).value())), ids(), FORWARD, BETWEEN_STEPS, "[I2]"),
                // a malformed id lands nothing, so entry 1 is unsettled with no ref too
                Arguments.of(CLEAN, main(commit(2, "not-an-id")), ids(), FORWARD, BETWEEN_STEPS, "[I2, I7]"),
                // landed with no Verified, or Verified as another commit: not verified on the main it landed on
                Arguments.of(CLEAN, main(landing(2, 1), landing(3, 2)), ids(), FORWARD, BETWEEN_STEPS, "[I2]"),
                Arguments.of(with(verified(2, 4)), main(landing(2, 1), landing(3, 2)), ids(), FORWARD, BETWEEN_STEPS, "[I2]"),
                Arguments.of(with(verified(2, 3)), main(landing(2, 1), landing(3, 2)), ids(), FORWARD, BETWEEN_STEPS, "[]"),
                // I3: no id on two commits
                Arguments.of(with(verified(1, 3)), main(landing(2, 1), landing(3, 1)), ids(), FORWARD, BETWEEN_STEPS,
                        "[I3]"),
                // I4: one settling record at most, none for an id on main, no attempt after one
                Arguments.of(with(attempt(3), failed(3)), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS, "[]"),
                Arguments.of(with(failed(3), bounced(3, BounceReason.CONFLICT)), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS,
                        "[I4]"),
                Arguments.of(with(failed(1)), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS, "[I4]"),
                Arguments.of(with(failed(3), attempt(3)), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS, "[I4]"),
                // I5: two runs' records never interleave
                Arguments.of(with(new JournalRecord.SeenMain(RUN2, T0, sha(2))), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS,
                        "[]"),
                Arguments.of(with(new JournalRecord.SeenMain(RUN2, T0, sha(2)), seen(2)), ONE_LANDED, ids(), FORWARD,
                        BETWEEN_STEPS, "[I5]"),
                // I6: main never rewound - each SeenMain an ancestor of the next, the last of main now
                Arguments.of(CLEAN, ONE_LANDED, ids(), Set.of(), BETWEEN_STEPS, "[I6]"),
                Arguments.of(with(seen(2)), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS, "[]"),
                Arguments.of(with(adopted(3)), main(landing(2, 1), plain(3)), ids(), Set.of(edge(1, 2)), BETWEEN_STEPS,
                        "[I6]"),
                Arguments.of(with(adopted(3)), main(landing(2, 1), plain(3)), ids(), FORWARD, BETWEEN_STEPS, "[]"),
                // I7: the request refs are the unsettled entries; a ref left for a settled one is the next run's to clear
                Arguments.of(with(attempt(3)), ONE_LANDED, ids(3), FORWARD, BETWEEN_STEPS, "[]"),
                Arguments.of(CLEAN, ONE_LANDED, ids(1), FORWARD, BETWEEN_STEPS, "[]"),
                Arguments.of(with(attempt(3), failed(3)), ONE_LANDED, ids(3), FORWARD, BETWEEN_STEPS, "[]"),
                Arguments.of(with(attempt(3)), ONE_LANDED, ids(), FORWARD, BETWEEN_STEPS, "[I7]"),
                Arguments.of(with(attempt(3)), ONE_LANDED, ids(), FORWARD, MID_STEP, "[]"));
    }

    @ParameterizedTest
    @MethodSource("table")
    void theInvariants(List<JournalRecord> journal, Main sinceK, Set<EntryId> queued, Set<Audit.Edge> ancestry,
                       boolean midStep, String expected) {
        Audit.Input input = new Audit.Input(journal, sinceK, Landings.of(sinceK.commits()), queued, ancestry, !midStep, T0,
                INSTALLED);
        assertEquals(expected, invariants(Audit.check(input)));
    }

    /** K is the journal's last Cutover, one home: a later cutover moves it. */
    @Test
    void kIsTheLastCutover() {
        List<JournalRecord> journal = List.of(cutover(1), seen(1), cutover(2));
        assertEquals(Optional.of(sha(2)), Audit.cutover(journal));
        Audit.Input input = new Audit.Input(journal, mainSince(Optional.of(sha(2))), landed(), ids(), Set.of(edge(1, 2)),
                true, T0, INSTALLED);
        assertEquals("[]", invariants(Audit.check(input)));
        assertEquals(Optional.empty(), Audit.cutover(List.of(seen(1))));
    }

    /** Main gathered from another K than the journal's is the shell's bug: said loud, not audited. */
    @Test
    void mainGatheredFromTheWrongKThrows() {
        List<JournalRecord> journal = List.of(cutover(1), seen(1));
        assertThrows(IllegalArgumentException.class, () -> Audit.check(new Audit.Input(journal,
                mainSince(Optional.of(sha(2))), landed(), ids(), Set.of(edge(1, 2)), true, T0, INSTALLED)));
        assertThrows(IllegalArgumentException.class, () -> Audit.check(new Audit.Input(List.of(seen(1)),
                mainSince(Optional.of(sha(1))), landed(), ids(), Set.of(), true, T0, INSTALLED)));
    }

    /** The journal is kept whole and given whole: a Verified as the first of 50,000 records still covers its commit. */
    @Test
    void aVerifiedFarBackStillCoversItsCommit() {
        List<JournalRecord> journal = new ArrayList<>();
        journal.add(verified(1, 2));
        journal.addAll(Collections.nCopies(49_998, seen(2)));
        journal.add(cutover(1));
        Audit.Report report = Audit.check(new Audit.Input(journal, ONE_LANDED, Landings.of(ONE_LANDED.commits()), ids(),
                Set.of(), true, T0, INSTALLED));
        assertEquals(50_000, journal.size());
        assertEquals("[]", invariants(report));
    }

    @Test
    void pastAHundredThousandRecordsTheReportAsksForCompaction() {
        List<JournalRecord> journal = Collections.nCopies(100_001, seen(1));
        Audit.Report report = Audit.check(new Audit.Input(journal, NOT_CUT_OVER, landed(), ids(), Set.of(), true, T0,
                INSTALLED));
        assertTrue(report.notes().contains("the journal holds 100001 records, past 100000: compact it"), report.notes()
                .toString());
        Audit.Report under = Audit.check(new Audit.Input(Collections.nCopies(100_000, seen(1)), NOT_CUT_OVER, landed(), ids(),
                Set.of(), true, T0, INSTALLED));
        assertEquals(1, under.notes().size(), under.notes().toString());
    }

    /**
     * The C4 order of migration: entry 1 lands through the queue, then the cutover is made at its commit. Main since K
     * holds nothing, and entry 1 is landed all the same - read from all of main.
     */
    @Test
    void anEntryLandedAtTheCutoverIsSettled() {
        List<JournalRecord> journal = List.of(attempt(1), verified(1, 2), cutover(2));
        Audit.Input input = new Audit.Input(journal, mainSince(Optional.of(sha(2))), landed(landing(2, 1)), ids(), Set.of(),
                true, T0, INSTALLED);
        assertEquals("[]", invariants(Audit.check(input)));
    }

    /** With no K, main's invariants are not passed in silence: the report says they were not checked. */
    @Test
    void beforeTheCutoverTheReportSaysWhatWasNotChecked() {
        Main sinceK = mainSince(Optional.empty(), merge(2), plain(3));
        Audit.Input input = new Audit.Input(List.of(seen(1)), sinceK, landed(), ids(), Set.of(edge(1, 3)), true, T0,
                new Audit.Install(Optional.empty(), "h1", sha(2), Optional.of(sha(2))));
        Audit.Report report = Audit.check(input);
        assertEquals("[]", invariants(report));
        assertEquals(List.of("not yet cut over: I1 to I3 and the pre-push hook (I8) not checked"), report.notes());
    }

    /**
     * I7 flags a queue nobody is working (amendment 1, R3), at 12:00 with the lock free: stranded when a ref is older
     * than 30 minutes and nothing has been journaled for 30 minutes, and no halt is in force; a halt in force is its
     * own line. A ref is named by when it was made; "on main" lands it as commit 2.
     */
    static Stream<Arguments> stranded() {
        EntryId at1150 = idAt(11, 50, 1), at1100 = idAt(11, 0, 1), at1000 = idAt(10, 0, 1);
        return Stream.of(
                // refs, journal, landed, lock held -> broken
                Arguments.of(Set.of(at1150), List.of(), Map.of(), false, "[]"),
                Arguments.of(Set.of(at1100), List.of(), Map.of(), false, "[I7]"),
                Arguments.of(Set.of(at1100), List.of(seenAt(11, 45)), Map.of(), false, "[]"),
                Arguments.of(Set.of(at1100), List.of(seenAt(11, 29)), Map.of(), false, "[I7]"),
                // the audit's own record is no sign of work: it does not end the quiet
                Arguments.of(Set.of(at1100), List.of(seenAt(11, 0), new JournalRecord.Fault(RUN, time(11, 59), FaultKind.AUDIT,
                        null, "I7: stranded")), Map.of(), false, "[I7]"),
                Arguments.of(Set.of(at1100), List.of(new JournalRecord.Fault(RUN, time(11, 59), FaultKind.AUDIT, null,
                        "I7: stranded")), Map.of(), false, "[I7]"),
                // four waiting their turn behind a queue that is moving: no false alarm
                Arguments.of(Set.of(idAt(10, 0, 1), idAt(10, 0, 2), idAt(10, 0, 3), idAt(10, 0, 4)), List.of(seenAt(11, 58)),
                        Map.of(), false, "[]"),
                Arguments.of(Set.of(at1100), List.of(new JournalRecord.Halted(RUN, time(11, 0), "3 integrator faults")),
                        Map.of(), false, "[HALTED]"),
                // a ref left for a settled entry: the next run's first Settle clears it - a note
                Arguments.of(Set.of(at1150), List.of(seenAt(11, 55)), Map.of(at1150, sha(2)), false, "[]"),
                Arguments.of(Set.of(at1150), List.of(new JournalRecord.Bounced(RUN, time(11, 55), at1150,
                        BounceReason.CONFLICT, List.of())), Map.of(), false, "[]"),
                // ... unless nobody is working the queue to clear it
                Arguments.of(Set.of(at1000), List.of(seenAt(11, 0)), Map.of(at1000, sha(2)), false, "[I7]"),
                Arguments.of(Set.of(), List.of(new JournalRecord.Attempt(RUN, time(11, 0), at1100, sha(7), sha(1))), Map.of(),
                        false, "[I7]"),
                Arguments.of(Set.of(at1000), List.of(), Map.of(), true, "[]"));
    }

    @ParameterizedTest
    @MethodSource("stranded")
    void aQueueNobodyIsWorking(Set<EntryId> refs, List<JournalRecord> journal, Map<EntryId, Sha> landed, boolean lockHeld,
                               String expected) {
        Audit.Input input = new Audit.Input(journal, NOT_CUT_OVER, new Landings(landed), refs, Set.of(), !lockHeld, T0,
                INSTALLED);
        assertEquals(expected, invariants(Audit.check(input)));
    }

    @Test
    void whatI7LeavesToTheNextRunIsANote() {
        EntryId ref = idAt(11, 50, 1);
        Audit.Report report = Audit.check(new Audit.Input(List.of(seenAt(11, 55)), NOT_CUT_OVER,
                new Landings(Map.of(ref, sha(2))), Set.of(ref), Set.of(), true, T0, INSTALLED));
        assertTrue(report.notes().stream().anyMatch(n -> n.startsWith("refs/queue/" + ref)
                && n.endsWith("to be cleared by the next run")), report.notes().toString());
        Audit.Report held = Audit.check(new Audit.Input(List.of(), NOT_CUT_OVER, landed(), Set.of(ref), Set.of(), false, T0,
                INSTALLED));
        assertTrue(held.notes().contains("an integrator is mid-step: I7 not checked"), held.notes().toString());
    }

    @Test
    void aHaltInForceIsItsOwnFaultLine() {
        List<Audit.Violation> found = Audit.check(new Audit.Input(List.of(new JournalRecord.Halted(RUN, T0, "the reason")),
                NOT_CUT_OVER, landed(), Set.of(), Set.of(), true, T0, INSTALLED)).violations();
        assertEquals("[Fault] HALTED: the reason", found.getFirst().toString());
    }

    static Stream<Arguments> installs() {
        Optional<Sha> k = Optional.of(sha(1));
        return Stream.of(
                // cut over, the hook's hash now, the last landing/ change as of the jar's commit, and of main -> broken
                // built from a later tip (say commit 5) whose last landing/ change is main's, commit 2: clean
                Arguments.of(k, Optional.of("h1"), 2, Optional.of(sha(2)), "[]"),
                Arguments.of(k, Optional.empty(), 2, Optional.of(sha(2)), "[I8]"),
                Arguments.of(k, Optional.of("h0"), 2, Optional.of(sha(2)), "[I8]"),
                // main has changed landing/ since the jar's commit: stale
                Arguments.of(k, Optional.of("h1"), 1, Optional.of(sha(2)), "[I8]"),
                Arguments.of(k, Optional.empty(), 1, Optional.of(sha(2)), "[I8, I8]"),
                Arguments.of(k, Optional.of("h1"), 2, Optional.empty(), "[]"),
                // before the cutover the hook is not installed yet; the jar is
                Arguments.of(Optional.empty(), Optional.empty(), 2, Optional.of(sha(2)), "[]"),
                Arguments.of(Optional.empty(), Optional.empty(), 1, Optional.of(sha(2)), "[I8]"));
    }

    @ParameterizedTest
    @MethodSource("installs")
    void theInstall(Optional<Sha> k, Optional<String> hook, int builtChange, Optional<Sha> mainChange, String expected) {
        Main sinceK = k.isPresent() ? ONE_LANDED : NOT_CUT_OVER;
        List<JournalRecord> journal = k.isPresent() ? CLEAN : List.of(seen(1));
        Audit.Install install = new Audit.Install(hook, "h1", sha(builtChange), mainChange);
        Audit.Input input = new Audit.Input(journal, sinceK, Landings.of(sinceK.commits()), ids(), FORWARD, true, T0,
                install);
        assertEquals(expected, invariants(Audit.check(input)));
    }

    /** The ancestry the shell is asked to confirm: each SeenMain against the next that differs, the last against main. */
    @Test
    void theQuestionsForTheShell() {
        assertEquals(List.of(edge(1, 2), edge(2, 4), edge(4, 5)),
                Audit.questions(List.of(seen(1), seen(1), cutover(3), seen(2), seen(4)), sha(5)));
        assertEquals(List.of(), Audit.questions(List.of(seen(5)), sha(5)));
        assertEquals(List.of(), Audit.questions(List.of(), sha(5)));
    }

    @Test
    void aViolationSaysWhatAndWhere() {
        Main sinceK = main(landing(2, 1), plain(3));
        List<Audit.Violation> found = Audit.check(new Audit.Input(CLEAN, sinceK, Landings.of(sinceK.commits()), ids(),
                FORWARD, true, T0, INSTALLED)).violations();
        assertEquals("I2", found.getFirst().invariant());
        assertTrue(found.getFirst().detail().contains(sha(3).value()), found.getFirst().detail());
    }

    private static List<JournalRecord> with(JournalRecord... more) {
        List<JournalRecord> journal = new ArrayList<>(CLEAN);
        journal.addAll(List.of(more));
        return journal;
    }

    private static Commit commit(int sha, String... ids) {
        return new Commit(sha(sha), List.of(sha(sha - 1)), List.of(ids), List.of());
    }

    private static Audit.Edge edge(int ancestor, int descendant) {
        return new Audit.Edge(sha(ancestor), sha(descendant));
    }

    private static Set<EntryId> ids(int... ns) {
        return IntStream.of(ns).mapToObj(Fixtures::id).collect(Collectors.toSet());
    }

    /** Entry {@code n}, made at {@code hour:minute} on the 26th. */
    private static EntryId idAt(int hour, int minute, int n) {
        return new EntryId(String.format("20261126T%02d%02d00Z-%08x", hour, minute, n));
    }

    private static Instant time(int hour, int minute) {
        return T0.minus(Duration.ofHours(12)).plus(Duration.ofHours(hour)).plus(Duration.ofMinutes(minute));
    }

    /** Main seen at commit 1 - nothing changed - at {@code hour:minute}: a record, and when the journal last spoke. */
    private static JournalRecord seenAt(int hour, int minute) {
        return new JournalRecord.SeenMain(RUN, time(hour, minute), sha(1));
    }

    private static String invariants(Audit.Report report) {
        return report.violations().stream().map(Audit.Violation::invariant).toList().toString();
    }
}
