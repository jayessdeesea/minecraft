package io.github.jayessdeesea.minecraft.command;

import java.util.function.BiPredicate;

/**
 * Which edges of a flattened chunk slope out to the ground round it (operator, 2026-11-21): one flag a side, north (the
 * least z), east (the most x), south (the most z), west (the least x). Off beside a chunk the force has made something
 * of, so a flatten never cuts into a neighbouring template; on elsewhere. The order may set each.
 */
public record Slopes(boolean north, boolean east, boolean south, boolean west) {

    /** Every side sloped. */
    public static final Slopes ALL = new Slopes(true, true, true, true);

    /** Each side of chunk ({@code chunkX}, {@code chunkZ}) sloped unless the chunk past it is {@code made} something of. */
    public static Slopes around(int chunkX, int chunkZ, BiPredicate<Integer, Integer> made) {
        return new Slopes(!made.test(chunkX, chunkZ - 1), !made.test(chunkX + 1, chunkZ), !made.test(chunkX, chunkZ + 1), !made.test(chunkX - 1, chunkZ));
    }

    /**
     * Whether a cell past the chunk's edges - {@code west} of it, {@code east}, {@code north}, {@code south}, any two at
     * a corner - is in its slope: every side it's past sloped.
     */
    boolean reaches(boolean west, boolean east, boolean north, boolean south) {
        return (!west || this.west) && (!east || this.east) && (!north || this.north) && (!south || this.south);
    }

    /** As said: "slopes north, east and west". */
    public String said() {
        StringBuilder s = new StringBuilder();
        for (String side : new String[]{north ? "north" : null, east ? "east" : null, south ? "south" : null, west ? "west" : null}) {
            if (side != null) {
                s.append(s.isEmpty() ? "" : ", ").append(side);
            }
        }
        return s.isEmpty() ? "no slopes" : "slopes " + s;
    }
}
