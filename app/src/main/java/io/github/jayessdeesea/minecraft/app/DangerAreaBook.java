package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DangerAreas;
import io.github.jayessdeesea.minecraft.command.Position;

/**
 * The force's danger areas (command.DangerAreas), kept on disk so a restart doesn't forget them: live
 * (field450-455), a garrison restarted every twenty minutes never counted three deaths by one mine's stairs,
 * eight there in three and a half hours. Thread-safe: death handlers write, squad loops read.
 */
final class DangerAreaBook {

    private static final String KEY = "deaths";

    private final DangerAreas areas = new DangerAreas();
    private final WallStore store;

    DangerAreaBook(WallStore store) {
        this.store = store;
        store.loadRows(KEY).ifPresent(areas::restore);
    }

    /** A soldier killed at {@code at}, at {@code millis}: counted, and kept. */
    synchronized void killed(Position at, long millis) {
        areas.killed(at, millis);
        store.saveRows(KEY, areas.rows(millis));
    }

    /** How many were killed within the danger radius of column ({@code x}, {@code z}) in the window before {@code now}. */
    synchronized long near(double x, double z, long now) {
        return areas.near(x, z, now);
    }
}
