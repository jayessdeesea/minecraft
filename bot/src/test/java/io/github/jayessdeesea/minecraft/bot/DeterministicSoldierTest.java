package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link DeterministicSoldier} as a black box: construct it, feed it an order and a
 * fake {@code WorldView} ({@code terrain}/{@code withHostileAt} below), tick it, observe {@code
 * status()}. No Netty, no live server, no LLM -
 * see AGENTS.md's "Behavior modeling" section on why this decision core is built to allow exactly
 * this.
 */
class DeterministicSoldierTest {

    @Test
    void trendsTowardTargetWhenUnobstructed() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(10, 64, 10));
        alice.receiveOrder(new Order.MoveTo(0, GROUND, 0, 1, 256));

        for (int i = 0; i < 10; i++) {
            alice.tick(terrain(false));
        }

        Position position = alice.status().position();
        assertTrue(position.x() < 10, "expected to have moved toward x=0, was " + position.x());
        assertTrue(position.z() < 10, "expected to have moved toward z=0, was " + position.z());
    }

    @Test
    void stopsWhenTheNextStepIsSolid() {
        DeterministicSoldier bob = new DeterministicSoldier("Bob", new Position(5, 64, 0));
        bob.receiveOrder(new Order.MoveTo(0, GROUND, 0, 1, 256));

        bob.tick(terrain(true));

        assertEquals(new Position(5, 64, 0), bob.status().position());
    }

    @Test
    void idleOrderProducesNoMovement() {
        DeterministicSoldier idle = new DeterministicSoldier("Idle", new Position(1, 64, 1));

        idle.tick(terrain(false));

        assertEquals(new Position(1, 64, 1), idle.status().position());
    }

    @Test
    void retreatsDirectlyAwayFromANearbyHostileEvenWhenIdle() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        alice.tick(withHostileAt(new Position(3, 64, 0), false));

        Position position = alice.status().position();
        assertTrue(position.x() < 0, "expected to back away from x=3, was " + position.x());
        assertEquals(0, position.z(), 1e-9);
    }

    @Test
    void retreatOverridesAnOrderTowardTheThreat() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0, 1, 256));

        alice.tick(withHostileAt(new Position(3, 64, 0), false));

        assertTrue(alice.status().position().x() < 0, "should retreat, not follow the order toward the threat");
    }

    @Test
    void standsStillWhenTheRetreatStepIsBlocked() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(-10, GROUND, 0, 1, 256));

        alice.tick(withHostileAt(new Position(3, 64, 0), true));

        assertEquals(new Position(0, 64, 0), alice.status().position());
    }

    @Test
    void attacksAHostileWithinReachAndKeepsWithdrawing() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        OptionalInt attack = alice.tick(withHostileAt(new Position(2, 64, 0), false));

        assertEquals(OptionalInt.of(1), attack);
        assertTrue(alice.status().position().x() < 0, "should still back away while fighting");
    }

    @Test
    void doesNotAttackOutOfReach() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        assertEquals(OptionalInt.empty(), alice.tick(withHostileAt(new Position(5, 64, 0), false)));
    }

    @Test
    void waitsForTheAttackToRecharge() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        // A hostile that keeps pace: always 2 blocks east of wherever Alice is.
        WorldView chaser = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND;
            }

            @Override

            public boolean isHazard(int x, int y, int z) {

                return false;

            }


            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of(new TrackedEntity(1, 151, new Position(center.x() + 2, center.y(), center.z())));
            }
        };

        int attacks = 0;
        for (int i = 0; i < DeterministicSoldier.ATTACK_COOLDOWN_TICKS * 3; i++) {
            if (alice.tick(chaser).isPresent()) {
                attacks++;
            }
        }

        assertEquals(3, attacks);
    }

    @Test
    void ignoresHostilesOutsideContactRadius() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0, 1, 256));

        alice.tick(withHostileAt(new Position(DeterministicSoldier.CONTACT_RADIUS + 1, 64, 0), false));

        assertTrue(alice.status().position().x() > 0, "a distant hostile shouldn't interrupt the order");
    }

    @Test
    void serverCorrectionReplacesPositionOnTheNextTickInsteadOfMoving() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0, 1, 256));

        alice.correctPosition(new Position(100, 70, 100));
        alice.tick(terrain(false));
        assertEquals(new Position(100, 70, 100), alice.status().position());

        for (int i = 0; i < 40; i++) {
            alice.tick(terrain(false)); // falls from y=70 to the ground first, then plans and walks
        }
        assertEquals(GROUND, alice.status().position().y(), 1e-9);
        assertTrue(alice.status().position().x() < 100, "should resume following the order from the corrected spot");
    }

    @Test
    void fallsWithGravityAndLandsOnTheGround() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 70, 0.5));

        alice.tick(terrain(false));
        assertTrue(alice.status().position().y() < 70, "should start falling");
        assertTrue(!alice.onGround());

        for (int i = 0; i < 40; i++) {
            alice.tick(terrain(false));
        }
        assertEquals(GROUND, alice.status().position().y(), 1e-9);
        assertTrue(alice.onGround());
    }

    @Test
    void walksOffALedgeAndFallsToTheLowerGround() {
        // Ground at 64 for x < 2, three blocks lower beyond it (the most the route planner will drop).
        WorldView ledge = blocks((x, y, z) -> y < (x < 2 ? GROUND : 61));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(1.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256));

        for (int i = 0; i < 60; i++) {
            alice.tick(ledge);
        }

        assertEquals(61, alice.status().position().y(), 1e-9);
        assertTrue(alice.onGround());
    }

    @Test
    void doesNotWalkOffACliff() {
        WorldView cliff = blocks((x, y, z) -> y < (x < 2 ? GROUND : 50));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256));

        for (int i = 0; i < 100; i++) {
            alice.tick(cliff);
        }

        assertEquals(GROUND, alice.status().position().y(), 1e-9, "a 14-block drop isn't a route");
    }

    @Test
    void routesAroundATree() {
        // A 2-high trunk at x=3 in a 3-wide band - straight-line walking used to stop dead here.
        WorldView tree = blocks((x, y, z) -> y < GROUND || (x == 3 && z >= -1 && z <= 1 && y < GROUND + 2));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(8.5, GROUND, 0.5, 1, 1));

        for (int i = 0; i < 200; i++) {
            alice.tick(tree);
        }

        Position end = alice.status().position();
        double cellCentreDistance = Math.hypot(Math.floor(end.x()) + 0.5 - 8.5, Math.floor(end.z()) + 0.5 - 0.5);
        assertTrue(cellCentreDistance <= 1, "should stand in a cell inside the zone past the tree: " + end);
    }

    @Test
    void reportsCantcoWhenStuckInAPit() {
        // A 5-deep pit around the soldier: no one-block steps out.
        WorldView pit = blocks((x, y, z) -> y < GROUND - 5 || (y < GROUND && (Math.abs(x) > 1 || Math.abs(z) > 1)));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND - 5, 0.5));
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 2, 2));

        for (int i = 0; i < DeterministicSoldier.REPLAN_TICKS * (DeterministicSoldier.CANTCO_AFTER_FAILED_PLANS + 1); i++) {
            alice.tick(pit);
        }

        assertTrue(alice.status().report() instanceof Report.Cantco, "should say it can't comply: " + alice.status().report());
    }

    @Test
    void aGenuinelyNewOrderClearsCantcoButSlotDriftDoesNot() {
        WorldView pit = blocks((x, y, z) -> y < GROUND - 5 || (y < GROUND && (Math.abs(x) > 1 || Math.abs(z) > 1)));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND - 5, 0.5));
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 2, 2));
        for (int i = 0; i < DeterministicSoldier.REPLAN_TICKS * (DeterministicSoldier.CANTCO_AFTER_FAILED_PLANS + 1); i++) {
            alice.tick(pit);
        }
        assertTrue(alice.status().report() instanceof Report.Cantco);

        alice.receiveOrder(new Order.MoveTo(21.5, GROUND, 0.5, 2, 2)); // drifted one block - same task
        alice.tick(pit);
        assertTrue(alice.status().report() instanceof Report.Cantco, "slot drift keeps the CANTCO");

        alice.receiveOrder(new Order.MoveTo(-20.5, GROUND, 0.5, 2, 2)); // somewhere else entirely
        alice.tick(pit);
        assertEquals(new Report.Moving(), alice.status().report(), "a new task gets a fresh assessment");
    }

    @Test
    void slidesAlongAWallInsteadOfSnaggingOnIt() {
        // A wall along z = 1 (z >= 1 solid at body height). Alice hugs it, off-centre, and is sent
        // east along it; a straight step toward a slightly-south target would clip the wall.
        WorldView wall = blocks((x, y, z) -> y < GROUND || (z >= 1 && y < GROUND + 2));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.69));
        alice.receiveOrder(new Order.MoveTo(8.5, GROUND, 0.5, 1, 1));

        for (int i = 0; i < 100; i++) {
            alice.tick(wall);
        }

        assertTrue(alice.status().position().x() > 7, "should slide east along the wall: " + alice.status().position());
    }

    @Test
    void reportsMovingThenInPosition() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(5.5, GROUND, 0.5, 1, 1));

        alice.tick(terrain(false));
        assertEquals(new Report.Moving(), alice.status().report());

        for (int i = 0; i < 100; i++) {
            alice.tick(terrain(false));
        }
        assertEquals(new Report.InPosition(), alice.status().report());
    }

    @Test
    void reportsContact() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        alice.tick(withHostileAt(new Position(3, 64, 0), false));

        assertEquals(new Report.InContact(1), alice.status().report());
    }

    @Test
    void stopsAnywhereInsideTheToleranceZone() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 10, 2));

        for (int i = 0; i < 200; i++) {
            alice.tick(terrain(false));
        }

        double x = alice.status().position().x();
        assertTrue(x >= 10 && x < 13, "should stop just inside the zone's near edge, not its centre: " + x);
    }

    @Test
    void jumpsUpAOneBlockStep() {
        // One block higher from x >= 2.
        WorldView step = blocks((x, y, z) -> y < (x < 2 ? GROUND : GROUND + 1));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(1.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256));

        for (int i = 0; i < 40; i++) {
            alice.tick(step);
        }

        assertTrue(alice.status().position().x() > 3, "should have climbed and kept walking");
        assertEquals(GROUND + 1, alice.status().position().y(), 1e-9);
    }

    @Test
    void stopsAtATwoBlockWall() {
        WorldView wall = blocks((x, y, z) -> y < GROUND || (x >= 2 && y < GROUND + 2));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(1.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256));

        for (int i = 0; i < 40; i++) {
            alice.tick(wall);
        }

        assertTrue(alice.status().position().x() < 2, "should not pass the wall");
        assertEquals(GROUND, alice.status().position().y(), 1e-9);
    }

    @Test
    void stopsAtAHeadHeightObstacle() {
        // Clear at foot level, solid at head level from x >= 2 (e.g. an overhang).
        WorldView overhang = blocks((x, y, z) -> y < GROUND || (x >= 2 && y == GROUND + 1));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(1.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256));

        for (int i = 0; i < 20; i++) {
            alice.tick(overhang);
        }

        assertTrue(alice.status().position().x() < 2, "the body doesn't fit under the overhang");
    }

    @Test
    void theBodyIsSixTenthsWideNotAPoint() {
        // Wall at x >= 2; the centre can get no closer than x = 1.7.
        WorldView wall = blocks((x, y, z) -> y < GROUND || (x >= 2 && y < GROUND + 2));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(1.0, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256));

        for (int i = 0; i < 20; i++) {
            alice.tick(wall);
        }

        assertTrue(alice.status().position().x() <= 1.7 + 1e-9, "was " + alice.status().position().x());
    }

    @Test
    void standsOnALedgeWhileAnyPartOfTheFootprintIsSupported() {
        // Ground only for x < 2; centre at 2.2 still has its x in [1.9, 2.5) over the edge block.
        WorldView ledge = blocks((x, y, z) -> x < 2 && y < GROUND);
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(2.2, GROUND, 0.5));

        alice.tick(ledge);

        assertEquals(GROUND, alice.status().position().y(), 1e-9);
    }

    /** Flat ground (solid below y=64); at and above it, every block is {@code solid} or not. No hostiles. */
    private static WorldView terrain(boolean solid) {
        return blocks((x, y, z) -> y < GROUND || solid);
    }

    private static final int GROUND = 64;

    private interface Blocks {
        boolean isSolid(int x, int y, int z);
    }

    private static WorldView blocks(Blocks blocks) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return blocks.isSolid(x, y, z);
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

    /** A fake {@link WorldView} with one hostile, honoring the radius the soldier asks for. */
    private static WorldView withHostileAt(Position hostile, boolean everythingSolid) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || everythingSolid;
            }

            @Override

            public boolean isHazard(int x, int y, int z) {

                return false;

            }


            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                double distance = Math.sqrt(Math.pow(hostile.x() - center.x(), 2)
                        + Math.pow(hostile.y() - center.y(), 2) + Math.pow(hostile.z() - center.z(), 2));
                return distance <= radius ? List.of(new TrackedEntity(1, 151, hostile)) : List.of();
            }
        };
    }

    @Test
    void stopsAdvancingOnceCloseEnoughToTarget() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.1, 64, 0));
        alice.receiveOrder(new Order.MoveTo(0, GROUND, 0, 1, 256));

        alice.tick(terrain(false));

        assertEquals(new Position(0.1, 64, 0), alice.status().position());
    }
}
