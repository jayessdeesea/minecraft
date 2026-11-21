package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A grove on a chunk: its layout, its stage from the world, and where it's sited (operator, 2026-11-21). */
class TreeChunkTest {

    private static final int GROUND = ChunkFarm.SEA_LEVEL;

    private final Set<BlockPos> saplings = new HashSet<>();
    private final Set<BlockPos> torches = new HashSet<>();
    private final Set<BlockPos> chests = new HashSet<>();

    /** Grass at sea level everywhere, with what's been put on it. */
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= GROUND || chests.contains(new BlockPos(x, y, z));
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
        public Optional<String> sapling(int x, int y, int z) {
            return saplings.contains(new BlockPos(x, y, z)) ? Optional.of("birch") : Optional.empty();
        }

        @Override
        public boolean isTorch(int x, int y, int z) {
            return torches.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isChest(int x, int y, int z) {
            return chests.contains(new BlockPos(x, y, z));
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
    void sixteenBirchNineTorchesAChestAndTheChunkFarmsFence() {
        TreeChunk grove = new TreeChunk(1, -1, GROUND);
        assertEquals(16, grove.trunks().size());
        assertTrue(grove.trunks().stream().allMatch(c -> TreeFarm.onGrid(c.x(), c.z())), "on the tree farm's world grid");
        assertEquals(9, grove.torches().size());
        assertTrue(grove.torches().stream().allMatch(t -> grove.trunks().stream()
                .anyMatch(c -> Math.abs(c.x() - t.x()) + Math.abs(c.z() - t.z()) <= 4)), "a torch within 4 of a trunk: light 10 there");
        assertEquals(new BlockPos(17, GROUND + 1, -15), grove.chest());
        assertEquals(new ChunkFarm(1, -1, GROUND).gates(), grove.grid().gates(), "one grid with the wheat");
    }

    @Test
    void itsStageIsTheFirstWorkLeftWoodFromTheSecond() {
        TreeChunk grove = new TreeChunk(0, 0, GROUND);
        assertEquals(TreeChunk.Stage.PLANTED, grove.stage(world), "grass ground: ready to plant");
        saplings.addAll(grove.trunks());
        chests.add(grove.chest());
        assertEquals(TreeChunk.Stage.LIT, grove.stage(world), "planted - growing wood - its torches next");
        torches.addAll(grove.torches());
        assertEquals(TreeChunk.Stage.PRODUCING, grove.stage(world), "lit: producing, the fence when wood isn't short");
    }

    @Test
    void itsSitedBesideAWheatChunkWithin64Blocks() {
        BlockPos camp = new BlockPos(5, GROUND + 1, 5);
        Set<Long> wheat = Set.of(ChunkFarm.key(3, 0));
        TreeChunk grove = TreeChunk.site(world, camp, 4, c -> true, wheat, wheat).orElseThrow();
        assertTrue(Math.abs(grove.chunkX() - 3) + Math.abs(grove.chunkZ()) == 1, "beside the wheat chunk at (3, 0): " + grove);
        TreeChunk alone = TreeChunk.site(world, camp, 4, c -> true, Set.of(), Set.of()).orElseThrow();
        assertEquals(1, Math.abs(alone.chunkX()) + Math.abs(alone.chunkZ()), "no wheat yet: the nearest, beside the camp's: " + alone);
    }
}
