package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        alice.receiveOrder(new Order.MoveTo(0, GROUND, 0, 1, 256, Risk.LOW));

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
        bob.receiveOrder(new Order.MoveTo(0, GROUND, 0, 1, 256, Risk.LOW));

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
    void closesOnAHostileOutsideReachEvenAgainstItsOrder() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(-10, GROUND, 0, 1, 256, Risk.LOW));

        alice.tick(withHostileAt(new Position(5, 64, 0), false));

        assertTrue(alice.status().position().x() > 0, "closes on the zombie at x=5, not away along the order");
    }

    @Test
    void holdsItsGroundAndStrikesAtArmsLength() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        OptionalInt attack = alice.tick(withHostileAt(new Position(3, 64, 0), false));

        assertEquals(OptionalInt.of(1), attack);
        assertEquals(new Position(0, 64, 0), alice.status().position(), "between keep-away and reach: hold");
    }

    @Test
    void backsOffFromAHostileInsideKeepAway() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        OptionalInt attack = alice.tick(withHostileAt(new Position(2, 64, 0), false));

        assertEquals(OptionalInt.of(1), attack);
        assertTrue(alice.status().position().x() < 0, "backs off while striking");
    }

    @Test
    void standsStillWhenBackingOffIsBlocked() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));

        alice.tick(withHostileAt(new Position(2, 64, 0), true));

        assertEquals(new Position(0, 64, 0), alice.status().position());
    }

    @Test
    void getsAwayFromACreeperInsteadOfFightingIt() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        WorldView creeper = new WorldView() {
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
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("creeper"), new Position(2, 64, 0)));
            }
        };

        OptionalInt attack = alice.tick(creeper);

        assertEquals(OptionalInt.empty(), attack);
        assertTrue(alice.status().position().x() < 0, "gets clear of the blast");
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
    void aHitWithNothingInReachSendsItAfterTheShooter() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.reportHealth(20, 20);
        alice.receiveOrder(new Order.MoveTo(-10, GROUND, 0, 1, 256, Risk.LOW));
        WorldView skeleton = withHostileAt(new Position(12, 64, 0), false);

        alice.tick(skeleton);
        assertTrue(alice.status().position().x() < 0, "nothing in contact radius: carries on with the order");

        alice.reportHealth(17, 20); // an arrow
        alice.tick(skeleton);

        assertEquals(new Report.InContact(1), alice.status().report());
        Position before = alice.status().position();
        alice.tick(skeleton);
        assertTrue(alice.status().position().x() > before.x(), "goes for the shooter at x=12");
    }

    @Test
    void ignoresHostilesOutsideContactRadius() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0, 1, 256, Risk.LOW));

        alice.tick(withHostileAt(new Position(DeterministicSoldier.CONTACT_RADIUS + 1, 64, 0), false));

        assertTrue(alice.status().position().x() > 0, "a distant hostile shouldn't interrupt the order");
    }

    @Test
    void serverCorrectionReplacesPositionOnTheNextTickInsteadOfMoving() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0, 1, 256, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256, Risk.LOW));

        for (int i = 0; i < 100; i++) {
            alice.tick(cliff);
        }

        assertEquals(GROUND, alice.status().position().y(), 1e-9, "a 14-block drop isn't a route");
    }

    @Test
    void takesADamagingDropOnlyWhenTheRiskAndItsHealthAllow() {
        // A 10-block cliff (7 damage) and nothing else: the lower ground is reachable only by falling.
        WorldView cliff = blocks((x, y, z) -> y < (x < 2 ? GROUND : GROUND - 10));
        Order.MoveTo low = new Order.MoveTo(10.5, GROUND - 10, 0.5, 1, 1, Risk.LOW);
        Order.MoveTo medium = new Order.MoveTo(10.5, GROUND - 10, 0.5, 1, 1, Risk.MEDIUM);

        assertEquals(GROUND - 10, afterTicks(cliff, medium, 20, 20).y(), 1e-9, "healthy, MEDIUM: 20 - 7 leaves 13 >= 10");
        assertEquals(GROUND, afterTicks(cliff, low, 20, 20).y(), 1e-9, "LOW never spends health");
        assertEquals(GROUND, afterTicks(cliff, medium, 15, 20).y(), 1e-9, "15 - 7 would leave 8 < 10");
        assertEquals(GROUND, afterTicks(cliff, medium, -1, 0).y(), 1e-9, "unknown health: spend none");
    }

    @Test
    void aCantcoUnderLowRiskSaysWhatARiskierOrderWouldCost() {
        WorldView cliff = blocks((x, y, z) -> y < (x < 2 ? GROUND : GROUND - 10));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportHealth(20, 20);
        alice.receiveOrder(new Order.MoveTo(10.5, GROUND - 10, 0.5, 1, 1, Risk.LOW));

        for (int i = 0; i < 400; i++) {
            alice.tick(cliff);
        }

        Report.Cantco cantco = assertInstanceOf(Report.Cantco.class, alice.status().report());
        assertTrue(cantco.reason().endsWith("a route with falls costing 7 of my 20 health exists (MEDIUM risk)"), cantco.reason());
    }

    /** Where Alice ends up after 200 ticks under {@code order}, having reported {@code health} (-1: never). */
    private static Position afterTicks(WorldView world, Order order, double health, int food) {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        if (health >= 0) {
            alice.reportHealth(health, food);
        }
        alice.receiveOrder(order);
        for (int i = 0; i < 200; i++) {
            alice.tick(world);
        }
        return alice.status().position();
    }

    @Test
    void aWellFedSoldierPricesDamageAtHalf() {
        DeterministicSoldier fed = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        fed.reportHealth(20, 20);
        DeterministicSoldier hungry = new DeterministicSoldier("Bob", new Position(0.5, GROUND, 0.5));
        hungry.reportHealth(20, 6);
        Order.MoveTo high = new Order.MoveTo(10.5, GROUND, 0.5, 1, 1, Risk.HIGH);

        assertEquals(new FallAllowance(16, Risk.HIGH.costPerHealth() / 2), fed.fallAllowance(high));
        assertEquals(new FallAllowance(16, Risk.HIGH.costPerHealth()), hungry.fallAllowance(high));
    }

    @Test
    void routesAroundATree() {
        // A 2-high trunk at x=3 in a 3-wide band - straight-line walking used to stop dead here.
        WorldView tree = blocks((x, y, z) -> y < GROUND || (x == 3 && z >= -1 && z <= 1 && y < GROUND + 2));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(8.5, GROUND, 0.5, 1, 1, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 2, 2, Risk.LOW));

        for (int i = 0; i < DeterministicSoldier.REPLAN_TICKS * (DeterministicSoldier.CANTCO_AFTER_FAILED_PLANS + 1); i++) {
            alice.tick(pit);
        }

        assertTrue(alice.status().report() instanceof Report.Cantco, "should say it can't comply: " + alice.status().report());
    }

    @Test
    void aGenuinelyNewOrderClearsCantcoButSlotDriftDoesNot() {
        WorldView pit = blocks((x, y, z) -> y < GROUND - 5 || (y < GROUND && (Math.abs(x) > 1 || Math.abs(z) > 1)));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND - 5, 0.5));
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 2, 2, Risk.LOW));
        for (int i = 0; i < DeterministicSoldier.REPLAN_TICKS * (DeterministicSoldier.CANTCO_AFTER_FAILED_PLANS + 1); i++) {
            alice.tick(pit);
        }
        assertTrue(alice.status().report() instanceof Report.Cantco);

        alice.receiveOrder(new Order.MoveTo(21.5, GROUND, 0.5, 2, 2, Risk.LOW)); // drifted one block - same task
        alice.tick(pit);
        assertTrue(alice.status().report() instanceof Report.Cantco, "slot drift keeps the CANTCO");

        alice.receiveOrder(new Order.MoveTo(-20.5, GROUND, 0.5, 2, 2, Risk.LOW)); // somewhere else entirely
        alice.tick(pit);
        assertEquals(new Report.Moving(), alice.status().report(), "a new task gets a fresh assessment");
    }

    @Test
    void slidesAlongAWallInsteadOfSnaggingOnIt() {
        // A wall along z = 1 (z >= 1 solid at body height). Alice hugs it, off-centre, and is sent
        // east along it; a straight step toward a slightly-south target would clip the wall.
        WorldView wall = blocks((x, y, z) -> y < GROUND || (z >= 1 && y < GROUND + 2));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.69));
        alice.receiveOrder(new Order.MoveTo(8.5, GROUND, 0.5, 1, 1, Risk.LOW));

        for (int i = 0; i < 100; i++) {
            alice.tick(wall);
        }

        assertTrue(alice.status().position().x() > 7, "should slide east along the wall: " + alice.status().position());
    }

    @Test
    void countsDeathsFromAliveButNotADeathCarriedOverFromAnEarlierRun() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        assertTrue(alice.status().condition().isEmpty(), "nothing reported yet");

        alice.reportHealth(0, 19); // joined dead
        alice.reportHealth(20, 20); // respawned
        alice.reportHealth(7, 15);
        alice.reportHealth(0, 15); // killed
        alice.reportHealth(20, 20);

        assertEquals(new io.github.jayessdeesea.minecraft.command.Condition(20, 20, 1), alice.status().condition().orElseThrow());
    }

    @Test
    void reportsMovingThenInPosition() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(5.5, GROUND, 0.5, 1, 1, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 10, 2, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(10, GROUND, 0.5, 1, 256, Risk.LOW));

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
        alice.receiveOrder(new Order.MoveTo(0, GROUND, 0, 1, 256, Risk.LOW));

        alice.tick(terrain(false));

        assertEquals(new Position(0.1, 64, 0), alice.status().position());
    }
}
