package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyInTheWayTest {

    private static final BlockPos STEP = new BlockPos(19, 90, -37);

    @Test
    void aSquadmateStandingInTheStepsCellIsInTheWay() {
        // Live (field333): Alice stood at (19.6, 90.0, -36.7) as Bob pillared into (19, 90, -37).
        assertTrue(SoldierRunner.overlaps(new Position(19.6, 90.0, -36.7), STEP));
        assertTrue(SoldierRunner.overlaps(new Position(18.8, 89.0, -36.5), STEP), "its head in the cell, its feet under it");
    }

    @Test
    void oneBesideItOrAboveItIsNot() {
        assertFalse(SoldierRunner.overlaps(new Position(20.4, 90.0, -36.5), STEP), "beside: its body's half-width short of the cell");
        assertFalse(SoldierRunner.overlaps(new Position(19.5, 91.0, -36.5), STEP), "standing on it");
        assertFalse(SoldierRunner.overlaps(new Position(19.5, 88.2, -36.5), STEP), "under it, its head short of the cell");
    }
}
