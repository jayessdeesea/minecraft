package io.github.jayessdeesea.minecraft.landing;

/**
 * A push of the verified candidate, classified from what a fetch after it found - never from the push's error text.
 * {@code mainMoved}: main is no longer the candidate's parent.
 */
public sealed interface PushOutcome permits PushOutcome.Landed, PushOutcome.Halt, Faulted {

    record Landed() implements PushOutcome {
    }

    /** Pushed, and the id is not on main: something this design does not explain. Stop. */
    record Halt(String reason) implements PushOutcome {
    }

    /**
     * The design's table: the fetch failed - an integrator fault, nothing settled; the id on main - landed, the push's
     * reply lost or not; refused with main unmoved - an integrator fault (guard or credential); refused with main moved
     * - main moved under it; pushed and not on main - halt.
     */
    static PushOutcome of(boolean pushOk, boolean fetchOk, boolean idOnMain, boolean mainMoved) {
        if (!fetchOk) {
            return new Faulted(FaultKind.FETCH_FAILED, "the fetch after the push failed: nothing settled");
        }
        if (idOnMain) {
            return new Landed();
        }
        if (!pushOk && !mainMoved) {
            return new Faulted(FaultKind.PUSH_REFUSED, "the push was refused with main unmoved");
        }
        if (!pushOk) {
            return new Faulted(FaultKind.OUTSIDE_PUSH, "main moved under the push");
        }
        return new Halt("the push succeeded and the entry is not on main");
    }
}
