package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A mineshaft's head on a chunk: its layout (operator, 2026-11-22). */
class MineHeadTest {

    /**
     * Four torches, four chests as two double ones, the chunk farms' fence - no two fixtures in one cell (in the squad
     * simulation a torch went into a chest's cell, and the head was done a chest short), and the shaft's ground clear.
     */
    @Test
    void itsFixturesEachHaveACellOfTheirOwnAndTheShaftIsClear() {
        MineHead head = new MineHead(2, 4, 63);
        List<BlockPos> all = new ArrayList<>();
        head.fixtures().values().forEach(all::addAll);
        assertEquals(all.size(), new HashSet<>(all).size(), "no cell named twice");
        assertEquals(4, head.torches().size());
        assertEquals(4, head.chests().size());
        Set<BlockPos> line = new HashSet<>(head.grid().fence());
        line.addAll(head.grid().gates());
        assertTrue(head.torches().stream().noneMatch(line::contains) && head.chests().stream().noneMatch(line::contains), "inside the fence");
        BlockPos shaft = head.shaft();
        assertEquals(new BlockPos(40, 64, 72), shaft, "the chunk's middle");
        assertTrue(all.stream().noneMatch(c -> Math.abs(c.x() - shaft.x()) <= 3 && Math.abs(c.z() - shaft.z()) <= 3), "nothing within three of the shaft's head");
    }
}
