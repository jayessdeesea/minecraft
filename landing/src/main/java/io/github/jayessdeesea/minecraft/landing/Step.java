package io.github.jayessdeesea.minecraft.landing;

import java.time.Instant;
import java.util.Optional;

/** The integrator's next step, as {@link Integrator#next} chooses it; the shell carries it out, then asks again. */
public sealed interface Step {

    /** Attempt nothing; {@code record} whether {@code Halted} is yet to be written (the first time). */
    record Halt(String reason, boolean record) implements Step {
    }

    /**
     * Settle entry {@code id} as {@code outcome}: write its record if {@code record} (never for Landed - that is main's),
     * then delete its request and withdrawal refs.
     */
    record Settle(EntryId id, EntryState outcome, boolean record) implements Step {
        public Settle {
            if (!outcome.settled()) {
                throw new IllegalArgumentException("settling " + id + " as " + outcome);
            }
        }
    }

    /** Attempt entry {@code id} again: {@code attempts} before it ended with no outcome - a crash, a timeout, main moved. */
    record Reconcile(EntryId id, int attempts) implements Step {
    }

    /** Attempt entry {@code id}, the oldest request, for the first time. */
    record Attempt(EntryId id) implements Step {
    }

    /** Nothing to do now: the queue is empty, or an integrator fault is being waited out {@code until}. */
    record Idle(Optional<Instant> until) implements Step {
    }
}
