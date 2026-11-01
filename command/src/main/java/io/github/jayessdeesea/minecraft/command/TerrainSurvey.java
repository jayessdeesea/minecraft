package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * A coarse picture of the ground around a squad - what a leader would get from a map and a look
 * around before picking an objective. A grid of samples every {@code spacing} blocks out to
 * {@code radius} blocks from ({@code originX}, {@code originZ}) - the point man's position -
 * each with the ground height there, whether it's land or water, and whether the point man can
 * actually walk to it. Choosing among reachable spots up front beats choosing blind and collecting
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

    /** {@code height} is the y a soldier's feet would be at (LAND) or the liquid's top (WATER). */
    public record Sample(int x, int z, Ground ground, int height, boolean reachable) {
    }
}
