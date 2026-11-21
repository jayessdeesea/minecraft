package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.command.DangerAreas;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tunnel running into a danger area is given up, though the mine's head is far from it: live (field598), four of
 * Charlie killed by skeletons in a cave 112 blocks down a gallery of the keep's mine.
 */
class MineDangerTest {

    @Test
    void aTunnelIntoADangerAreaIsGivenUp() {
        Staircase mine = new Staircase(new BlockPos(0, 64, 0), 40, 6);
        // Three deaths at the far end of the main tunnel, nowhere near the head.
        BlockPos end = mine.tunnelEnd();
        DangerAreas areas = new DangerAreas();
        for (int i = 0; i < DangerAreas.DEATHS; i++) {
            areas.killed(new io.github.jayessdeesea.minecraft.command.Position(end.x() + 0.5, end.y(), end.z() + 0.5), 1000L * i);
        }
        assertTrue(areas.near(0.5, 0.5, 10_000) < DangerAreas.DEATHS, "the head reads safe");

        Map<Integer, BlockPos> into = MineDanger.tunnels(mine.cuts(), c -> areas.near(c.x() + 0.5, c.y(), c.z() + 0.5, 10_000));

        assertTrue(into.containsKey(0), "the main tunnel given up: " + into);
        assertEquals(into.keySet().stream().filter(t -> t < 0).count(), 0L, "never the stairs");
    }

    /** Deaths at another level over the tunnel - the surface by the mine's head - give it up no more: live (field599). */
    @Test
    void deathsAtAnotherLevelDontGiveATunnelUp() {
        Staircase mine = new Staircase(new BlockPos(0, 64, 0), 40, 6);
        BlockPos end = mine.tunnelEnd();
        DangerAreas areas = new DangerAreas();
        for (int i = 0; i < DangerAreas.DEATHS; i++) {
            areas.killed(new io.github.jayessdeesea.minecraft.command.Position(end.x() + 0.5, 64, end.z() + 0.5), 1000L * i);
        }

        assertTrue(MineDanger.tunnels(mine.cuts(), c -> areas.near(c.x() + 0.5, c.y(), c.z() + 0.5, 10_000)).isEmpty(),
                "deaths 24 over the tunnel, on the surface");
    }
}
