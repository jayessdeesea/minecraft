package io.github.jayessdeesea.minecraft.bot;

/**
 * How fast a soldier moves in each gait, blocks per tick, and what slows it.
 */
public final class MovementRates {
    private MovementRates() {
    }

    /**
     * Sprinting, vanilla's 5.6 blocks/s: only when the time is worth the food - an urgent move to a
     * goal more than {@link #SPRINT_DISTANCE} away, closing on a hostile it fights, moving under fire.
     * A routine walk is walked.
     */
    static final double SPRINT_STEP = 0.28;

    /** Far enough from its goal that a soldier sprints to catch up - a moving principal, a distant slot. */
    static final double SPRINT_DISTANCE = 8;

    /** At this food level or below a player can't sprint. */
    static final int SPRINT_FOOD_FLOOR = 6;

    /** A player with an item in use moves at a fifth of its speed - the vanilla client's rule. */
    static final double SHIELD_SLOWDOWN = 0.2;

    /** Swimming at the surface, blocks per tick: about vanilla's 2 blocks/s, half a walk. */
    static final double SWIM_STEP = 0.1;

    /** Afloat, the feet this far into the top block of water: the head stays out, so no drowning. */
    static final double SWIM_FLOAT = 0.2;

    /** Blocks per tick rising or settling in water, and climbing out onto a bank. */
    static final double SWIM_RISE = 0.2;
}
