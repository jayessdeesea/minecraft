package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Condition;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WantsFoodTest {

    private static Condition of(double health, int food) {
        return new Condition(health, food, 0, Optional.empty());
    }

    @Test
    void aWoundedSoldierWantsFoodUntilItCanHeal() {
        assertTrue(SoldierRunner.wantsFood(of(2, 15)), "wounded at food 15: it doesn't heal there");
        assertFalse(SoldierRunner.wantsFood(of(2, Condition.REGENERATION_FOOD)), "at the food it heals at");
    }

    @Test
    void aWellSoldierWantsFoodOnlyWhenHungry() {
        assertFalse(SoldierRunner.wantsFood(of(Condition.MAX_HEALTH, 15)));
        assertTrue(SoldierRunner.wantsFood(of(Condition.MAX_HEALTH, SoldierRunner.HUNT_BELOW_FOOD)));
    }
}
