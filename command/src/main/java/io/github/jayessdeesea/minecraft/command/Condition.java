package io.github.jayessdeesea.minecraft.command;

/**
 * A soldier's own condition, as doctrine's LACE report carries it upward after contact
 * (docs/military-doctrine.md §4), mapped onto Minecraft: <b>L</b>iquids is food, <b>C</b>asualties
 * is health and deaths. Ammunition and equipment aren't reported yet (backlog/inventory-decoding.md).
 *
 * @param health 0 to {@link #MAX_HEALTH}; 0 means dead
 * @param food   0 to {@link #MAX_FOOD}; health regenerates only at {@link #REGENERATION_FOOD} or more
 * @param deaths deaths since this soldier's connection joined
 */
public record Condition(double health, int food, int deaths) {

    public static final double MAX_HEALTH = 20;
    public static final int MAX_FOOD = 20;
    /** Vanilla natural regeneration needs a food level of at least 18. */
    public static final int REGENERATION_FOOD = 18;
}
