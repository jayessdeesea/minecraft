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

    /** Flat soil ground, raised {@code height} blocks for x of 2 and beyond - a bank, sand on it where {@code sandTop}. */
    private static WorldView bank(int height, boolean sandTop) {
        return new WorldView() {
            private boolean ground(int x, int y) {
                return y < GROUND || x >= 2 && y < GROUND + height;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return ground(x, y) || sandTop && x >= 2 && y == GROUND + height;
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return ground(x, y);
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
    void aSoilBankTooHighToJumpHasAStepCutIntoIt() {
        BlockPos foot = new BlockPos(1, GROUND, 0);
        BlockPos step = new BlockPos(2, GROUND + 1, 0);

        assertEquals(List.of(step), Pathfinder.stepCut(bank(2, false), foot, step), "one block out of a 2-high bank");
        assertEquals(List.of(step, step.offset(0, 1, 0)), Pathfinder.stepCut(bank(3, false), foot, step),
                "two out of a 3-high one: the step and the head room over it");
        assertTrue(Pathfinder.find(bank(3, false), START, exactly(5, GROUND + 3, 0), 5000).reachesGoal());
    }

    @Test
    void noStepIsCutWhereItWouldTunnelOrBringSandDown() {
        BlockPos foot = new BlockPos(1, GROUND, 0);
        BlockPos step = new BlockPos(2, GROUND + 1, 0);

        assertEquals(List.of(), Pathfinder.stepCut(bank(4, false), foot, step), "a 4-high face is a tunnel, not a step");
        assertEquals(List.of(), Pathfinder.stepCut(bank(2, true), foot, step), "sand isn't dug");
        assertFalse(Pathfinder.find(bank(4, false), START, exactly(5, GROUND + 4, 0), 5000).reachesGoal());
        assertFalse(Pathfinder.find(world((x, y, z) -> x >= 2 && y < GROUND + 2), START, exactly(5, GROUND + 2, 0), 5000).reachesGoal(),
                "stone isn't dug");
    }

    /** Flat ground, a bank of plain stone {@code height} high for x of 2 and beyond. */
    private static WorldView stoneBank(int height) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || x >= 2 && y < GROUND + height;
            }

            @Override
            public boolean isStone(int x, int y, int z) {
                return x >= 2 && y >= GROUND && y < GROUND + height;
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
    void withAPickaxeAStepIsCutIntoStoneToo() {
        BlockPos foot = new BlockPos(1, GROUND, 0);
        BlockPos step = new BlockPos(2, GROUND + 1, 0);

        assertEquals(List.of(), Pathfinder.stepCut(stoneBank(2), foot, step), "not by hand");
        assertEquals(List.of(step), Pathfinder.stepCut(stoneBank(2), foot, step, true));
        assertFalse(Pathfinder.find(stoneBank(3), START, exactly(5, GROUND + 3, 0), 5000).reachesGoal());
        assertTrue(Pathfinder.find(stoneBank(3), START, exactly(5, GROUND + 3, 0), 5000, FallAllowance.NONE, 0, true).reachesGoal());
        assertFalse(Pathfinder.find(stoneBank(4), START, exactly(5, GROUND + 4, 0), 5000, FallAllowance.NONE, 0, true).reachesGoal(),
                "still no tunnelling");
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

    @Test
    void withBlocksItBuildsAStairDownACliffRatherThanFall() {
        // A sheer 10-block face east of x=0.
        WorldView sheer = new WorldView() {
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
        Goal bottom = new Goal(8.5, GROUND - 10, 0.5, 2, 0.5);

        Pathfinder.Result stair = Pathfinder.find(sheer, START, bottom, 20000, FallAllowance.NONE, 30, false, true);

        assertTrue(stair.reachesGoal(), "" + stair.path());
        assertEquals(0, Pathfinder.fallDamage(START, stair.path()), "every drop on it walked unhurt: " + stair.path());
        assertFalse(Pathfinder.find(sheer, START, bottom, 20000, FallAllowance.NONE, 0, false, true).reachesGoal(), "no blocks, no stair");
    }

    @Test
    void aBuiltStepDownGoesAgainstTheFaceOrOnABlockUnderItsOwnGround() {
        WorldView face = world((x, y, z) -> x <= 0 && y < GROUND + 10); // standing on a tower's top, x <= 0
        BlockPos top = new BlockPos(0, GROUND + 10, 0);

        // Out over the edge, the tower's side beside the step holds it: one block.
        assertEquals(List.of(new BlockPos(1, GROUND + 8, 0)), Pathfinder.stepBuild(face, top, new BlockPos(1, GROUND + 9, 0)));

        WorldView slab = world((x, y, z) -> x == 0 && z == 0 && y == GROUND + 9); // one block in mid-air
        // Nothing beside the step: a block under its own ground first.
        assertEquals(List.of(new BlockPos(0, GROUND + 8, 0), new BlockPos(1, GROUND + 8, 0)),
                Pathfinder.stepBuild(slab, top, new BlockPos(1, GROUND + 9, 0)));
        assertEquals(List.of(), Pathfinder.stepBuild(world((x, y, z) -> false), START, new BlockPos(1, GROUND - 1, 0)),
                "level ground: nothing to build");
    }

    @Test
    void withBlocksItBuildsAStairUpToAPlatformInOpenAir() {
        // A 3x3 platform 8 blocks up, nothing under it.
        WorldView platform = world((x, y, z) -> y == GROUND + 7 && x >= 6 && x <= 8 && z >= -1 && z <= 1);
        Goal onIt = new Goal(7.5, GROUND + 8, 0.5, 0.5, 0.5);

        Pathfinder.Result stair = Pathfinder.find(platform, START, onIt, 20000, FallAllowance.NONE, 20, false, true);

        assertTrue(stair.reachesGoal(), "" + stair.path());
        assertFalse(Pathfinder.find(platform, START, onIt, 20000, FallAllowance.NONE, 0, false, true).reachesGoal());
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

    /**
     * A river 3 wide at x=2..4 across all z, 2 deep, its surface at y=GROUND-1: no way round. The
     * banks stand a block above the water.
     */
    static WorldView river() {
        return new WorldView() {
            private boolean water(int x, int y, int z) {
                return x >= 2 && x <= 4 && y >= GROUND - 2 && y <= GROUND - 1;
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
            public boolean isWater(int x, int y, int z) {
                return water(x, y, z);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    /**
     * A pool one block deep and one cell wide, its water at y=GROUND-1, in soil whose top is at
     * GROUND+1: a bank two blocks above the water all round, too high to climb out of.
     */
    static WorldView pit() {
        return new WorldView() {
            private boolean water(int x, int y, int z) {
                return x == 0 && z == 0 && y == GROUND - 1;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND && !water(x, y, z) && !(x == 0 && z == 0 && y == GROUND);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return water(x, y, z);
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return water(x, y, z);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void aSwimmerInAPitWithBanksTooHighCutsAStepOutOfTheWater() {
        BlockPos afloat = new BlockPos(0, GROUND - 1, 0);

        assertTrue(Pathfinder.find(pit(), afloat, exactly(4, GROUND + 1, 0), 5000).reachesGoal(), "a step cut in the soil bank");
    }

    @Test
    void aCellItGotStuckShortOfIsRoutedRoundNotTriedAgain() {
        WorldView flat = world((x, y, z) -> y < GROUND);
        BlockPos blockedStep = new BlockPos(2, GROUND, 0);
        Pathfinder.Result straight = Pathfinder.find(flat, START, exactly(4, GROUND, 0), 2000);
        assertTrue(straight.path().contains(blockedStep), "the straight way: " + straight.path());

        Pathfinder.Result round = Pathfinder.find(flat, START, exactly(4, GROUND, 0), 2000, FallAllowance.NONE, 0, false, false,
                java.util.Set.of(blockedStep));

        assertTrue(round.reachesGoal());
        assertFalse(round.path().contains(blockedStep), "round it: " + round.path());
    }

    @Test
    void swimsARiverThereIsNoWayRound() {
        Pathfinder.Result result = Pathfinder.find(river(), START, exactly(6, GROUND, 0), 2000);

        assertTrue(result.reachesGoal());
        assertTrue(result.path().stream().anyMatch(c -> c.y() == GROUND - 1), "across at the surface: " + result.path());
        assertTrue(result.path().stream().allMatch(c -> c.y() >= GROUND - 1), "never under it: " + result.path());
    }

    @Test
    void theFarBankOfARiverIsReachable() {
        assertTrue(Pathfinder.reachable(river(), START, 8, 2000).contains(new BlockPos(6, GROUND, 0)));
    }

    /** Ground with a 3-wide channel of a hazard that isn't water (lava, say) at x=2..4, z=-2..2, its top at y=GROUND-1. */
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
    void routesAroundLavaRatherThanThroughIt() {
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
