package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirmed real state IDs (protocol 776's own {@code --reports} output - see
 * {@code tools/block-solidity-generator}), not made-up numbers: 0 = {@code minecraft:air}, 1 =
 * {@code minecraft:stone}, 9 = {@code minecraft:grass_block[snowy=false]}, 2248 = {@code
 * minecraft:short_grass} - the exact block confirmed live under a Soldier's feet that motivated
 * this registry (see DEVELOPMENT.md's "Known gaps").
 */
class BlockSolidityRegistryTest {

    @Test
    void airIsNotSolid() {
        assertFalse(BlockSolidityRegistry.isSolid(0));
    }

    @Test
    void stoneIsSolid() {
        assertTrue(BlockSolidityRegistry.isSolid(1));
    }

    @Test
    void grassBlockIsSolid() {
        assertTrue(BlockSolidityRegistry.isSolid(9));
    }

    @Test
    void shortGrassIsNotSolid() {
        assertFalse(BlockSolidityRegistry.isSolid(2248));
    }

    @Test
    void negativeIdsAreTreatedAsUnknownAndSolid() {
        assertTrue(BlockSolidityRegistry.isSolid(-1));
    }
}
