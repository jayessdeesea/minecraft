package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real entity-type protocol IDs from protocol 777's own {@code --reports} registries.json, not
 * made-up numbers: 10 = bat (the first entity seen live in {@code PersistentJoinLiveIT}), 30 = cow,
 * 32 = creeper, 154 = zombie.
 */
class HostileMobRegistryTest {

    @Test
    void zombieIsHostile() {
        assertTrue(HostileMobRegistry.isHostile(154));
    }

    @Test
    void creeperIsHostile() {
        assertTrue(HostileMobRegistry.isHostile(32));
    }

    @Test
    void cowIsNotHostile() {
        assertFalse(HostileMobRegistry.isHostile(30));
    }

    @Test
    void batIsNotHostile() {
        assertFalse(HostileMobRegistry.isHostile(10));
    }

    @Test
    void negativeIdsAreNotHostile() {
        assertFalse(HostileMobRegistry.isHostile(-1));
    }
}
