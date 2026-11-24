package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A placeholder on a chunk: its layout (operator, 2026-11-24). */
class PlaceholderChunkTest {

    /** Fenced and gated on the chunk's lines, and lit: no cell of it or its lines is thirteen or more from a torch, where light runs out. */
    @Test
    void itIsFencedGatedAndLitEverywhere() {
        PlaceholderChunk chunk = new PlaceholderChunk(2, 4, 63);
        List<BlockPos> all = new ArrayList<>();
        chunk.fixtures().values().forEach(all::addAll);
        assertEquals(all.size(), new HashSet<>(all).size(), "no cell named twice");
        assertEquals(chunk.grid().fence(), chunk.fixtures().get(ItemClass.FENCE));
        assertEquals(chunk.grid().gates(), chunk.fixtures().get(ItemClass.FENCE_GATE));
        for (int x = 32; x <= 48; x++) {
            for (int z = 64; z <= 80; z++) {
                int fx = x, fz = z;
                int nearest = chunk.torches().stream().mapToInt(t -> Math.abs(t.x() - fx) + Math.abs(t.z() - fz)).min().orElseThrow();
                assertTrue(nearest < 13, "lit at " + x + "," + z + ": " + nearest + " from a torch");
            }
        }
    }
}
