package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.bot.world.BlockClassRegistry.BlockClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real protocol-777 state IDs from the server's own blocks.json report, not made-up numbers. */
class BlockClassRegistryTest {

    @Test
    void classifiesRealBlockStates() {
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(0));    // air
        assertEquals(BlockClass.STONE, BlockClassRegistry.classOf(1));       // stone: solid, and quarried
        assertEquals(BlockClass.SOIL, BlockClassRegistry.classOf(9));        // grass_block: solid, and dug by hand
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(2363)); // short_grass
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(89));     // water, level 0
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(4931));   // fire
        assertEquals(BlockClass.TALL, BlockClassRegistry.classOf(8637));     // oak_fence, dry
        assertEquals(BlockClass.HAZARD, BlockClassRegistry.classOf(8635));   // oak_fence, waterlogged
        assertEquals(BlockClass.TALL, BlockClassRegistry.classOf(10382));     // oak_fence_gate, closed
        assertEquals(BlockClass.PASSABLE, BlockClassRegistry.classOf(10380)); // oak_fence_gate, open
        assertEquals(BlockClass.LEAVES, BlockClassRegistry.classOf(268));     // oak_leaves, dry - solid, broken for saplings
        assertEquals(java.util.Optional.of("oak"), BlockClassRegistry.leaves(268));
        assertEquals(java.util.Optional.of("spruce"), BlockClassRegistry.sapling(32)); // spruce_sapling
        assertEquals(BlockClass.LOG, BlockClassRegistry.classOf(140));        // oak_log, upright
    }

    @Test
    void unknownAndNegativeIdsAreSolid() {
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(-1));
        assertEquals(BlockClass.SOLID, BlockClassRegistry.classOf(10_000_000));
    }

    @Test
    void waterAndWhatGrowsOnlyInItAreSwumButLavaIsNot() {
        assertTrue(BlockClassRegistry.isWater(89));     // water, a source
        assertTrue(BlockClassRegistry.isWater(2370));   // seagrass: stands in water
        assertTrue(BlockClassRegistry.isWater(18419));  // kelp
        assertFalse(BlockClassRegistry.isWater(105));   // lava
    }

    @Test
    void rockCarriesItsHardnessAndThePickaxeItNeeds() {
        assertEquals(new Hardness(1.5, 0), BlockClassRegistry.hardness(1).orElseThrow()); // stone
    }
}
