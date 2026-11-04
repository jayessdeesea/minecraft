package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * A coarse picture of the ground around a squad - what a leader would get from a map and a look
 * around before picking an objective. A grid of samples every {@code spacing} blocks out to
 * {@code radius} blocks from ({@code originX}, {@code originZ}) - the point man's position -
 * each with the ground height there, whether it's land or water, and whether the point man can
 * actually get there - walking, or climbing when he carries blocks to climb with. Choosing among reachable spots up front beats choosing blind and collecting
 * CANTCOs (docs/military-doctrine.md §3).
 */
public record TerrainSurvey(int originX, int originZ, int spacing, int radius, List<Sample> samples) {

    public enum Ground {
        LAND,
        /** Water or lava at the surface - soldiers don't swim. */
        WATER,
        /** Not loaded, or nothing standable found near the squad's height. */
        UNKNOWN
    }

    /**
     * {@code height} is the y a soldier's feet would be at (LAND) or the liquid's top (WATER).
     * {@code climb} is how many blocks the point man spends pillaring to get there - 0 for a walk,
     * and for anywhere unreachable.
     */
    public record Sample(int x, int z, Ground ground, int height, boolean reachable, int climb) {

        public Sample {
            if (climb < 0 || climb > 0 && !reachable) {
                throw new IllegalArgumentException("a climb of " + climb + " to " + (reachable ? "" : "un") + "reachable ground");
            }
        }

        /** Ground the point man walks to, or can't reach. */
        public Sample(int x, int z, Ground ground, int height, boolean reachable) {
            this(x, z, ground, height, reachable, 0);
        }
    }
}
