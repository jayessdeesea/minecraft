package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockpileWatchTest {

    @TempDir
    Path state;

    private static final BlockPos CHEST = new BlockPos(-48, 75, 51);

    @Test
    void twoSoldiersWhoCantReachTheStockpileMoveItWhereTheyStand() {
        StockpileWatch watch = new StockpileWatch(new WallStore(state, "chests"));

        assertTrue(watch.unreached("Pete", new Position(-33.5, 66, 54.5), CHEST, 0).isEmpty(), "one soldier once: not yet");
        assertEquals(Optional.of(new BlockPos(-34, 66, 54)), watch.unreached("Hal", new Position(-40.5, 68, 57.5), CHEST, 60_000),
                "a second: the stockpile moves to where they stand");
        assertEquals(Set.of(CHEST), watch.givenUp(), "the old chest given up");
        assertTrue(watch.unreached("Ivy", new Position(0, 63, 0), new BlockPos(-33, 66, 55), 120_000).isEmpty(), "the new site settles first");

        StockpileWatch restarted = new StockpileWatch(new WallStore(state, "chests"));
        assertEquals(Optional.of(new BlockPos(-34, 66, 54)), restarted.ground(), "kept on disk");
        assertEquals(Set.of(CHEST), restarted.givenUp());
    }

    @Test
    void oneSoldierReportingItForMinutesIsEnoughAndAnOldReportLapses() {
        StockpileWatch watch = new StockpileWatch(new WallStore(state, "chests"));

        watch.unreached("Pete", new Position(-33.5, 66, 54.5), CHEST, 0);
        assertTrue(watch.unreached("Hal", new Position(-40.5, 68, 57.5), CHEST, StockpileWatch.WINDOW_MILLIS + 1).isEmpty(), "Pete's lapsed");
        assertTrue(watch.unreached("Hal", new Position(-40.5, 68, 57.5), CHEST, StockpileWatch.WINDOW_MILLIS + StockpileWatch.ONE_MILLIS + 1)
                .isPresent(), "Hal three minutes on");
    }
}
