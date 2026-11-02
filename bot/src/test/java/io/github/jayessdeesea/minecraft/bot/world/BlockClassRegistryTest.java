package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.bot.world.BlockClassRegistry.BlockClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real protocol-777 state IDs from the server's own blocks.json report, not made-up numbers. */
class BlockClassRegistryTest {

    @Test
    void classifiesRealBlockStates() {
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(0));    // air
        assertEquals(BlockClass.STONE, BlockClassRegistry.classOf(1));       // stone: solid, and quarried
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(9));       // grass_block
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(2363)); // short_grass
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(89));     // water, level 0
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(4931));   // fire
        assertEquals(BlockClass.TALL, BlockClassRegistry.classOf(8637));     // oak_fence, dry
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(8635));   // oak_fence, waterlogged
        assertEquals(BlockClass.TALL, BlockClassRegistry.classOf(10382));     // oak_fence_gate, closed
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(10380)); // oak_fence_gate, open
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(268));
        assertEquals(BlockClass.LOG, BlockClassRegistry.classOf(140));    // oak_log, upright     // oak_leaves, dry - really solid
    }

    @Test
    void unknownAndNegativeIdsAreSolid() {
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(-1));
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(10_000_000));
    }
}
