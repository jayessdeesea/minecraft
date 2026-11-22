package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camp's farm sites no chunk farm of its own: it takes in the one a farm mission gives it (operator, 2026-11-21: a farm at
 * a chunk is the operator's to order and the platoon leader's to carry out), and plants its ground as it's laid.
 */
class CampFarmTest {

    private static final int GRASS = 9, STONE = 1, WATER = 89, AIR = 0;
    private static final BlockPos CAMP = new BlockPos(8, 64, 8);

    @TempDir
    Path dir;

    /** Flat grass at y=63 for chunks round the camp, a pond by the camp, and {@code open} cells dug out over a floor. */
    private static WorldView ground(Set<BlockPos> open) {
        return RecordedGroundEfficiencyTest.world(-48, -48, 112, 112, (x, z) -> 0, (x, yz) -> {
            int y = yz >> 16, z = (short) (yz & 0xFFFF);
            if (open.contains(new BlockPos(x, y, z))) {
                return AIR;
            }
            boolean pond = Math.abs(x - 8) <= 1 && Math.abs(z - 12) <= 1;
            return y < 63 ? STONE : y == 63 ? (pond ? WATER : GRASS) : AIR;
        });
    }

    @Test
    void aChunkFarmIsTakenInOnlyWhenAMissionGivesIt() {
        CampFarm farms = new CampFarm(new WallStore(dir, "farm"), 20_000);
        FarmPlan first = farms.kept(ground(Set.of()), CAMP, "8,8", 4);
        assertNotNull(first, "a farm by the pond");
        assertEquals(0, first.chunks().size(), "no chunk farm sited by itself: " + first.chunks());

        ChunkFarm mission = new ChunkFarm(2, 0, 63);
        farms.take(mission);
        FarmPlan taken = farms.kept(ground(Set.of()), CAMP, "8,8", 4);
        assertEquals(java.util.List.of(mission), taken.chunks(), "the mission's taken in");
        assertEquals(1, taken.chunks().stream().map(c -> ChunkFarm.key(c.chunkX(), c.chunkZ())).collect(Collectors.toSet()).size());
        assertTrue(taken.plot().containsAll(mission.ground().stream().filter(g -> !g.equals(mission.ground().getFirst())).limit(10).toList()),
                "its ground, grass at its level, in the plot");

        // Its ground dug out - floored a block below - is out of the plot as it stands, and back in once laid.
        Set<BlockPos> open = Set.copyOf(mission.ground());
        CampFarm afresh = new CampFarm(new WallStore(dir, "afresh"), 20_000);
        afresh.take(mission);
        FarmPlan floored = afresh.kept(ground(open), CAMP, "8,8", 4);
        assertTrue(floored.plot().stream().noneMatch(open::contains), "nothing to plant on its floor yet");
        assertTrue(afresh.kept(ground(Set.of()), CAMP, "8,8", 4).plot().containsAll(mission.ground()), "its dirt laid, planted");
    }
}
