package io.github.jayessdeesea.minecraft.bot.nav;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic terrain: flat ground (solid below y=64) plus whatever each test adds. */
class PathfinderTest {

    private static final int GROUND = 64;
    private static final BlockPos START = new BlockPos(0, GROUND, 0);

    private interface Blocks {
        boolean isSolid(int x, int y, int z);
    }

    private static WorldView world(Blocks extra) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || extra.isSolid(x, y, z);
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

    private static Goal exactly(int x, int y, int z) {
        return new Goal(x + 0.5, y, z + 0.5, 0.1, 0);
    }

    @Test
    void walksStraightAcrossOpenGround() {
        Pathfinder.Result result = Pathfinder.find(world((x, y, z) -> false), START, exactly(5, GROUND, 0), 1000);

        assertTrue(result.reachesGoal());
        assertEquals(new BlockPos(5, GROUND, 0), result.path().getLast());
        assertEquals(5, result.path().size());
    }

    @Test
    void goesAroundATree() {
        // A two-high trunk at x=2, z=-1..1 - in the way of the straight line.
        WorldView trees = world((x, y, z) -> x == 2 && z >= -1 && z <= 1 && y < GROUND + 2);

        Pathfinder.Result result = Pathfinder.find(trees, START, exactly(5, GROUND, 0), 1000);

        assertTrue(result.reachesGoal());
        for (BlockPos cell : result.path()) {
            assertFalse(trees.isSolid(cell.x(), cell.y(), cell.z()), "path goes through the trunk at " + cell);
        }
    }

    @Test
    void doesNotCutCornersDiagonally() {
        // Two blocks touching only at a corner: (1,0) and (0,1). Going diagonally to (1,1) would clip both.
        WorldView corner = world((x, y, z) -> y < GROUND + 2 && ((x == 1 && z == 0) || (x == 0 && z == 1)));

        Pathfinder.Result result = Pathfinder.find(corner, START, exactly(1, GROUND, 1), 1000);

        assertFalse(result.path().contains(new BlockPos(1, GROUND, 1)) && result.path().size() == 1,
                "must not step diagonally between two corner-touching blocks");
    }

    @Test
    void climbsOneBlockSteps() {
        // A staircase: each x step one block higher.
        WorldView stairs = world((x, y, z) -> x > 0 && y < GROUND + x);

        Pathfinder.Result result = Pathfinder.find(stairs, START, exactly(3, GROUND + 3, 0), 1000);

        assertTrue(result.reachesGoal());
        assertEquals(List.of(new BlockPos(1, GROUND + 1, 0), new BlockPos(2, GROUND + 2, 0), new BlockPos(3, GROUND + 3, 0)),
                result.path());
    }

    @Test
    void doesNotClimbATwoBlockWall() {
        WorldView wall = world((x, y, z) -> x == 2 && y < GROUND + 2);

        Pathfinder.Result result = Pathfinder.find(wall, START, exactly(4, GROUND, 0), 5000);

        // The wall is infinite in z, so the far side is unreachable - the search gives a partial path.
        assertFalse(result.reachesGoal());
        assertTrue(result.path().stream().allMatch(cell -> cell.x() < 2));
    }

    @Test
    void dropsDownAShortLedgeButNotACliff() {
        WorldView ledge = world((x, y, z) -> false);
        WorldView pit = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return x <= 0 ? y < GROUND : y < GROUND - 3; // 3-block drop beyond x=0
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
        WorldView cliff = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return x <= 0 ? y < GROUND : y < GROUND - 10;
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

        assertTrue(Pathfinder.find(pit, START, exactly(2, GROUND - 3, 0), 1000).reachesGoal());
        assertFalse(Pathfinder.find(cliff, START, exactly(2, GROUND - 10, 0), 1000).reachesGoal());
        assertTrue(Pathfinder.find(ledge, START, exactly(1, GROUND, 0), 10).reachesGoal());

        assertTrue(Pathfinder.find(cliff, START, exactly(2, GROUND - 10, 0), 1000, new FallAllowance(7, 5)).reachesGoal(),
                "a 10-block drop costs 7");
        assertFalse(Pathfinder.find(cliff, START, exactly(2, GROUND - 10, 0), 1000, new FallAllowance(6, 5)).reachesGoal());
        assertTrue(Pathfinder.find(cliff, START, exactly(2, GROUND - 10, 0), 1000, new FallAllowance(4, 5, 0.52)).reachesGoal(),
                "Feather Falling IV (12 points) lets 48% through: the drop costs 3.6, within 4");
    }

    @Test
    void prefersALongerSafeRouteToADamagingDropUnlessDamageIsCheap() {
        // Lower ground 5 down beyond x=0, except a 1-block staircase down along z=3.
        WorldView steps = world((x, y, z) -> false);
        WorldView terrain = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                if (x <= 0) {
                    return y < GROUND;
                }
                if (z == 3 && x <= 5) {
                    return y < GROUND - x;
                }
                return y < GROUND - 5;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return steps.nearbyHostiles(center, radius);
            }
        };
        Goal below = exactly(7, GROUND - 5, 0);

        List<BlockPos> careful = Pathfinder.find(terrain, START, below, 5000, new FallAllowance(10, 20)).path();
        List<BlockPos> hasty = Pathfinder.find(terrain, START, below, 5000, new FallAllowance(10, 0.1)).path();

        assertTrue(maxDrop(careful) <= Pathfinder.MAX_DROP, "takes the stairs: " + careful);
        assertTrue(maxDrop(hasty) > Pathfinder.MAX_DROP, "jumps: " + hasty);
    }

    private static int maxDrop(List<BlockPos> path) {
        int max = 0;
        BlockPos previous = START;
        for (BlockPos cell : path) {
            max = Math.max(max, previous.y() - cell.y());
            previous = cell;
        }
        return max;
    }

    @Test
    void stopsAnywhereInsideTheToleranceZone() {
        Goal loose = new Goal(10.5, GROUND, 0.5, 4, 1);

        Pathfinder.Result result = Pathfinder.find(world((x, y, z) -> false), START, loose, 1000);

        assertTrue(result.reachesGoal());
        assertEquals(6, result.path().getLast().x(), "should stop at the near edge of the zone, not its centre");
    }

    @Test
    void verticalToleranceKeepsTheSoldierOnTheRequestedFloor() {
        // A raised platform (top at GROUND+3) right at the goal; ground level is also near the goal.
        WorldView platform = world((x, y, z) -> x >= 8 && x <= 12 && y < GROUND + 3);
        Goal upstairs = new Goal(10.5, GROUND + 3, 0.5, 6, 0);

        Pathfinder.Result result = Pathfinder.find(platform, START, upstairs, 5000);

        // The platform is 3 high with no steps - unreachable, so the ground nearby must not count.
        assertFalse(result.reachesGoal());
    }

    /** Ground with a 3-wide water channel at x=2..4, z=-2..2 - the surface of the water is at y=GROUND-1. */
    private static WorldView pond() {
        return new WorldView() {
            private boolean water(int x, int y, int z) {
                return x >= 2 && x <= 4 && z >= -2 && z <= 2 && y == GROUND - 1;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND && !water(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return water(x, y, z);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void routesAroundWaterRatherThanThroughIt() {
        WorldView pond = pond();

        Pathfinder.Result result = Pathfinder.find(pond, START, exactly(6, GROUND, 0), 2000);

        assertTrue(result.reachesGoal());
        for (BlockPos cell : result.path()) {
            assertFalse(pond.isHazard(cell.x(), cell.y() - 1, cell.z()) || pond.isHazard(cell.x(), cell.y(), cell.z()),
                    "path enters the water at " + cell);
        }
    }

    @Test
    void aFenceIsNotAOneBlockStep() {
        // A fence row at x=2, as ChunkCache reports it: the fence cell and the cell above are both solid.
        WorldView fence = world((x, y, z) -> x == 2 && (y == GROUND || y == GROUND + 1));

        Pathfinder.Result result = Pathfinder.find(fence, START, exactly(4, GROUND, 0), 3000);

        assertFalse(result.reachesGoal(), "an endless fence can't be crossed");
    }

    @Test
    void anOutOfBudgetGoalYieldsPartialProgressTowardIt() {
        Pathfinder.Result result = Pathfinder.find(world((x, y, z) -> false), START, exactly(500, GROUND, 0), 200);

        assertFalse(result.reachesGoal());
        assertTrue(result.path().getLast().x() > 5, "should still make headway: " + result.path().getLast());
    }
}
