package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteEffortTest {

    /** Ground whose top block is at {@code height(x, z)}. */
    private static WorldView ground(IntBinaryOperator height) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= height.applyAsInt(x, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void levelGroundIsAStraightWalk() {
        RouteEffort walk = RouteEffort.of("farm", ground((x, z) -> 63), new BlockPos(0, 64, 0), new BlockPos(10, 64, 0));
        assertTrue(walk.reached());
        assertEquals(100, walk.percent(), walk.line());
        assertEquals(0, walk.jumps() + walk.drops(), walk.line());
    }

    @Test
    void aStepUpAndDownIsCounted() {
        // A ridge one block high across the way at x=5.
        RouteEffort walk = RouteEffort.of("farm", ground((x, z) -> x == 5 ? 64 : 63), new BlockPos(0, 64, 0), new BlockPos(10, 64, 0));
        assertTrue(walk.reached());
        assertEquals(1, walk.jumps(), walk.line());
        assertEquals(1, walk.drops(), walk.line());
    }

    @Test
    void aWallInTheWayIsADetourOrNoRoute() {
        // A wall three high at x=5 from z=-12 to 12: round it, a detour.
        RouteEffort round = RouteEffort.of("farm", ground((x, z) -> x == 5 && Math.abs(z) <= 12 ? 66 : 63),
                new BlockPos(0, 64, 0), new BlockPos(10, 64, 0));
        assertTrue(round.reached());
        assertTrue(round.percent() < 100, round.line());
        // And all the way across: no way on foot.
        RouteEffort none = RouteEffort.of("farm", ground((x, z) -> x == 5 ? 66 : 63), new BlockPos(0, 64, 0), new BlockPos(10, 64, 0));
        assertFalse(none.reached(), none.line());
    }
}
