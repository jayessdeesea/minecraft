package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.bot.world.BlockClassRegistry.BlockClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real protocol-776 state IDs from the server's own blocks.json report, not made-up numbers. */
class BlockClassRegistryTest {

    @Test
    void classifiesRealBlockStates() {
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(0));    // air
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(1));       // stone
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(9));       // grass_block
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(2248)); // short_grass
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(86));     // water, level 0
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(3375));   // fire
        assertEquals(BlockClass.TALL, BlockClassRegistry.classOf(6967));     // oak_fence, dry
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(6965));   // oak_fence, waterlogged
        assertEquals(BlockClass.TALL, BlockClassRegistry.classOf(8648));     // oak_fence_gate, closed
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(8646)); // oak_fence_gate, open
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(253));     // oak_leaves, dry - really solid
    }

    @Test
    void unknownAndNegativeIdsAreSolid() {
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(-1));
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(10_000_000));
    }
}
