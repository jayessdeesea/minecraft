package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mineshaft's head laid out on one chunk of the server's grid (operator, 2026-11-22: the mineshaft template - fenced
 * as the farms are, lit, a bank of chests at its head for what's worth keeping and won't fit a pack): the chunk farms'
 * fence and gates round it, a torch inside each corner, four chests - two double ones - along its south side, and its
 * middle left clear: the shaft's head is the chunk's centre, where its squad cuts down.
 *
 * <p>Pure: laid out from its chunk and the level of its ground.
 */
public record MineHead(int chunkX, int chunkZ, int level) {

    /** The torches' local x and z: inside each corner. */
    static final int[] TORCH = {2, 13};
    /** The chests' local x, on local z = {@link #CHEST_Z}: two double chests, a cell between them. */
    static final int[] CHEST_X = {5, 6, 9, 10};
    static final int CHEST_Z = 13;

    private int x0() {
        return chunkX * ChunkFarm.SIDE;
    }

    private int z0() {
        return chunkZ * ChunkFarm.SIDE;
    }

    /** Where its shaft goes down: the chunk's middle. */
    public BlockPos shaft() {
        return new BlockPos(x0() + 8, level + 1, z0() + 8);
    }

    /** Its four torches, on the ground inside each corner. */
    public List<BlockPos> torches() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : TORCH) {
            for (int z : TORCH) {
                cells.add(new BlockPos(x0() + x, level + 1, z0() + z));
            }
        }
        return cells;
    }

    /** Its head's chests: for ore, ingots, minerals and tools - never dirt or cobblestone. */
    public List<BlockPos> chests() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : CHEST_X) {
            cells.add(new BlockPos(x0() + x, level + 1, z0() + CHEST_Z));
        }
        return cells;
    }

    /** Its fence and gates: the chunk farms', on the same lines. */
    public ChunkFarm grid() {
        return new ChunkFarm(chunkX, chunkZ, level);
    }

    /** What's put up at its head, and where, in the order it's built: its torches, its fence and gates, its chests. */
    public Map<ItemClass, List<BlockPos>> fixtures() {
        Map<ItemClass, List<BlockPos>> layout = new LinkedHashMap<>();
        layout.put(ItemClass.TORCH, torches());
        layout.put(ItemClass.FENCE, grid().fence());
        layout.put(ItemClass.FENCE_GATE, grid().gates());
        layout.put(ItemClass.CHEST, chests());
        return layout;
    }
}
