package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
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
     * No two fixtures in one cell; its chunk's fence round it; two rows of four double chests, a kind to each; its mouth
     * ringed one out from the well with one gate, beside the cell the stair leaves from, and a torch on each corner post.
     */
    @Test
    void itsFixturesEachHaveACellOfTheirOwnAndItsMouthIsRinged() {
        MineHead head = new MineHead(2, 4, 63);
        List<BlockPos> all = new ArrayList<>();
        head.fixtures().values().forEach(all::addAll);
        assertEquals(all.size(), new HashSet<>(all).size(), "no cell named twice");
        assertEquals(8, head.torches().size(), "four corners, four ring posts");
        assertEquals(16, head.chests().size(), "eight double chests");
        Set<BlockPos> line = new HashSet<>(head.grid().fence());
        line.addAll(head.grid().gates());
        assertTrue(all.stream().filter(c -> !line.contains(c)).allMatch(c -> c.x() > 32 && c.x() < 48 && c.z() > 64 && c.z() < 80), "inside the fence");
        BlockPos shaft = head.shaft();
        assertEquals(new BlockPos(40, 64, 72), shaft, "the chunk's middle");
        assertTrue(all.stream().noneMatch(c -> c.x() >= 38 && c.x() <= 41 && c.z() >= 70 && c.z() <= 73), "nothing in the well's four by four");
        assertEquals(19, head.ring().size(), "a six by six square's twenty, less its gate");
        BlockPos stairHead = Staircase.wellCell(2, 4, 64), gate = head.mouthGate();
        assertEquals(1, Math.abs(gate.x() - stairHead.x()) + Math.abs(gate.z() - stairHead.z()), "its gate beside the stair's head: " + gate + ", " + stairHead);
        assertTrue(head.torches().stream().filter(t -> t.y() == 65).allMatch(t -> head.ring().contains(t.offset(0, -1, 0))), "a ring torch on a ring post");
    }

    /** Each kind has its chest, and what has none goes to the kit's: the rows are north then south, west to east. */
    @Test
    void aKindIsKeptInItsOwnChest() {
        MineHead head = new MineHead(2, 4, 63);
        assertEquals(MineHead.Bank.RAW_ORE, MineHead.Bank.of(ItemClass.RAW_COPPER));
        assertEquals(MineHead.Bank.SPOIL, MineHead.Bank.of(ItemClass.COBBLESTONE));
        assertEquals(MineHead.Bank.KIT, MineHead.Bank.of(ItemClass.WOOL));
        assertEquals(List.of(new BlockPos(36, 64, 67), new BlockPos(37, 64, 67)), head.chests(MineHead.Bank.FUEL), "the north row's first");
        assertEquals(List.of(new BlockPos(42, 64, 76), new BlockPos(43, 64, 76)), head.chests(MineHead.Bank.KIT), "the south row's last");
    }
}
