package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaircaseTest {

    private final Staircase stairs = new Staircase(new BlockPos(0, 64, 0), 40);

    /** Ground to y=63, with {@code open} cleared out of it. */
    private static io.github.jayessdeesea.minecraft.bot.world.WorldView ground(Set<BlockPos> open) {
        return new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < 64 && !open.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void aCaveAtTheLandingIsNoLandingTillTheStepsReachIt() {
        Set<BlockPos> open = new java.util.HashSet<>();
        for (int k = 0; k < 8; k++) {
            open.addAll(stairs.cells(k)); // a third of the way down
        }
        for (BlockPos recess : stairs.landing()) {
            open.add(recess);
            open.add(recess.offset(0, 1, 0)); // a cave at the landing's depth
        }

        assertFalse(stairs.landingCut(ground(open)), "the steps stop 16 blocks above it");

        for (int k = 0; k < stairs.steps(); k++) {
            open.addAll(stairs.cells(k));
        }
        assertTrue(stairs.landingCut(ground(open)), "cut all the way down");
    }

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
