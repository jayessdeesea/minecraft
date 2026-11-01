package io.github.jayessdeesea.minecraft.command;

/**
 * Spacing between soldiers in a formation, in blocks (= metres). Doctrine's normal wedge interval
 * is 10 m and "expands and contracts depending on the terrain" (ATP 3-21.8 ¶3-46); here that's
 * {@link #EXTENDED}, with tighter options because Minecraft's short sight lines and mob density
 * make 10 blocks loose for a two- or three-soldier team. See docs/military-doctrine.md §2.
 */
public enum Interval {

    TIGHT(3),
    NORMAL(6),
    EXTENDED(10);

    private final double blocks;

    Interval(double blocks) {
        this.blocks = blocks;
    }

    public double blocks() {
        return blocks;
    }
}
