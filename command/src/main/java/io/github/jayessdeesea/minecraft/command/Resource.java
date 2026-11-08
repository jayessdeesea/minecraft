package io.github.jayessdeesea.minecraft.command;

/**
 * What a squad sees around it to work: a material it can be told to gather around its objective -
 * doctrine's Class IV (construction materials, docs/military-doctrine.md §12) - or ground to farm.
 */
public enum Resource {
    /** Logs, chopped by hand from trees. */
    WOOD,
    /** Cobblestone, quarried from exposed stone with a pickaxe. */
    STONE,
    /** Dirt, dug by hand - building blocks for a patrol base when a squad carries too few. */
    SOIL,
    /** Flint, one gravel block in ten dug by hand: a soldier's own errand for arrows, never surveyed. */
    FLINT,
    /** Ground by water to till for wheat: a farm's site, not something gathered. */
    FARMLAND;

    /** Whether a squad can be told to gather it. */
    public boolean gathered() {
        return this != FARMLAND;
    }
}
