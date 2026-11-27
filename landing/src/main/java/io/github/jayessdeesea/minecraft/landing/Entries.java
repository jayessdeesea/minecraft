package io.github.jayessdeesea.minecraft.landing;

import java.util.List;

/** An entry's state, folded from the journal and where entries landed; the counts the caps are measured by. */
public final class Entries {

    private Entries() {
    }

    /**
     * A {@code Landing-Id} trailer on main wins over everything; then the first record that settles the entry; then
     * its attempts, if any; else it is queued.
     */
    public static EntryState state(EntryId id, List<JournalRecord> journal, Landings landed) {
        var sha = landed.of(id);
        if (sha.isPresent()) {
            return new EntryState.Landed(sha.get());
        }
        int attempts = 0;
        for (JournalRecord record : journal) {
            if (!about(record, id)) {
                continue;
            }
            switch (record) {
                case JournalRecord.Failed f -> {
                    return new EntryState.Failed(f.reason());
                }
                case JournalRecord.Bounced b -> {
                    return new EntryState.Bounced(b.reason());
                }
                case JournalRecord.Withdrawn w -> {
                    return new EntryState.Withdrawn();
                }
                case JournalRecord.Attempt a -> attempts++;
                default -> {
                }
            }
        }
        return attempts == 0 ? new EntryState.Queued() : new EntryState.Verifying(attempts);
    }

    /**
     * The tries at the entry, for the five-attempt cap: its Attempts, less each Attempt an integrator's fault about the
     * entry ended before its next Attempt. An entry is charged only for what is its own: a crash (an Attempt with
     * nothing after it) and its own faults count; the integrator's faults are bounded by the halt, not here.
     */
    static int tries(EntryId id, List<JournalRecord> journal) {
        int tries = 0;
        boolean open = false;
        for (JournalRecord record : journal) {
            if (!about(record, id)) {
                continue;
            }
            if (record instanceof JournalRecord.Attempt) {
                tries++;
                open = true;
            } else if (record instanceof JournalRecord.Fault f) {
                if (open && f.kind().blame() == FaultKind.Blame.INTEGRATOR) {
                    tries--;
                }
                open = false;
            }
        }
        return tries;
    }

    /** The faults of {@code kind} against entry {@code id}. */
    static int faults(EntryId id, FaultKind kind, List<JournalRecord> journal) {
        int n = 0;
        for (JournalRecord record : journal) {
            if (record instanceof JournalRecord.Fault f && f.kind() == kind && about(f, id)) {
                n++;
            }
        }
        return n;
    }

    static boolean about(JournalRecord record, EntryId id) {
        return record.entry().filter(id::equals).isPresent();
    }
}
