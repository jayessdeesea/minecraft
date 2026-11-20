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

    /** Fenced on the chunk's lines and the next ones, a gate beside each corner; holes, chest, field torches and 216 cells inside. */
    @Test
    void aChunkFarmIsLaidOutOnItsChunksLines() {
        ChunkFarm farm = new ChunkFarm(-1, 2, GROUND);

        assertEquals(List.of(new BlockPos(-15, GROUND + 1, 32), new BlockPos(0, GROUND + 1, 33), new BlockPos(-1, GROUND + 1, 48),
                new BlockPos(-16, GROUND + 1, 47)), farm.gates(), "beside each corner, going round");
        assertEquals(17 * 4 - 4 - 4, farm.fence().size(), "the ring of 17 a side, less its gates");
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
}
