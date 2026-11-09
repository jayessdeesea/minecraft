package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StaircaseTest {

    private final Staircase stairs = new Staircase(new BlockPos(0, 64, 0), 40);

    @Test
    void everyTorchStandsWhereABodyWalks() {
        Set<BlockPos> walkway = stairs.walkway();
        for (BlockPos light : stairs.lights()) {
            assertTrue(walkway.contains(light) || stairs.landing().contains(light), light + " is off the walkway");
        }
    }

    @Test
    void everyTunnelCellIsWithinATorchsLight() {
        List<BlockPos> lights = stairs.lights();
        stairs.cuts().stream().filter(c -> c.tunnel() >= 0).map(Staircase.Cut::feet).forEach(cell ->
                assertTrue(lights.stream().anyMatch(l -> Math.abs(l.x() - cell.x()) + Math.abs(l.y() - cell.y()) + Math.abs(l.z() - cell.z())
                        <= Lighting.REACH), cell + " is dark"));
    }
}
