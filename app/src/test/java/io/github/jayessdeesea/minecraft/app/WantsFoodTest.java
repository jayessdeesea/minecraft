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
        assertTrue(SoldierErrands.wantsFood(of(2, 15)), "wounded at food 15: it doesn't heal there");
        assertFalse(SoldierErrands.wantsFood(of(2, Condition.REGENERATION_FOOD)), "at the food it heals at");
    }

    @Test
    void aWellSoldierWantsFoodOnlyWhenHungry() {
        assertFalse(SoldierErrands.wantsFood(of(Condition.MAX_HEALTH, 15)));
        assertTrue(SoldierErrands.wantsFood(of(Condition.MAX_HEALTH, SoldierErrands.HUNT_BELOW_FOOD)));
    }
}
