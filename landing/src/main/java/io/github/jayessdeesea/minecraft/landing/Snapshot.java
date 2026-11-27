package io.github.jayessdeesea.minecraft.landing;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * What the integrator has seen, after a fetch, to choose its next step by: the journal in the order written, where
 * entries landed on main, the request refs ({@code refs/queue/<id>}), the withdrawal refs
 * ({@code refs/queue-withdraw/<id>}) and the time.
 */
public record Snapshot(List<JournalRecord> journal, Landings landed, Set<EntryId> queued, Set<EntryId> withdrawals,
                       Instant now) {

    public Snapshot {
        if (landed == null || now == null) {
            throw new IllegalArgumentException("a snapshot with no landings or no time");
        }
        journal = List.copyOf(journal);
        queued = Set.copyOf(queued);
        withdrawals = Set.copyOf(withdrawals);
    }
}
