package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Where soldiers have been killed, and when: a place that kills again and again is a danger area, bypassed
 * rather than crossed (ATP 3-21.8, danger areas). Live (field446-449), nine of seventeen deaths in an hour
 * fell within one mine's staircase and the caves it crossed, and its squad was sent back down it after
 * each. Not thread-safe on its own: the caller holds it under one lock.
 */
public final class DangerAreas {

    /** Deaths within this many blocks across of a place count against it. */
    public static final double RADIUS = 16;
    /** How many deaths there within {@link #WINDOW_MILLIS} make it a danger area. */
    public static final int DEATHS = 3;
    /** The window deaths are counted over: a day and a night. */
    public static final long WINDOW_MILLIS = 20 * 60_000;

    private record Death(Position at, long millis) {
    }

    private final List<Death> deaths = new ArrayList<>();

    /** A soldier killed at {@code at}, at {@code millis}. */
    public void killed(Position at, long millis) {
        deaths.add(new Death(at, millis));
        deaths.removeIf(d -> millis - d.millis() > WINDOW_MILLIS);
    }

    /** How many were killed within {@link #RADIUS} across of column ({@code x}, {@code z}) in the window before {@code now}. */
    public long near(double x, double z, long now) {
        return deaths.stream().filter(d -> now - d.millis() <= WINDOW_MILLIS && Math.hypot(d.at().x() - x, d.at().z() - z) <= RADIUS).count();
    }

    /** Whether column ({@code x}, {@code z}) is a danger area as of {@code now}. */
    public boolean dangerous(double x, double z, long now) {
        return near(x, z, now) >= DEATHS;
    }
}
