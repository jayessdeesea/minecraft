package io.github.jayessdeesea.minecraft.landing.shell;

/** Wakes the integrator: the scheduler's task is run now rather than at its next five minutes. */
public interface Poke {

    /** No poke: the scheduler's next start will do. */
    Poke NONE = () -> {
    };

    /** Never fails the caller: a poke that does not take is said, and the scheduler still comes. */
    void poke();
}
