package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The camp's grove is a tree chunk within 64 blocks, beside its wheat (operator, 2026-11-21; field630's 200-block walks). */
class CampTreesTest {

    private static final int GROUND = ChunkFarm.SEA_LEVEL;

    private final WorldView grass = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= GROUND;
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return y == GROUND;
        }

        @Override
        public boolean isTillable(int x, int y, int z) {
            return y == GROUND;
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    @Test
    void theCampsGroveIsATreeChunkCloseByBesideItsWheat() {
        BlockPos camp = new BlockPos(24, GROUND + 1, 88);
        ChunkFarm wheat = new ChunkFarm(2, 5, GROUND);
        FarmPlan farm = new FarmPlan(wheat.holes().getFirst(), wheat.ground(), List.of(), List.of(wheat));
        TreeFarm grove = new CampTrees().grove(grass, camp, farm, 0).orElseThrow();
        assertTrue(grove.plot().size() == 16, "its 16 trunks");
        assertTrue(grove.plot().stream().allMatch(c -> Math.hypot(c.x() - camp.x(), c.z() - camp.z()) <= 64 + 16),
                "within 64 blocks of the camp, not 200: " + grove.plot().getFirst());
        int cx = Math.floorDiv(grove.plot().getFirst().x(), 16), cz = Math.floorDiv(grove.plot().getFirst().z(), 16);
        assertTrue(Math.abs(cx - 2) + Math.abs(cz - 5) == 1, "beside the wheat chunk (2, 5): chunk (" + cx + ", " + cz + ")");
    }
}
