package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An animal farm on a chunk (operator, 2026-11-24): one template for sheep, cows, pigs and chickens - only what's kept in
 * it differs. Its ground as every template's: a cobblestone slab one under its level; at its level dirt, for grass, but
 * cobblestone under its fence, gates, torches and chest. Fenced on the chunk's lines with a gate each side of each
 * corner, the same grid as a wheat or tree farm's ({@link ChunkFarm}), and lit so nothing spawns in it or by it: a torch
 * on every fourth post and nine inside. A chest for its feed. Pure over a {@link WorldView}.
 */
public record PastureChunk(int chunkX, int chunkZ, int level, Animal animal) {

    /** What's kept in it, and what it's fed and bred with, where the force carries that as a kind of its own. */
    public enum Animal {
        SHEEP(Optional.of(ItemClass.WHEAT)), COW(Optional.of(ItemClass.WHEAT)), PIG(Optional.empty()), CHICKEN(Optional.of(ItemClass.SEEDS));

        private final Optional<ItemClass> feed;

        Animal(Optional<ItemClass> feed) {
            this.feed = feed;
        }

        /** What it follows and breeds on - none for a pig, whose carrots, potatoes and beetroot the force has no kind for yet. */
        public Optional<ItemClass> feed() {
            return feed;
        }
    }

    /** Where its inside torches stand, by local x and z: three rows of three, none further than eight blocks from one. */
    private static final int[] TORCHES = {4, 8, 12};
    /** Its chest's local x and z, as a chunk farm's. */
    private static final int CHEST = 13;

    /** The fence and gates it shares with the chunks beside it. */
    public ChunkFarm grid() {
        return new ChunkFarm(chunkX, chunkZ, level);
    }

    private int x0() {
        return chunkX * ChunkFarm.SIDE;
    }

    private int z0() {
        return chunkZ * ChunkFarm.SIDE;
    }

    /** Where its feed's chest stands, on its ground. */
    public BlockPos chest() {
        return new BlockPos(x0() + CHEST, level + 1, z0() + CHEST);
    }

    /** Its torches: the nine inside, on its ground, and one on every {@value ChunkFarm#POSTS_A_TORCH}th post's top. */
    public List<BlockPos> torches() {
        List<BlockPos> torches = new ArrayList<>();
        for (int x : TORCHES) {
            for (int z : TORCHES) {
                torches.add(new BlockPos(x0() + x, level + 1, z0() + z));
            }
        }
        List<BlockPos> posts = grid().fence();
        for (int i = 0; i < posts.size(); i += ChunkFarm.POSTS_A_TORCH) {
            torches.add(posts.get(i).offset(0, 1, 0));
        }
        return torches;
    }

    /** What its fixtures stand on, at its level: under its fence, gates, inside torches and chest - cobblestone. */
    public List<BlockPos> footings() {
        List<BlockPos> cells = new ArrayList<>();
        grid().fence().forEach(p -> cells.add(p.offset(0, -1, 0)));
        grid().gates().forEach(g -> cells.add(g.offset(0, -1, 0)));
        for (int x : TORCHES) {
            for (int z : TORCHES) {
                cells.add(new BlockPos(x0() + x, level, z0() + z));
            }
        }
        cells.add(chest().offset(0, -1, 0));
        return cells;
    }

    /** Its ground inside the fence at its level, but its footings: dirt, for grass to take. */
    public List<BlockPos> ground() {
        List<BlockPos> footings = footings();
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = 1; dx < ChunkFarm.SIDE; dx++) {
            for (int dz = 1; dz < ChunkFarm.SIDE; dz++) {
                BlockPos c = new BlockPos(x0() + dx, level, z0() + dz);
                if (!footings.contains(c)) {
                    cells.add(c);
                }
            }
        }
        return cells;
    }

    /** Its ground still to lay in dirt: a cell open, or under water. */
    public List<BlockPos> groundToLay(WorldView world) {
        return ground().stream().filter(c -> !world.isSolid(c.x(), c.y(), c.z()) || world.isWater(c.x(), c.y(), c.z())).toList();
    }

    /** Its footings still to pave in cobblestone: open, under water, or of dirt or grass. Rock found there is left. */
    public List<BlockPos> footingsToPave(WorldView world) {
        return footings().stream().filter(c -> !world.isCobblestone(c.x(), c.y(), c.z()) && (!world.isSolid(c.x(), c.y(), c.z())
                || world.isWater(c.x(), c.y(), c.z()) || world.isSoil(c.x(), c.y(), c.z()) || world.isFarmland(c.x(), c.y(), c.z()))).toList();
    }

    /** What it's put up with, each kind in its cells, in the order they're built: the fence, its gates, its lights, its chest. */
    public Map<ItemClass, List<BlockPos>> fixtures() {
        Map<ItemClass, List<BlockPos>> layout = new LinkedHashMap<>();
        layout.put(ItemClass.FENCE, grid().fence());
        layout.put(ItemClass.FENCE_GATE, grid().gates());
        layout.put(ItemClass.TORCH, torches());
        layout.put(ItemClass.CHEST, List.of(chest()));
        return layout;
    }

    /** Whether its ground is laid and paved: nothing left of either. */
    public boolean grounded(WorldView world) {
        return groundToLay(world).isEmpty() && footingsToPave(world).isEmpty();
    }
}
