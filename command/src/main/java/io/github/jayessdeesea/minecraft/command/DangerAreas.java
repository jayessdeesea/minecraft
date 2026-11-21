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
    /**
     * The window deaths are counted over: three hours, nine days and nights. Live (field450-455), a mine's
     * stairs killed eight in three and a half hours, never three within one day - fifteen minutes to an
     * hour apart.
     */
    public static final long WINDOW_MILLIS = 3 * 60 * 60_000L;

    private record Death(Position at, long millis) {
    }

    private final List<Death> deaths = new ArrayList<>();

    /** A soldier killed at {@code at}, at {@code millis}. */
    public void killed(Position at, long millis) {
        deaths.add(new Death(at, millis));
        deaths.removeIf(d -> millis - d.millis() > WINDOW_MILLIS);
    }

    /** The deaths still in the window as of {@code now}, each x, y, z and when: to be kept across a restart. */
    public List<long[]> rows(long now) {
        return deaths.stream().filter(d -> now - d.millis() <= WINDOW_MILLIS)
                .map(d -> new long[]{(long) Math.floor(d.at().x()), (long) Math.floor(d.at().y()), (long) Math.floor(d.at().z()), d.millis()})
                .toList();
    }

    /** Deaths kept from before a restart, as {@link #rows}. */
    public void restore(List<long[]> rows) {
        rows.forEach(r -> deaths.add(new Death(new Position(r[0] + 0.5, r[1], r[2] + 0.5), r[3])));
    }

    /** How many were killed within {@link #RADIUS} across of column ({@code x}, {@code z}) in the window before {@code now}. */
    public long near(double x, double z, long now) {
        return deaths.stream().filter(d -> now - d.millis() <= WINDOW_MILLIS && Math.hypot(d.at().x() - x, d.at().z() - z) <= RADIUS).count();
    }

    /** How far up or down from a level its deaths count for one of the level's tunnels ({@link #near(double, double, double, long)}). */
    public static final double LEVEL_HEIGHT = 8;

    /**
     * How many were killed within {@link #RADIUS} across of ({@code x}, {@code z}) and {@link #LEVEL_HEIGHT} up or down of
     * {@code y}: a tunnel's danger, at its own level - the column's {@link #near(double, double, long)} is a mine's
     * stairs', every level on them. Live (field599), deaths on the surface by a mine's head gave up its tunnels 60 below.
     */
    public long near(double x, double y, double z, long now) {
        return deaths.stream().filter(d -> now - d.millis() <= WINDOW_MILLIS && Math.hypot(d.at().x() - x, d.at().z() - z) <= RADIUS
                && Math.abs(d.at().y() - y) <= LEVEL_HEIGHT).count();
    }

    /** Whether column ({@code x}, {@code z}) is a danger area as of {@code now}. */
    public boolean dangerous(double x, double z, long now) {
        return near(x, z, now) >= DEATHS;
    }
}
