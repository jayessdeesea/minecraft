package io.github.jayessdeesea.minecraft.bot.nav;

/**
 * The block columns a body's box overlaps - where a position, a double as the server's is, becomes cells, in one place.
 * A body up against a column's face on its high side, or over it by no more than {@link #EDGE}, isn't in that column;
 * on its low side a hair over is over, as it was where this was written out by hand. Making the two sides one rule
 * changes where a soldier fits and stands, and goes with the changes that want it (backlog/mobility.md).
 *
 * @param minX the least column's x
 * @param maxX the greatest column's x
 * @param minZ the least column's z
 * @param maxZ the greatest column's z
 */
public record Footprint(int minX, int maxX, int minZ, int maxZ) {

    /** Half a player's width: its box is 0.6 by 0.6. */
    public static final double HALF_WIDTH = 0.3;
    /** How far over a column's face a box may reach and not be in the column. */
    public static final double EDGE = 1e-9;

    /** The columns under a body standing at ({@code x}, {@code z}). */
    public static Footprint of(double x, double z) {
        return new Footprint((int) Math.floor(x - HALF_WIDTH), (int) Math.floor(x + HALF_WIDTH - EDGE),
                (int) Math.floor(z - HALF_WIDTH), (int) Math.floor(z + HALF_WIDTH - EDGE));
    }
}
