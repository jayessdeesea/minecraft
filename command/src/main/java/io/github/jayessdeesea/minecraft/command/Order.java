package io.github.jayessdeesea.minecraft.command;

/**
 * A goal handed down to a {@link Soldier} - what to achieve, not how. "How" (which direction to
 * step this tick, whether the next block is solid) is re-derived every tick by the soldier's own
 * decision core against current {@code Position} and {@code WorldView} state - see AGENTS.md's
 * "Behavior modeling" section. A closed set on purpose: exhaustive {@code switch} over variants
 * should be a compile error, not a silent no-op, whenever a new order kind is added.
 */
public sealed interface Order {

    /** No goal - a soldier with this order takes no movement action on tick. */
    record Idle() implements Order {
    }

    /** Trend toward (x, z), one step per tick, stopping (not pathing around) if blocked. */
    record TrendToward(double x, double z) implements Order {
    }
}
