package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

class SiteGeometryTest {

    /** A closed pocket: a flood that ran out of ground well inside its span - not one cut short at the span's edge. */
    @Test
    void aClosedPocketIsAFloodThatRanOutOfGround() {
        io.github.jayessdeesea.minecraft.command.Position at = new io.github.jayessdeesea.minecraft.command.Position(0.5, 20, 0.5);
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cave = new java.util.HashSet<>();
        for (int x = -5; x <= 5; x++) {
            cave.add(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, 20, 0));
        }
        org.junit.jupiter.api.Assertions.assertTrue(SiteGeometry.closedPocket(at, cave), "a cave of eleven cells");
        cave.add(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(SoldierRunner.CUT_OFF_SPAN - 1, 20, 0));
        org.junit.jupiter.api.Assertions.assertFalse(SiteGeometry.closedPocket(at, cave), "a gallery reaching the flood's span: cut short, not closed");
    }
}
