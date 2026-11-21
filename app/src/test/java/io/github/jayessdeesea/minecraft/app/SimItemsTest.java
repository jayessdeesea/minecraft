package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimItemsTest {

    /** Stone dug at once is refused; dug for its time with a stone pickaxe in hand, taken; by hand in that time, refused. */
    @Test
    void aDigIsTimedByTheToolInHand() {
        assertTrue(SimItems.dugTooFast(SimServer.STONE, SimServer.ITEM_STONE_PICKAXE, 0L, 0), "at once");
        assertTrue(SimItems.dugTooFast(SimServer.STONE, SimServer.ITEM_STONE_PICKAXE, null, 10_000), "never begun");
        assertFalse(SimItems.dugTooFast(SimServer.STONE, SimServer.ITEM_STONE_PICKAXE, 0L, 1_000), "a stone pickaxe's second");
        assertTrue(SimItems.dugTooFast(SimServer.STONE, -1, 0L, 1_000), "by hand, a second isn't enough");
        assertFalse(SimItems.dugTooFast(SimServer.WHEAT_RIPE, -1, 0L, 0), "wheat breaks at a touch");
    }
}
