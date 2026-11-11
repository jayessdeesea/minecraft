package io.github.jayessdeesea.minecraft.command;

import java.util.Optional;

/**
 * A soldier's own condition, as doctrine's LACE report carries it upward after contact
 * (docs/military-doctrine.md §4), mapped onto Minecraft: <b>L</b>iquids is food, <b>C</b>asualties
 * is health and deaths, <b>A</b>mmunition and <b>E</b>quipment are its {@link Kit} - empty until
 * the server has first described its inventory.
 *
 * @param health 0 to {@link #MAX_HEALTH}; 0 means dead
 * @param food   0 to {@link #MAX_FOOD}; health regenerates only at {@link #REGENERATION_FOOD} or more
 * @param deaths deaths since this soldier's connection joined
 * @param kit    weapon, arrows and armour, once known
 */
public record Condition(double health, int food, int deaths, Optional<Kit> kit) {

    public static final double MAX_HEALTH = 20;
    public static final int MAX_FOOD = 20;
    /** Vanilla natural regeneration needs a food level of at least 18. */
    public static final int REGENERATION_FOOD = 18;
}
