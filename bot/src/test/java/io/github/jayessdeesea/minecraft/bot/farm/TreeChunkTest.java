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
    private final Set<BlockPos> logs = new HashSet<>();
    /** Columns of ground standing over the grass, six high. */
    private final Set<BlockPos> mounds = new HashSet<>();

    /** Grass at sea level everywhere, with what's been put on it. */
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= GROUND || chests.contains(new BlockPos(x, y, z)) || logs.contains(new BlockPos(x, y, z)) || y <= GROUND + 6 && mounds.contains(new BlockPos(x, 0, z));
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
        public boolean isLog(int x, int y, int z) {
            return logs.contains(new BlockPos(x, y, z));
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

    /**
     * A tree whose foot was cut and the rest left standing is still that cell's tree: live (field708) the tree farm at
     * chunk (2, 3) stood PLANTED, "15 of 16 trunk cells take a sapling", three squads on it with nothing to do - at
     * (34, 64, 54) air, and a birch's trunk from the block over it up. No sapling grows under a trunk; felled, it's replanted.
     */
    @Test
    void aTreeWithItsFootCutIsStillPlanted() {
        TreeChunk grove = new TreeChunk(0, 0, GROUND);
        saplings.addAll(grove.trunks());
        chests.add(grove.chest());
        BlockPos cut = grove.trunks().getFirst();
        saplings.remove(cut);
        for (int up = 1; up <= 5; up++) {
            logs.add(cut.offset(0, up, 0));
        }
        assertTrue(TreeFarm.planted(world, cut), "a trunk over its cell");
        assertEquals(TreeChunk.Stage.LIT, grove.stage(world), "all sixteen planted: its torches next");
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

    /**
     * A chunk flat enough for wheat but with ground over one of its trunk cells isn't a tree chunk: live (field647-648),
     * chunk (5, 5)'s west trunks lay under a rise, every walk to them timed out, and only its east cells were planted.
     */
    @Test
    void aChunkWithATrunkCellUnderGroundIsPassedOver() {
        BlockPos camp = new BlockPos(5, GROUND + 1, 5);
        assertEquals(-1, TreeChunk.site(world, camp, 4, c -> true, Set.of(), Set.of()).orElseThrow().chunkX(), "the first of the nearest: (-1, 0)");
        mounds.add(new BlockPos(-16 + 6, 0, 10)); // one trunk cell of chunk (-1, 0) under a mound
        TreeChunk sited = TreeChunk.site(world, camp, 4, c -> true, Set.of(), Set.of()).orElseThrow();
        assertTrue(sited.chunkX() != -1 || sited.chunkZ() != 0, "not the chunk with a trunk cell under ground, beside one with none: " + sited);
        TreeChunk buried = new TreeChunk(-1, 0, GROUND);
        assertEquals(15, buried.plantable(world).size(), "its plantable trunks: the one under the mound left out");
        for (int t = 0; t < 8; t++) { // half its trunks under ground, everywhere: still a tree chunk, the rest its plot
            for (int[] c : new int[][]{{-1, 0}, {1, 0}, {0, 1}, {0, -1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}}) {
                mounds.add(new BlockPos(c[0] * 16 + TreeChunk.TRUNKS[t % 4], 0, c[1] * 16 + TreeChunk.TRUNKS[t / 4]));
            }
        }
        assertTrue(TreeChunk.site(world, camp, 1, c -> true, Set.of(), Set.of()).isPresent(), "half its trunks take a sapling: enough");
    }
}
