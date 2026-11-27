package io.github.jayessdeesea.minecraft.landing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The integrator's decision, one step at a time (design sections 3 and 5, amendment 1). In order: halted, attempt
 * nothing; settle what is settled - landed on main, recorded, withdrawn, or over a cap; wait out an integrator fault's
 * back-off; attempt again an entry with an attempt and no outcome; attempt the oldest request; else idle. An entry is
 * charged only for what is its own: it fails at its fifth try ({@link Entries#tries}), its second verify timeout or the
 * third time main moves under its push. The integrator's own faults are bounded by a halt, never by failing entries:
 * three running, a fetch's aside, halt the queue until the operator resumes it.
 */
public final class Integrator {

    static final int MOST_ATTEMPTS = 5;
    static final int MOST_TIMEOUTS = 2;
    static final int MOST_MAIN_MOVES = 3;
    /** The integrator's faults running, a fetch's aside, that halt the queue. */
    static final int MOST_INTEGRATOR_FAULTS = 3;
    /** The wait after the first, second and any later integrator fault running. */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(5), Duration.ofMinutes(10), Duration.ofMinutes(20));

    private Integrator() {
    }

    public static Step next(Snapshot snapshot) {
        Optional<JournalRecord.Halted> halted = halted(snapshot.journal());
        if (halted.isPresent()) {
            return new Step.Halt(halted.get().reason(), false);
        }
        Streak streak = Streak.of(snapshot.journal(), snapshot.landed());
        if (streak.running() >= MOST_INTEGRATOR_FAULTS) {
            JournalRecord.Fault f = streak.lastHalting();
            return new Step.Halt(streak.running() + " integrator faults running, the last " + f.kind() + " about "
                    + f.entry().map(EntryId::value).orElse("no entry") + ": " + f.detail(), true);
        }
        TreeSet<EntryId> asked = new TreeSet<>(snapshot.queued());
        asked.addAll(snapshot.withdrawals());
        for (EntryId id : asked) {
            Optional<Step.Settle> settle = settle(id, snapshot);
            if (settle.isPresent()) {
                return settle.get();
            }
        }
        if (streak.faults() > 0) {
            Instant until = streak.last().plus(BACKOFF.get(Math.min(streak.faults(), BACKOFF.size()) - 1));
            if (snapshot.now().isBefore(until)) {
                return new Step.Idle(Optional.of(until));
            }
        }
        TreeSet<EntryId> queued = new TreeSet<>(snapshot.queued());
        for (EntryId id : queued) {
            if (Entries.state(id, snapshot.journal(), snapshot.landed()) instanceof EntryState.Verifying v) {
                return new Step.Reconcile(id, v.attempts());
            }
        }
        return queued.isEmpty() ? new Step.Idle(Optional.empty()) : new Step.Attempt(queued.first());
    }

    private static Optional<Step.Settle> settle(EntryId id, Snapshot snapshot) {
        List<JournalRecord> journal = snapshot.journal();
        EntryState state = Entries.state(id, journal, snapshot.landed());
        if (state.settled()) {
            return Optional.of(new Step.Settle(id, state, false));
        }
        if (snapshot.withdrawals().contains(id)) {
            return Optional.of(new Step.Settle(id, new EntryState.Withdrawn(), true));
        }
        if (Entries.faults(id, FaultKind.VERIFY_TIMEOUT, journal) >= MOST_TIMEOUTS) {
            return failed(id, FailReason.TIMED_OUT);
        }
        if (Entries.faults(id, FaultKind.OUTSIDE_PUSH, journal) >= MOST_MAIN_MOVES) {
            return failed(id, FailReason.MAIN_MOVED);
        }
        if (Entries.tries(id, journal) >= MOST_ATTEMPTS) {
            return failed(id, FailReason.ATTEMPTS);
        }
        return Optional.empty();
    }

    /** How long a kill is given to prove its tree gone (the job's ten seconds). */
    static final Duration KILL = Duration.ofSeconds(10);
    /** What a run keeps in hand past its last attempt's worst case. */
    static final Duration BUDGET_MARGIN = Duration.ofMinutes(5);

    /**
     * Whether a run may start another attempt: only while what has gone ({@code elapsed}), and the attempt's worst case -
     * a whole verify, its kill, a push and the fetch after it each at their remote time - and a margin, fit the run's
     * {@code budget}. The task's own time limit is the budget and a margin, so a run is never stopped mid-push; out of
     * budget, it ends and the next start takes up the queue.
     */
    public static boolean budgetAllows(Duration elapsed, Duration verifyTimeout, Duration remoteTimeout, Duration budget) {
        return elapsed.plus(leastBudget(verifyTimeout, remoteTimeout)).compareTo(budget) < 0;
    }

    /** One attempt's worst case and the margin: a budget not above it has room for no attempt, and is refused. */
    public static Duration leastBudget(Duration verifyTimeout, Duration remoteTimeout) {
        return verifyTimeout.plus(KILL).plus(remoteTimeout.multipliedBy(2)).plus(BUDGET_MARGIN);
    }

    /**
     * Whether a run should start at all: not while the journal ends in failed fetches or a pass's own git failures
     * (REPO) whose back-off is still running - an offline machine's, or a broken repository's, every scheduled start
     * would journal another. The run that is not due exits writing nothing.
     */
    public static boolean runDue(List<JournalRecord> journal, Instant now) {
        int failed = 0;
        Instant last = null;
        for (int i = journal.size() - 1; i >= 0; i--) {
            if (!(journal.get(i) instanceof JournalRecord.Fault f
                    && (f.kind() == FaultKind.FETCH_FAILED || f.kind() == FaultKind.REPO))) {
                break;
            }
            failed++;
            if (last == null) {
                last = f.at();
            }
        }
        return failed == 0 || !now.isBefore(last.plus(BACKOFF.get(Math.min(failed, BACKOFF.size()) - 1)));
    }

    /**
     * The halt a pass's own failure brings, read from the journal alone, where main cannot be: three integrator faults
     * running, a fetch's aside, since a Verified or a Resumed, and no halt already in force. Each Verified is taken as
     * landed, so this halts no sooner than {@link #next} would.
     */
    public static Optional<String> haltDue(List<JournalRecord> journal) {
        if (halted(journal).isPresent()) {
            return Optional.empty();
        }
        java.util.Map<EntryId, Sha> verified = new java.util.HashMap<>();
        journal.forEach(r -> {
            if (r instanceof JournalRecord.Verified v) {
                verified.put(v.id(), v.candidate());
            }
        });
        Streak streak = Streak.of(journal, new Landings(verified));
        return streak.running() >= MOST_INTEGRATOR_FAULTS
                ? Optional.of(streak.running() + " integrator faults running, the last " + streak.lastHalting().kind() + ": "
                + streak.lastHalting().detail()) : Optional.empty();
    }

    /** The halt in force: the last {@code Halted} with no {@code Resumed} after it. Every waiter reads it as exit 4. */
    public static Optional<JournalRecord.Halted> halted(List<JournalRecord> journal) {
        JournalRecord.Halted halted = null;
        for (JournalRecord record : journal) {
            if (record instanceof JournalRecord.Halted h) {
                halted = h;
            } else if (record instanceof JournalRecord.Resumed) {
                halted = null;
            }
        }
        return Optional.ofNullable(halted);
    }

    private static Optional<Step.Settle> failed(EntryId id, FailReason reason) {
        return Optional.of(new Step.Settle(id, new EntryState.Failed(reason), true));
    }

    /**
     * Two counts of the integrator's faults. For the back-off, every one since the queue last got anywhere - an entry
     * settled, an entry's own fault, a verified candidate on main, the operator's resume - and the last one's time.
     * For the halt, those but a fetch's since a verified candidate was found on main or the operator resumed: a settle,
     * a bounce or an entry's fault does not break that run, and the last of them names the halt.
     */
    private record Streak(int faults, Instant last, int running, JournalRecord.Fault lastHalting) {

        static Streak of(List<JournalRecord> journal, Landings landed) {
            int faults = 0;
            Instant last = null;
            int running = 0;
            JournalRecord.Fault lastHalting = null;
            for (JournalRecord record : journal) {
                switch (record) {
                    case JournalRecord.Fault f when f.kind().blame() == FaultKind.Blame.INTEGRATOR -> {
                        faults++;
                        last = f.at();
                        if (f.kind() != FaultKind.FETCH_FAILED) {
                            running++;
                            lastHalting = f;
                        }
                    }
                    case JournalRecord.Fault f when f.kind().blame() == FaultKind.Blame.ENTRY -> faults = 0;
                    case JournalRecord.Verified v when landed(v, landed) -> {
                        faults = 0;
                        running = 0;
                    }
                    case JournalRecord.Resumed r -> {
                        faults = 0;
                        running = 0;
                    }
                    default -> {
                        if (record.settles()) {
                            faults = 0;
                        }
                    }
                }
            }
            return new Streak(faults, last, running, lastHalting);
        }

        private static boolean landed(JournalRecord.Verified verified, Landings landed) {
            return landed.of(verified.id()).filter(verified.candidate()::equals).isPresent();
        }
    }
}
