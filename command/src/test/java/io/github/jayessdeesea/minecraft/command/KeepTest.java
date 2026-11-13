package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeepTest {

    /** Level by level: no soldier digs more than the window under the keep's current layer, and never under the slab. */
    @Test
    void aSoldierDigsNoLowerThanTheWindowUnderTheLayerNorUnderTheSlab() {
        assertEquals(93 - Keep.LAYER_WINDOW, Keep.floor(93));
        assertEquals(Keep.BOTTOM, Keep.floor(Keep.BOTTOM + 1), "the slab's bottom is the floor of the dig");
    }
}
