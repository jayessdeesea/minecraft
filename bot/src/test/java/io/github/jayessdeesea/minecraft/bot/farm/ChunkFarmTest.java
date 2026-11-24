package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkFarmTest {

    private static final int GROUND = 63;

    /** Grass ground at y=63, flat everywhere but stone over the chunks in {@code stony}. */
    private static WorldView flat(Set<Long> stony) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isTillable(int x, int y, int z) {
                return y == GROUND && !stony.contains(ChunkFarm.key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    /** Fenced on the chunk's lines and the next ones, a gate each side of each corner; holes, chest, field torches and 216 cells inside. */
    @Test
    void aChunkFarmIsLaidOutOnItsChunksLines() {
        ChunkFarm farm = new ChunkFarm(-1, 2, GROUND);

        assertEquals(List.of(new BlockPos(-15, GROUND + 1, 32), new BlockPos(-1, GROUND + 1, 32), new BlockPos(0, GROUND + 1, 33),
                new BlockPos(0, GROUND + 1, 47), new BlockPos(-1, GROUND + 1, 48), new BlockPos(-15, GROUND + 1, 48),
                new BlockPos(-16, GROUND + 1, 47), new BlockPos(-16, GROUND + 1, 33)), farm.gates(), "each side of each corner, going round");
        assertEquals(17 * 4 - 4 - 8, farm.fence().size(), "the ring of 17 a side, less its gates");
        for (int[] corner : new int[][]{{-16, 32}, {0, 32}, {0, 48}, {-16, 48}}) {
            assertTrue(farm.fence().contains(new BlockPos(corner[0], GROUND + 1, corner[1])), "each corner a post");
        }
        assertTrue(farm.fence().stream().allMatch(c -> Math.floorMod(c.x(), 16) == 0 || Math.floorMod(c.z(), 16) == 0),
                "every post on a chunk line");
        assertEquals(List.of(new BlockPos(-12, GROUND, 36), new BlockPos(-12, GROUND, 44), new BlockPos(-4, GROUND, 36), new BlockPos(-4, GROUND, 44)),
                farm.holes(), "in the farmland's own layer");
        assertEquals(new BlockPos(-3, GROUND + 1, 45), farm.chest());
        assertEquals(15 * 15 - 4 - 1 - 4, farm.ground().size());
        assertTrue(farm.ground().stream().allMatch(c -> farm.holes().stream()
                        .anyMatch(h -> Math.abs(h.x() - c.x()) <= FarmPlan.HYDRATION && Math.abs(h.z() - c.z()) <= FarmPlan.HYDRATION)),
                "every cell within a hole's hydration");
    }

    /** Sited beside the camp's chunk, never in it, on the ground that farms most; its plan the holes to fill. */
    @Test
    void aChunkFarmIsSitedBesideTheCampOnTheGroundThatFarmsMost() {
        WorldView world = flat(Set.of(ChunkFarm.key(-1, 0), ChunkFarm.key(1, 0), ChunkFarm.key(0, -1)));
        ChunkFarm farm = ChunkFarm.site(world, new BlockPos(5, GROUND + 1, 5), 1, c -> true, Set.of()).orElseThrow();

        assertEquals(new ChunkFarm(0, 1, GROUND), farm, "the one open side, the nearest");
        FarmPlan plan = farm.plan(world, c -> true);
        assertEquals(216, plan.plot().size(), "the 225 inside its fence but its four holes, its chest's cell and its four field torches'");
        assertEquals(farm.holes(), plan.holes());
        assertEquals(new ChunkFarm(-1, -1, GROUND), ChunkFarm.site(world, new BlockPos(5, GROUND + 1, 5), 1, c -> true,
                Set.of(ChunkFarm.key(0, 1))).orElseThrow(), "that taken, a corner");
    }

    /** Of the chunks flat enough, the one at sea level before a nearer one higher up (operator, 2026-11-21). */
    @Test
    void ofTheFlatChunksTheOneAtSeaLevelIsSitedFirst() {
        // Chunk (1, 0) flat at sea level + 6, chunk (2, 0) flat at sea level; everything else broken ground.
        WorldView world = new WorldView() {
            int top(int x, int z) {
                int chunkX = Math.floorDiv(x, 16), chunkZ = Math.floorDiv(z, 16);
                return chunkZ != 0 ? Integer.MIN_VALUE : chunkX == 1 ? ChunkFarm.SEA_LEVEL + 6 : chunkX == 2 ? ChunkFarm.SEA_LEVEL
                        : ChunkFarm.SEA_LEVEL + Math.floorMod(x * 7 + z * 13, 9) - 4;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= top(x, z);
            }

            @Override
            public boolean isTillable(int x, int y, int z) {
                return y == top(x, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position c, double r) {
                return List.of();
            }
        };
        ChunkFarm farm = ChunkFarm.site(world, new BlockPos(5, ChunkFarm.SEA_LEVEL + 1, 5), 2, c -> true, Set.of()).orElseThrow();
        assertEquals(new ChunkFarm(2, 0, ChunkFarm.SEA_LEVEL), farm, "sea level, a chunk farther, before the nearer one 6 up");
    }

    /** Ground too broken for a farm - a walk to only some of it - none sited, but the best is said, with what it makes. */
    @Test
    void groundTooBrokenForAFarmHasNoneSitedButTheBestSaid() {
        WorldView world = flat(Set.of());
        java.util.function.Predicate<BlockPos> half = c -> Math.floorMod(c.x(), 16) < 8;

        assertTrue(ChunkFarm.site(world, new BlockPos(5, GROUND + 1, 5), 1, half, Set.of()).isEmpty(), "none of 160 cells walkable to");
        ChunkFarm best = ChunkFarm.best(world, new BlockPos(5, GROUND + 1, 5), 1, half, Set.of()).orElseThrow();
        assertTrue(best.plan(world, half).plot().size() < ChunkFarm.MIN_CELLS, "the best, under a farm's: " + best.plan(world, half).plot().size());
    }

    /** Its torches: four in the field a step in on the diagonal from each hole, its cells untilled; and one on every fourth post's top. */
    @Test
    void itsTorchesStandInTheFieldOnTheDiagonals() {
        ChunkFarm farm = new ChunkFarm(0, 0, GROUND);
        List<BlockPos> torches = farm.torches();

        assertTrue(torches.containsAll(List.of(new BlockPos(5, GROUND + 1, 5), new BlockPos(5, GROUND + 1, 11),
                new BlockPos(11, GROUND + 1, 5), new BlockPos(11, GROUND + 1, 11))), "the field's four: " + torches);
        assertTrue(farm.ground().stream().noneMatch(c -> c.equals(new BlockPos(5, GROUND, 5))), "under a field torch: untilled");
        List<BlockPos> posts = farm.fence();
        assertEquals(4 + (posts.size() + ChunkFarm.POSTS_A_TORCH - 1) / ChunkFarm.POSTS_A_TORCH, torches.size());
        assertTrue(torches.subList(4, torches.size()).stream().allMatch(t -> posts.contains(t.offset(0, -1, 0))), "on posts' tops: " + torches);
    }

    /**
     * What its fixtures stand on is cobblestone, and what stands where they go is dug out (operator, 2026-11-24: blocks
     * on fences, missing torches by the water, a lot of fencing gone - the area under the fence should be cobblestone,
     * and where the torch stands). Live (field712): the fence line and the torches' cells were dirt, tilled and sown;
     * dirt, grass and a chest stood in posts' cells, cobblestone and a furnace on posts, cobblestone in a gate's cell.
     */
    @Test
    void itsFootingsArePavedAndWhatStandsInItsFixturesPlaceIsInTheWay() {
        ChunkFarm farm = new ChunkFarm(1, 3, GROUND);
        BlockPos post = farm.fence().get(5), gate = farm.gates().getFirst(), topped = farm.fence().get(9);
        BlockPos torch = farm.torches().getFirst();
        BlockPos tilled = farm.fence().get(12).offset(0, -1, 0), paved = farm.fence().get(20).offset(0, -1, 0), open = farm.fence().get(30).offset(0, -1, 0);
        Set<BlockPos> blocks = Set.of(post, gate, topped, topped.offset(0, 1, 0), torch);
        Set<BlockPos> soil = Set.of(post);
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return y <= GROUND && !c.equals(open) || blocks.contains(c);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return y == GROUND && !c.equals(tilled) && !c.equals(paved) || soil.contains(c);
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(tilled);
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return c.equals(paved) || c.equals(gate) || c.equals(topped.offset(0, 1, 0)) || c.equals(torch);
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

        List<BlockPos> toPave = farm.footingsToPave(world);
        assertEquals(farm.footings().size() - 1, toPave.size(), "every footing of dirt, the tilled and the open among them - not the one in cobblestone");
        assertTrue(toPave.contains(tilled) && toPave.contains(open) && !toPave.contains(paved), toPave.toString());
        assertEquals(Set.of(post, gate, topped.offset(0, 1, 0), torch), Set.copyOf(farm.obstructions(world)),
                "dirt in a post's cell, cobblestone in a gate's, a block on a post, a block where a field torch goes - not the post under it");

        FarmSite.Work work = FarmSite.work(world, farm);
        assertEquals(FarmSite.Stage.SURVEYED, work.stage(), "its ground isn't right till they're paved and dug: " + work);
        assertEquals(toPave, FarmSite.surveyed(world, farm).get(2), "the survey's third list");
    }

    /** A plot never holds a footing of the farm's chunk farms: nothing a fence, a gate or a torch stands on is tilled or sown. */
    @Test
    void aFarmsPlotNeverTakesInAChunkFarmsFootings() {
        ChunkFarm farm = new ChunkFarm(1, 3, GROUND);
        BlockPos footing = farm.footings().getFirst(), ground = farm.ground().getFirst();

        FarmPlan plan = new FarmPlan(farm.holes().getFirst(), List.of(footing, ground), List.of(), List.of(farm));

        assertEquals(List.of(ground), plan.plot());
    }
}
