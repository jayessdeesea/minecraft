package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardnessTest {

    /** Stone: with a pickaxe of any tier it drops and takes hardness x 30 / speed; by hand x 100 and nothing. */
    @Test
    void aPickaxesBlockIsSlowAndDropsNothingWithoutOne() {
        Hardness stone = new Hardness(1.5, 0);
        assertEquals(Hardness.Tool.PICKAXE, stone.tool());
        assertEquals(150, stone.ticks(Hardness.HAND));
        assertTrue(!stone.drops(Hardness.HAND));
        assertEquals(23, stone.ticks(0), "a wooden pickaxe, speed 2");
        assertEquals(12, stone.ticks(1), "a stone one, speed 4");
    }

    /** A chest: an axe's block (operator, 2026-11-13: the right tool), by hand 75 ticks, with a stone axe a quarter of that; no tier bars its drop. */
    @Test
    void anAxesBlockIsDugByTheAxesSpeedAndDropsForAnyone() {
        Hardness chest = new Hardness(2.5, Hardness.HAND, Hardness.Tool.AXE);
        assertEquals(75, chest.ticks(Hardness.HAND));
        assertEquals(19, chest.ticks(Hardness.HAND, 4), "a stone axe");
        assertEquals(75, chest.ticks(2, 1), "an iron pickaxe is the wrong tool: hand speed");
        assertTrue(chest.drops(Hardness.HAND));
    }

    @Test
    void aBlockDugByHandIsNoFasterWithAnyTool() {
        Hardness glass = new Hardness(0.3, Hardness.HAND, Hardness.Tool.HAND);
        assertEquals(9, glass.ticks(3, 8));
    }

    @Test
    void aTierBelongsToAPickaxesBlockAndOnlyToOne() {
        assertThrows(IllegalArgumentException.class, () -> new Hardness(1, 0, Hardness.Tool.AXE));
        assertThrows(IllegalArgumentException.class, () -> new Hardness(1, Hardness.HAND, Hardness.Tool.PICKAXE));
    }
}
