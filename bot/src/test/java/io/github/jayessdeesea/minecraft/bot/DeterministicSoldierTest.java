package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link DeterministicSoldier} as a black box: construct it, feed it an order and a
 * fake {@code WorldView} ({@code terrain}/{@code withHostileAt} below), tick it, observe {@code
 * status()}. No Netty, no live server, no LLM -
 * see docs/architecture.md's \"Behavior modeling\" section on why this decision core is built to allow exactly
 * this.
 */
class DeterministicSoldierTest {

    @Test
    void aSoldierOnABlocksEdgeRoutesFromTheBlockItStandsOn() {
        // Flat ground at y=63, one block on it at (9, 64, 0); the soldier on its top, overhanging x=10.
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || x == 9 && y == 64 && z == 0;
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

        assertEquals(new BlockPos(9, 65, 0), DeterministicSoldier.standingCell(world, new Position(10.2, 65, 0.5)),
                "the block's cell, not the open one its body overhangs");
        assertEquals(new BlockPos(5, 64, 0), DeterministicSoldier.standingCell(world, new Position(5.5, 64, 0.5)), "on the ground, its own");
    }

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
    void sprintsToAFarGoalAndWalksTheLastStretch() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(RATIONED);
        alice.receiveOrder(new Order.MoveTo(-29.5, GROUND, 0.5, 1, 256, Risk.LOW));
        alice.tick(terrain(false)); // plans
        double before = alice.status().position().x();
        alice.tick(terrain(false));
        assertEquals(DeterministicSoldier.SPRINT_STEP, before - alice.status().position().x(), 1e-6, "30 blocks out: sprint");

        DeterministicSoldier bob = new DeterministicSoldier("Bob", new Position(0.5, GROUND, 0.5));
        bob.reportKit(RATIONED);
        bob.receiveOrder(new Order.MoveTo(-4.5, GROUND, 0.5, 1, 256, Risk.LOW));
        bob.tick(terrain(false));
        before = bob.status().position().x();
        bob.tick(terrain(false));
        assertTrue(before - bob.status().position().x() < DeterministicSoldier.SPRINT_STEP - 1e-6, "5 blocks out: walk");
        assertTrue(alice.sprinting(), "the server is told, so it charges for it");
        assertTrue(!bob.sprinting());
    }

    /** Carrying rations: food spent sprinting can be made up. */
    private static final Kit RATIONED =
            new Kit(Optional.empty(), 0, 0, 0, 0, 0, 4);

    @Test
    void aSoldierWithoutRationsWalksEvenFar() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.empty(), 0, 0, 0, 0, 0, 0));
        alice.receiveOrder(new Order.MoveTo(-29.5, GROUND, 0.5, 1, 256, Risk.LOW));
        alice.tick(terrain(false));
        alice.tick(terrain(false));

        assertTrue(!alice.sprinting(), "nothing to make the food up with");
    }

    @Test
    void anArrowFromOutOfSightRaisesTheShieldFacingItAndItDropsOnceItsPast() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 0, 0, true));
        alice.receiveOrder(new Order.MoveTo(-29.5, GROUND, 0.5, 1, 256, Risk.LOW));
        boolean[] arrow = {true};
        WorldView shotAt = new WorldView() {
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
                return List.of();
            }

            @Override
            public List<Position> arrowsNear(Position center, double radius) {
                return arrow[0] ? List.of(new Position(10.5, GROUND + 1, 0.5)) : List.of();
            }
        };
        alice.tick(shotAt);
        double before = alice.status().position().x();
        alice.tick(shotAt);

        assertTrue(alice.shieldRaised());
        assertEquals(before, alice.status().position().x(), 1e-9, "no shooter in sight: it holds behind the shield");
        assertEquals(-90, alice.yaw(), 1e-3, "facing where the arrow came from, east");
        arrow[0] = false;
        for (int i = 0; i <= DeterministicSoldier.SHIELD_HOLD_TICKS; i++) {
            alice.tick(shotAt);
        }
        assertTrue(!alice.shieldRaised(), "lowered once the arrow's past");
    }

    @Test
    void aSoldierWithoutAShieldDoesntRaiseOne() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        alice.tick(new WorldView() {
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
                return List.of();
            }

            @Override
            public List<Position> arrowsNear(Position center, double radius) {
                return List.of(new Position(10.5, GROUND + 1, 0.5));
            }
        });

        assertTrue(!alice.shieldRaised());
    }

    @Test
    void aSoldierAtFoodSixOrBelowCantSprint() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(RATIONED);
        alice.reportHealth(20, DeterministicSoldier.SPRINT_FOOD_FLOOR);
        alice.receiveOrder(new Order.MoveTo(-29.5, GROUND, 0.5, 1, 256, Risk.LOW));
        alice.tick(terrain(false));
        double before = alice.status().position().x();
        alice.tick(terrain(false));

        assertTrue(before - alice.status().position().x() < DeterministicSoldier.SPRINT_STEP - 1e-6, "too hungry: walks");
        assertTrue(!alice.sprinting());
    }

    @Test
    void facesWhereItWalksAndThenTheHostileItFights() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(-9.5, GROUND, 0.5, 1, 256, Risk.LOW)); // due west

        alice.tick(terrain(false));
        assertEquals(90, alice.yaw(), 1, "walking west faces west");
        assertEquals(0, alice.pitch(), 1e-6);

        alice.tick(withHostileAt(new Position(alice.status().position().x(), GROUND, 5.5), false));
        assertEquals(0, alice.yaw(), 1, "a zombie to the south is faced");
        assertTrue(alice.pitch() > 0, "and looked down at, being shorter than eye height");
    }

    @Test
    void anArrowAimedFlatAtANearMarkAndHigherForAFarOne() {
        double near = DeterministicSoldier.aimPitch(8, 0).orElseThrow();
        double far = DeterministicSoldier.aimPitch(30, 0).orElseThrow();
        assertTrue(near < 0 && near > -3, "a little up, a short shot: " + near);
        assertTrue(far < near, "higher for a longer one: " + far);
        assertTrue(DeterministicSoldier.aimPitch(8, -6).orElseThrow() > 0, "down, at a mark below");
    }

    @Test
    void aSoldierWithABowStandsAndShootsAtAHostileOutOfReachLoosingAtFullDraw() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 16, 0, 0, 0, 0, 0,
                false, false, 0, true));
        WorldView zombie = withHostileAt(new Position(7.5, GROUND, 0.5), false);

        int loosed = 0;
        for (int tick = 0; tick < DeterministicSoldier.FULL_DRAW_TICKS; tick++) {
            alice.tick(zombie);
            assertTrue(alice.drawing(), "the bow is drawn while it shoots");
            loosed += alice.takeLoose() ? 1 : 0;
        }

        assertEquals(1, loosed, "one arrow, at full draw");
        assertEquals(0.5, alice.status().position().x(), 1e-9, "it stands to shoot, not closes");
        alice.tick(terrain(false));
        assertTrue(!alice.drawing(), "no hostile, the bow comes down");
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
    void aSwordWaitsWhileASquadmateStandsInItsSweep() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        Position zombie = new Position(3, 64, 0);
        WorldView bobBeside = new WorldView() {
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
                return List.of(new TrackedEntity(1, 154, zombie));
            }

            @Override
            public List<Position> playersNear(Position center, double radius) {
                // Alice herself, as her squadmates see her, and Bob a block from the zombie.
                return List.of(new Position(0, 64, 0), new Position(3, 64, 1));
            }
        };

        assertEquals(OptionalInt.empty(), alice.tick(bobBeside), "Bob is in the sweep");
        assertEquals(OptionalInt.of(1), alice.tick(withHostileAt(zombie, false)), "with nobody beside it, she swings");
    }

    /**
     * A flier's sweep reaches down a squadmate's whole height: live, Dana swung at a phantom two
     * blocks over Fran's feet, and the sweep killed Fran at a heart.
     */
    @Test
    void aSquadmateUnderAFlierIsInTheSwordsSweep() {
        DeterministicSoldier dana = new DeterministicSoldier("Dana", new Position(0, 64, 0));
        dana.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        dana.entityId(7);
        Position phantom = new Position(1.5, 66, 0);
        WorldView franUnderIt = new WorldView() {
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
                return List.of(new TrackedEntity(1, 154, phantom));
            }

            @Override
            public List<Position> playersNear(Position center, double radius, int except) {
                return List.of(new Position(1.5, 64, 1));
            }
        };

        assertEquals(OptionalInt.empty(), dana.tick(franUnderIt), "Fran stands under the phantom, in the sweep");
        assertEquals(OptionalInt.of(1), dana.tick(withHostileAt(phantom, false)), "with nobody under it, she swings");
    }

    @Test
    void aSquadmateStandingWhereTheSwordsmanStandsIsStillInItsSweep() {
        DeterministicSoldier eli = new DeterministicSoldier("Eli", new Position(0, 64, 0));
        eli.reportKit(new Kit(Optional.of("wooden_sword"), 0, 0));
        eli.entityId(7);
        Position zombie = new Position(1.2, 64, 0);
        WorldView halInHisBoots = new WorldView() {
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
                return List.of(new TrackedEntity(1, 154, zombie));
            }

            @Override
            public List<Position> playersNear(Position center, double radius, int except) {
                // Hal, a tenth of a block from Eli - and Eli himself, entity 7, left out.
                return except == 7 ? List.of(new Position(0.1, 64, 0)) : List.of(new Position(0, 64, 0), new Position(0.1, 64, 0));
            }
        };

        assertEquals(OptionalInt.empty(), eli.tick(halInHisBoots), "Hal is in the sweep, a tenth of a block from Eli");
    }

    @Test
    void backingOffAHostileItStopsAtTheEdgeOfADrop() {
        // A ledge ten blocks up, its edge at x=0: west of it, the ground is at GROUND - 10.
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 64, 0.5));
        WorldView ledge = withHostileAt(new Position(2.0, 64, 0.5), (x, y, z) -> x >= 0 ? y < GROUND : y < GROUND - 10);

        for (int tick = 0; tick < 20; tick++) {
            alice.tick(ledge);
        }

        assertEquals(64, alice.status().position().y(), 1e-9, "still on the ledge: " + alice.status().position());
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
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.3, 64, 0.5));
        // Her back flush to a wall: she sees the zombie in front, and can't step back.
        WorldView backToTheWall = withHostileAt(new Position(2.3, 64, 0.5), (x, y, z) -> y < GROUND || x < 0);

        alice.tick(backToTheWall);

        assertEquals(new Report.InContact(1), alice.status().report());
        assertEquals(new Position(0.3, 64, 0.5), alice.status().position());
    }

    @Test
    void aHostileBehindAWallIsNotContact() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 64, 0.5));
        // A wall two high at x=2, between her and a zombie at x=4 - a patrol base's wall.
        WorldView walled = withHostileAt(new Position(4.5, 64, 0.5), (x, y, z) -> y < GROUND || (x == 2 && y <= GROUND + 2));

        alice.tick(walled);

        assertEquals(new Report.Idle(), alice.status().report());
    }

    @Test
    void withAWallAtItsBackItSidestepsACreeper() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 64, 0.5));
        // A wall two high just west of her, the creeper coming from the east.
        WorldView walled = withHostileAt(new Position(3.5, 64, 0.5), (x, y, z) -> y < GROUND || x < 0 && y <= GROUND + 1);
        WorldView creeper = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return walled.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("creeper"), new Position(3.5, 64, 0.5)));
            }
        };

        for (int tick = 0; tick < 10; tick++) {
            alice.tick(creeper);
        }

        assertTrue(Math.abs(alice.status().position().z() - 0.5) > 0.5, "moves along the wall, out of the creeper's line: " + alice.status().position());
    }

    @Test
    void boxedInItStrikesTheCreeper() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 64, 0.5));
        // Walls on three sides, the creeper on the fourth.
        WorldView boxed = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || y <= GROUND + 2 && (x < 0 || z < 0 || z > 0);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("creeper"), new Position(2.5, 64, 0.5)));
            }
        };

        // The last fraction of a block to the wall at her back first, then nowhere left: she strikes.
        boolean struck = false;
        for (int tick = 0; tick < 5 && !struck; tick++) {
            struck = alice.tick(boxed).isPresent();
        }
        assertTrue(struck);
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
                return List.of(new TrackedEntity(1, 154, new Position(center.x() + 2, center.y(), center.z())));
            }
        };

        int attacks = 0;
        // Bare-handed: a fist recharges in 5 ticks.
        for (int i = 0; i < WeaponStats.HAND.ticksToCharge() * 3; i++) {
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
    void atLowRiskOnlyAMinorFallAndNotBelowFifteen() {
        DeterministicSoldier healthy = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        healthy.reportHealth(20, 6);
        DeterministicSoldier hurt = new DeterministicSoldier("Bob", new Position(0.5, GROUND, 0.5));
        hurt.reportHealth(16, 6);
        DeterministicSoldier wounded = new DeterministicSoldier("Carol", new Position(0.5, GROUND, 0.5));
        wounded.reportHealth(15, 6);
        Order.MoveTo low = new Order.MoveTo(10.5, GROUND, 0.5, 1, 1, Risk.LOW);

        assertEquals(new FallAllowance(3, Risk.LOW.costPerHealth()), healthy.fallAllowance(low), "3 at most, though 5 would leave 15");
        assertEquals(new FallAllowance(1, Risk.LOW.costPerHealth()), hurt.fallAllowance(low));
        assertEquals(FallAllowance.NONE, wounded.fallAllowance(low), "nothing that leaves less than 15");
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

        assertEquals(new Condition(20, 20, 1, Optional.empty()), alice.status().condition().orElseThrow());
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
    void swimsARiverHeadAboveWaterAndClimbsOutOnTheFarBank() {
        // A river 3 wide at x=2..4, 2 deep, its surface at GROUND-1; banks a block above it.
        WorldView river = new WorldView() {
            private boolean water(int x, int y) {
                return x >= 2 && x <= 4 && y >= GROUND - 2 && y <= GROUND - 1;
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND && !water(x, y);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return water(x, y);
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return water(x, y);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.receiveOrder(new Order.MoveTo(7.5, GROUND, 0.5, 0.5, 1, Risk.LOW));

        double lowest = GROUND;
        for (int i = 0; i < 300 && !(alice.status().report() instanceof Report.InPosition); i++) {
            alice.tick(river);
            lowest = Math.min(lowest, alice.status().position().y());
        }

        assertInstanceOf(Report.InPosition.class, alice.status().report(), "across: " + alice.status().position());
        assertEquals(GROUND, alice.status().position().y(), 1e-9, "on the far bank");
        assertTrue(lowest >= GROUND - 1, "the head stays out: the feet never sink below the top block of water, lowest " + lowest);
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
    @Test
    void withCobblestoneItPillarsUpAWallTooHighToStepUp() {
        // A wall three blocks high from x=3 east, its top at y=67.
        Set<BlockPos> placed = new HashSet<>();
        WorldView cliff = blocks((x, y, z) -> y < GROUND || x >= 3 && y < GROUND + 3 || placed.contains(new BlockPos(x, y, z)));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 16));
        alice.receiveOrder(new Order.MoveTo(5.5, GROUND + 3, 0.5, 0.5, 0.5, Risk.LOW));

        for (int tick = 0; tick < 400; tick++) {
            alice.tick(cliff);
            alice.takePlacement().ifPresent(placed::add); // the server takes it
        }

        assertEquals(new Report.InPosition(), alice.status().report(), "on top: " + alice.status().position());
        // Two blocks up beside the wall, then an ordinary step up onto its top.
        assertEquals(Set.of(new BlockPos(2, GROUND, 0), new BlockPos(2, GROUND + 1, 0)), placed);
    }

    @Test
    void withDirtItBuildsItsWayDownASheerDropRatherThanFall() {
        // On a tower's top at y=74, ten blocks over flat ground; the order is to the ground below.
        Set<BlockPos> placed = new HashSet<>();
        WorldView tower = blocks((x, y, z) -> y < GROUND || x <= 0 && y < GROUND + 10 || placed.contains(new BlockPos(x, y, z)));
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(-0.5, GROUND + 10, 0.5));
        alice.reportKit(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, false, 32, false));
        alice.reportHealth(20, 20);
        alice.receiveOrder(new Order.MoveTo(8.5, GROUND, 0.5, 1, 0.5, Risk.HIGH));

        double resting = alice.status().position().y();
        double previous = resting;
        for (int tick = 0; tick < 2000; tick++) {
            alice.tick(tower);
            alice.takePlacement().ifPresent(placed::add); // the server takes it
            double y = alice.status().position().y();
            if (y == previous) {
                assertTrue(resting - y <= 3 + 1e-9, "fell from " + resting + " to " + y); // three is unhurt
                resting = y;
            }
            previous = y;
        }

        assertEquals(new Report.InPosition(), alice.status().report(), "at the bottom: " + alice.status().position());
        assertTrue(!placed.isEmpty());
    }

    @Test
    void withoutBlocksAWallTooHighToStepUpHasNoRoute() {
        WorldView cliff = blocks((x, y, z) -> y < GROUND || x >= 3 && y < GROUND + 3);
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("wooden_sword"), 0, 0));
        alice.receiveOrder(new Order.MoveTo(5.5, GROUND + 3, 0.5, 0.5, 0.5, Risk.LOW));

        for (int tick = 0; tick < 400; tick++) {
            alice.tick(cliff);
            assertTrue(alice.takePlacement().isEmpty());
        }

        assertTrue(alice.status().report() instanceof Report.Cantco, "" + alice.status().report());
    }

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
        return withHostileAt(hostile, (x, y, z) -> y < GROUND || everythingSolid);
    }

    /** Which cells are solid. */
    private interface Solid {
        boolean at(int x, int y, int z);
    }

    private static WorldView withHostileAt(Position hostile, Solid solid) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return solid.at(x, y, z);
            }

            @Override

            public boolean isHazard(int x, int y, int z) {

                return false;

            }


            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                double distance = Math.sqrt(Math.pow(hostile.x() - center.x(), 2)
                        + Math.pow(hostile.y() - center.y(), 2) + Math.pow(hostile.z() - center.z(), 2));
                return distance <= radius ? List.of(new TrackedEntity(1, 154, hostile)) : List.of();
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
