package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;

class SoldiersWorkshopTest {

    private static final Workshop KEEP = new Workshop(new BlockPos(-26, 71, 4), new BlockPos(-26, 71, 5), new BlockPos(-27, 71, 4), Optional.empty());
    private static final Workshop CAMP = new Workshop(new BlockPos(-46, 70, 44), new BlockPos(-46, 70, 43), new BlockPos(-45, 70, 44), Optional.empty());

    @Test
    void aSoldierOfTheKeepsSquadOffItsWorkBakesAtTheCamp() {
        assertSame(KEEP, SoldierRunner.soldiersWorkshop(KEEP, KEEP, CAMP, true), "a builder: the keep's");
        assertSame(CAMP, SoldierRunner.soldiersWorkshop(KEEP, KEEP, CAMP, false), "fed first at the camp's farm: the camp's");
        assertSame(KEEP, SoldierRunner.soldiersWorkshop(KEEP, KEEP, null, false), "no camp's yet: the keep's");
    }
}
