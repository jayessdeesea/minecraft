package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampGroundTest {

    /** The box goes out a ring once it's level - dug and filled - and no further than the camp's radius. */
    @Test
    void theGroundWorkGoesOutARingOnceItsBoxIsLevel() {
        assertEquals(7, CampGround.grown(7, 3, -1, 24, "Alpha"), "still digging");
        assertEquals(7, CampGround.grown(7, 0, 5, 24, "Alpha"), "still filling");
        assertEquals(11, CampGround.grown(7, 0, 0, 24, "Alpha"), "level: a ring out");
        assertEquals(24, CampGround.grown(23, 0, 0, 24, "Alpha"), "to the camp's radius");
        assertEquals(24, CampGround.grown(24, 0, 0, 24, "Alpha"), "and no further");
    }

    /** Past the wall line the farm's plot, trenches and water are never dug: whole columns of the box. */
    @Test
    void aFarmsColumnsAreNeverDug() {
        FarmPlan farm = new FarmPlan(new BlockPos(10, 63, 0), List.of(new BlockPos(11, 63, 0)), List.of(new BlockPos(12, 63, 0)), List.of());
        Box dig = new Box(-11, 64, -11, 11, 90, 11);
        Set<Cell> kept = CampGround.kept(dig, farm, List.of(new BlockPos(-5, 64, -5)));
        assertTrue(kept.contains(new Cell(11, 64, 0)) && kept.contains(new Cell(11, 90, 0)), "the plot's column, its whole height");
        assertTrue(kept.contains(new Cell(9, 70, 1)), "a cell round the water");
        assertFalse(kept.contains(new Cell(12, 64, 0)), "the trench at 12 is out of the box at 11");
        assertFalse(kept.contains(new Cell(0, 64, 0)), "the camp's own ground is dug");
        assertTrue(kept.contains(new Cell(-6, 70, -4)), "a planted grove cell's column, a cell round");
    }
}
