package io.github.jayessdeesea.minecraft.landing.shell;

/**
 * Where a test reaches into the integrator's step (design section 6, crash injection): told as each numbered step is
 * done, and asked what the push reported. The integrator's own is the default: nothing happens, the report stands.
 */
public interface Steps {

    /** Step {@code step} (1 fetch ... 10 the ref deleted) is done. */
    default void reached(int step) {
    }

    /** What the push reported, as the integrator is to hear it. */
    default boolean pushReported(boolean ok) {
        return ok;
    }
}
