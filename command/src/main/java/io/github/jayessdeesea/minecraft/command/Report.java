package io.github.jayessdeesea.minecraft.command;

/**
 * The "what I'm doing / what I can't do" line of a soldier's SITREP (docs/military-doctrine.md §4).
 * Closed on purpose, like {@link Order}: a squad leader's reasoning over these must handle every
 * case.
 */
public sealed interface Report {

    /** No task. */
    record Idle() implements Report {
    }

    /** Executing the current order (WILCO, in progress). */
    record Moving() implements Report {
    }

    /** At the ordered position. */
    record InPosition() implements Report {
    }

    /** Hostiles within contact range - the contact reflex has taken over (SPOTREP-worthy, §4). */
    record InContact(int hostiles) implements Report {
    }

    /**
     * CANTCO - "unable to comply" (docs/military-doctrine.md §3): the order can't be carried out,
     * and {@code reason} says why, so the echelon above can re-task instead of waiting on a soldier
     * that will never arrive.
     */
    record Cantco(String reason) implements Report {
    }
}
