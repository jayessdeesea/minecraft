package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DangerAreasTest {

    /** Three killed by one staircase within a day make it a danger area; ones far off, or long ago, don't count. */
    @Test
    void threeKilledNearOnePlaceInADayMakeItADangerArea() {
        DangerAreas areas = new DangerAreas();
        areas.killed(new Position(54.4, 26, -9.6), 0);
        areas.killed(new Position(58.6, 24, -10.5), 60_000);
        areas.killed(new Position(-85, -56, -22), 90_000);
        assertFalse(areas.dangerous(55, -9, 100_000), "two near, one far: not yet");
        areas.killed(new Position(54.3, 26, -9.3), 120_000);
        assertTrue(areas.dangerous(55, -9, 130_000), "three near the stairs");
        assertFalse(areas.dangerous(55, -9, 120_000 + DangerAreas.WINDOW_MILLIS + 1), "a day on, forgotten");
    }
}
