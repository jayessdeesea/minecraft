package io.github.jayessdeesea.minecraft.landing;

/** Where an entry stands, as {@link Entries#state} folds it: never stored, so never two at once. */
public sealed interface EntryState {

    /** Whether it is over: landed, failed, bounced or withdrawn. */
    default boolean settled() {
        return !(this instanceof Queued || this instanceof Verifying);
    }

    /** Requested, not yet attempted. */
    record Queued() implements EntryState {
    }

    /** Attempted {@code attempts} times without an outcome. */
    record Verifying(int attempts) implements EntryState {
        public Verifying {
            if (attempts < 1) {
                throw new IllegalArgumentException("verifying after " + attempts + " attempts");
            }
        }
    }

    /** On main as commit {@code sha}. */
    record Landed(Sha sha) implements EntryState {
    }

    record Failed(FailReason reason) implements EntryState {
    }

    record Bounced(BounceReason reason) implements EntryState {
    }

    record Withdrawn() implements EntryState {
    }
}
