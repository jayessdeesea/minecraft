package io.github.jayessdeesea.minecraft.landing;

/** A step that went wrong, to be journaled as a {@code Fault}: its kind says whose it is and what follows. */
public record Faulted(FaultKind kind, String detail) implements MergeOutcome, VerifyOutcome, PushOutcome {

    public Faulted {
        if (kind == null || detail == null) {
            throw new IllegalArgumentException("a fault with no kind or no detail");
        }
    }
}
