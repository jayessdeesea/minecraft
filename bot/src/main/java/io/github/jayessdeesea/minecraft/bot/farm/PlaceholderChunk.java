package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A placeholder on one chunk of the server's grid (operator, 2026-11-24): ground held for whatever is put there later,
 * and safe to walk meanwhile - cobblestone one under its level and at its level, the chunk farms' fence and gates round
 * it, and torches enough that nothing spawns on it. Another template is laid over it by giving its chunk that mission.
 *
 * <p>Pure: laid out from its chunk and the level of its ground.
 */
public record PlaceholderChunk(int chunkX, int chunkZ, int level) {

    /** Its torches' local cells: its middle and one toward each corner - no cell of its paving more than eight from one. */
    static final int[][] TORCHES = {{8, 8}, {4, 4}, {4, 12}, {12, 4}, {12, 12}};

    /** Its torches, on its paving. */
    public List<BlockPos> torches() {
        List<BlockPos> cells = new ArrayList<>();
        for (int[] t : TORCHES) {
            cells.add(new BlockPos(chunkX * ChunkFarm.SIDE + t[0], level + 1, chunkZ * ChunkFarm.SIDE + t[1]));
        }
        return cells;
    }

    /** Its fence and gates: the chunk farms', on the same lines. */
    public ChunkFarm grid() {
        return new ChunkFarm(chunkX, chunkZ, level);
    }

    /** What's put up on it, and where, in the order it's built: its fence, its gates, its torches. */
    public Map<ItemClass, List<BlockPos>> fixtures() {
        Map<ItemClass, List<BlockPos>> layout = new LinkedHashMap<>();
        layout.put(ItemClass.FENCE, grid().fence());
        layout.put(ItemClass.FENCE_GATE, grid().gates());
        layout.put(ItemClass.TORCH, torches());
        return layout;
    }
}
