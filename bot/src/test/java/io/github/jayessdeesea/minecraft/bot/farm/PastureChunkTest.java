package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
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

/** An animal farm's template: one layout for sheep, cows, pigs and chickens. */
class PastureChunkTest {

    private static final int GROUND = 63;

    /** Cobblestone under y=63 and where {@code cobbled} says, dirt where {@code soil} says; open over it. */
    private static WorldView world(Set<BlockPos> soil, Set<BlockPos> cobbled) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || soil.contains(new BlockPos(x, y, z)) || cobbled.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return soil.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return y < GROUND || cobbled.contains(new BlockPos(x, y, z));
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
    }

    /** Fenced and gated on the chunk's lines as every farm is, lit inside and on its fence, its feed's chest on its ground. */
    @Test
    void itsLayoutIsTheGridsFenceAndGatesItsLightsAndItsChest() {
        PastureChunk sheep = new PastureChunk(1, 2, GROUND, PastureChunk.Animal.SHEEP);
        ChunkFarm grid = new ChunkFarm(1, 2, GROUND);

        assertEquals(List.of(ItemClass.FENCE, ItemClass.FENCE_GATE, ItemClass.TORCH, ItemClass.CHEST), List.copyOf(sheep.fixtures().keySet()), "in the order they're built");
        assertEquals(grid.fence(), sheep.fixtures().get(ItemClass.FENCE));
        assertEquals(grid.gates(), sheep.fixtures().get(ItemClass.FENCE_GATE), "a gate each side of each corner");
        assertEquals(9 + (grid.fence().size() + ChunkFarm.POSTS_A_TORCH - 1) / ChunkFarm.POSTS_A_TORCH, sheep.torches().size(), "nine inside, one on every fourth post");
        assertTrue(sheep.torches().subList(9, sheep.torches().size()).stream().allMatch(t -> grid.fence().contains(t.offset(0, -1, 0))), "those on posts' tops");
        // Lit so nothing spawns: no cell inside is more than eight blocks, counted along the ground, from a torch inside.
        for (BlockPos c : sheep.ground()) {
            int nearest = sheep.torches().subList(0, 9).stream().mapToInt(t -> Math.abs(t.x() - c.x()) + Math.abs(t.z() - c.z())).min().orElseThrow();
            assertTrue(nearest <= 8, c + " is " + nearest + " from a torch");
        }
        assertEquals(new BlockPos(16 + 13, GROUND + 1, 32 + 13), sheep.chest());
        assertEquals(Optional.of(ItemClass.WHEAT), PastureChunk.Animal.SHEEP.feed());
        assertEquals(Optional.of(ItemClass.SEEDS), PastureChunk.Animal.CHICKEN.feed());
        // The same template whatever is kept in it.
        for (PastureChunk.Animal animal : PastureChunk.Animal.values()) {
            assertEquals(sheep.fixtures(), new PastureChunk(1, 2, GROUND, animal).fixtures(), animal.name());
        }
    }

    /** Dirt at its level but under what stands on it, which is cobblestone: its ground and its footings share its inside out. */
    @Test
    void itsGroundIsDirtAndItsFootingsCobblestone() {
        PastureChunk cows = new PastureChunk(0, 0, GROUND, PastureChunk.Animal.COW);
        Set<BlockPos> inside = new HashSet<>(cows.ground());
        assertTrue(cows.footings().stream().noneMatch(inside::contains), "no footing is ground");
        assertEquals(15 * 15 - 9 - 1, cows.ground().size(), "its inside, less its nine torches' cells and its chest's");
        assertTrue(cows.footings().containsAll(cows.grid().fence().stream().map(p -> p.offset(0, -1, 0)).toList()), "under every post");
        assertTrue(cows.footings().containsAll(cows.grid().gates().stream().map(p -> p.offset(0, -1, 0)).toList()), "and every gate");

        WorldView bare = world(Set.of(), Set.of());
        assertEquals(cows.ground().size(), cows.groundToLay(bare).size(), "on its slab: all of its ground to lay");
        assertEquals(cows.footings().size(), cows.footingsToPave(bare).size(), "and every footing to pave");
        assertEquals(false, cows.grounded(bare));

        // Laid in dirt all over: its ground is right, its footings still to pave - dirt isn't what a fence stands on.
        Set<BlockPos> dirt = new HashSet<>(cows.ground());
        dirt.addAll(cows.footings());
        WorldView dirtAllOver = world(dirt, Set.of());
        assertEquals(List.of(), cows.groundToLay(dirtAllOver));
        assertEquals(cows.footings().size(), cows.footingsToPave(dirtAllOver).size());

        WorldView made = world(new HashSet<>(cows.ground()), new HashSet<>(cows.footings()));
        assertTrue(cows.grounded(made), "dirt in its ground, cobblestone in its footings");
    }
}
