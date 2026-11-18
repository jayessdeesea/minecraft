package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.DangerAreas;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DangerAreaBookTest {

    @TempDir
    Path state;

    /** Deaths by a mine's stairs before a restart still count after it: live, restarts every twenty minutes hid eight. */
    @Test
    void aRestartRemembersWhereSoldiersWereKilled() {
        DangerAreaBook before = new DangerAreaBook(new WallStore(state, "chests"));
        before.killed(new Position(55.5, 5, -5.5), 0);
        before.killed(new Position(54.4, 26, -9.6), 60 * 60_000L);

        DangerAreaBook after = new DangerAreaBook(new WallStore(state, "chests"));
        after.killed(new Position(57.5, -38, -9.5), 2 * 60 * 60_000L);

        assertEquals(DangerAreas.DEATHS, after.near(55, -9, 2 * 60 * 60_000L), "all three, across the restart");
    }
}
