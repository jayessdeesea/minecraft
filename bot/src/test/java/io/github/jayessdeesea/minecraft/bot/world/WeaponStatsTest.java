package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponStatsTest {

    @Test
    void everyWeaponTheItemRegistryKnowsIsMeasured() {
        IntStream.range(0, 4096)
                .filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClassRegistry.ItemClass.WEAPON)))
                .mapToObj(id -> ItemClassRegistry.nameOf(id).orElseThrow())
                .forEach(name -> assertTrue(WeaponStats.known(name), name + " has no measured stats"));
    }

    @Test
    void aSwordRechargesInThirteenTicksAndAFistInFive() {
        assertEquals(new WeaponStats.Stats(4, 1.6), WeaponStats.melee(Optional.of("wooden_sword")));
        assertEquals(13, WeaponStats.melee(Optional.of("wooden_sword")).ticksToCharge());
        assertEquals(5, WeaponStats.melee(Optional.empty()).ticksToCharge());
        assertEquals(WeaponStats.HAND, WeaponStats.melee(Optional.of("bow")), "a bow in melee is a bare hand");
    }
}
