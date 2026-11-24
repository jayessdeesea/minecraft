package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldierTest.*;

/** The soldier's fight: its shield and its bow, closing and backing off, its sword's sweep, creepers, reach and recharge. */
class DeterministicSoldierCombatTest {

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
        double near = Ballistics.aimPitch(8, 0).orElseThrow();
        double far = Ballistics.aimPitch(30, 0).orElseThrow();
        assertTrue(near < 0 && near > -3, "a little up, a short shot: " + near);
        assertTrue(far < near, "higher for a longer one: " + far);
        assertTrue(Ballistics.aimPitch(8, -6).orElseThrow() > 0, "down, at a mark below");
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

        // In the sweep: struck, but never swept - a knockback hit (bot.Strike).
        assertTrue(alice.tick(bobBeside).isPresent() && alice.takeKnockbackHit(), "Bob is in the sweep: a knockback hit, no sweep");
        for (int charging = 0; charging < 13; charging++) {
            alice.tick(DeterministicSoldierTest.blocks((x, y, z) -> y < GROUND)); // charged again, no hostile near
        }
        assertEquals(OptionalInt.of(1), alice.tick(withHostileAt(zombie, false)), "with nobody beside it, she swings");
        assertFalse(alice.takeKnockbackHit(), "with nobody beside it, she swings - a full swing, its sweep hitting none of ours");
    }

    /**
     * An archer below a ledge is closed on by the walk down to it, not straight at the drop: live (field597), Alice stood
     * 46 s on a ledge 4 over a skeleton 6.4 off, "fights hand to hand", and was shot dead.
     */
    @Test
    void anArcherBelowALedgeIsClosedOnByTheWalkDown() {
        // A ledge at GROUND for x < 3; past it the ground 4 lower, but for a ramp down at z 5..7.
        WorldView ledge = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                int top = x < 3 ? GROUND : z >= 5 && z <= 7 && x <= 6 ? GROUND - (x - 2) : GROUND - 4;
                return y < top;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("skeleton"), new Position(7.5, GROUND - 4, 0.5)));
            }
        };
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(1.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 254, 0, false));
        alice.reportHealth(20, 20);
        boolean struck = false;
        for (int tick = 0; tick < 400 && !struck; tick++) {
            struck = alice.tick(ledge).isPresent();
        }
        assertTrue(struck, "down the ramp and at it: " + alice.status().position());
    }

    /**
     * A creeper at arm's length comes first, a skeleton nearer: it gets clear of the blast, not fights the skeleton. Live
     * (field595), Olga fought a skeleton 0.8 off with a creeper 1.3 off, and the blast took her from 16 health to 2.
     */
    @Test
    void aCreeperAtArmsLengthComesBeforeANearerSkeleton() {
        DeterministicSoldier olga = new DeterministicSoldier("Olga", new Position(0.5, GROUND, 0.5));
        olga.reportKit(new Kit(Optional.of("stone_sword"), 0, 4));
        TrackedEntity skeleton = new TrackedEntity(1, HostileMobRegistry.typeIdOf("skeleton"), new Position(1.3, GROUND, 0.5));
        TrackedEntity creeper = new TrackedEntity(2, HostileMobRegistry.typeIdOf("creeper"), new Position(0.5, GROUND, 2.0));
        WorldView both = new WorldView() {
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
                return List.of(skeleton, creeper);
            }
        };
        Position from = olga.status().position();
        OptionalInt swing = OptionalInt.empty();
        for (int tick = 0; tick < 5; tick++) {
            OptionalInt s = olga.tick(both);
            swing = s.isPresent() ? s : swing;
        }
        Position to = olga.status().position();
        assertTrue(swing.isEmpty() || swing.getAsInt() == 2, "not the skeleton: " + swing);
        assertTrue(Math.hypot(to.x() - 0.5, to.z() - 2.0) > Math.hypot(from.x() - 0.5, from.z() - 2.0), "away from the creeper: " + from + " -> " + to);
    }

    /** Alice beside a zombie, Bob between them - in her sword's sweep for good, four in one cell of a slot as live (field584). */
    private static WorldView packed() {
        Position zombie = new Position(2, 64, 0);
        return new WorldView() {
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
                return List.of(new Position(0, 64, 0), new Position(1.5, 64, 0.5)); // Alice, and Bob between her and it
            }
        };
    }

    /**
     * With a squadmate in the sweep and food to sprint, every hit is a knockback hit at full charge - full damage, never a
     * sweep (bot.Strike; operator, 2026-11-21: the sweep has its place, and it's not always). Live (field584), four in one
     * cell each held fire for the others, and a zombie killed two; the second's hold that answered it then swept them.
     */
    @Test
    void aSquadmateInTheSweepWithFoodToSprintGetsKnockbackHits() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        alice.reportHealth(20, 20);
        WorldView packed = packed();
        int hits = 0;
        for (int tick = 0; tick < 60; tick++) {
            if (alice.tick(packed).isPresent()) {
                hits++;
                assertTrue(alice.takeKnockbackHit(), "hit " + hits + " a knockback hit, not a sweep");
            }
            assertFalse(alice.takeArmSwing(), "no swing alone with a sprint to strike with");
        }
        assertTrue(hits >= 4, "struck at full charge: " + hits + " in 60 ticks");
    }

    /**
     * With a squadmate in the sweep and too hungry to sprint, its charge is started over - a swing alone - then each hit
     * is short of a sweep's charge: never a full one, which would sweep him.
     */
    @Test
    void aSquadmateInTheSweepTooHungryToSprintGetsShortHits() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        alice.reportHealth(20, 4);
        WorldView packed = packed();
        List<Integer> hits = new java.util.ArrayList<>();
        boolean reset = false;
        for (int tick = 0; tick < 60; tick++) {
            if (alice.tick(packed).isPresent()) {
                hits.add(tick);
                assertFalse(alice.takeKnockbackHit(), "no sprint to knock back with");
            }
            reset |= alice.takeArmSwing();
        }
        int cycle = Strike.Weapon.of(Optional.of("stone_sword")).shortTicks();
        assertTrue(reset, "its charge started over first");
        assertTrue(hits.size() >= 3 && hits.get(2) - hits.get(1) == cycle, "short hits every " + cycle + " ticks: " + hits);
    }

    /** A squadmate a block and a half off the target on each axis is in its sweep's box, though 2.1 across: the swing waits. */
    @Test
    void aSquadmateDiagonallyBesideTheTargetIsInItsSweep() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0, 64, 0));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        Position zombie = new Position(3, 64, 0);
        java.util.function.Function<Position, WorldView> bobAt = bob -> new WorldView() {
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
                return List.of(new Position(0, 64, 0), bob).stream().filter(p -> Math.hypot(p.x() - center.x(), p.z() - center.z()) <= radius).toList();
            }
        };

        // In the sweep: struck, but never swept - a knockback hit (bot.Strike).
        assertTrue(alice.tick(bobAt.apply(new Position(4.5, 64, 1.5))).isPresent() && alice.takeKnockbackHit(), "Bob diagonal to the zombie, in its box: a knockback hit, no sweep");
        for (int charging = 0; charging < 13; charging++) {
            alice.tick(DeterministicSoldierTest.blocks((x, y, z) -> y < GROUND)); // charged again, no hostile near
        }
        assertEquals(OptionalInt.of(1), alice.tick(bobAt.apply(new Position(5.5, 64, 2.5))), "Bob past it, she swings");
        assertFalse(alice.takeKnockbackHit(), "Bob past it, she swings - a full swing, its sweep hitting none of ours");
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

        // In the sweep: struck, but never swept - a knockback hit (bot.Strike).
        assertTrue(dana.tick(franUnderIt).isPresent() && dana.takeKnockbackHit(), "Fran stands under the phantom, in the sweep: a knockback hit, no sweep");
        for (int charging = 0; charging < 13; charging++) {
            dana.tick(DeterministicSoldierTest.blocks((x, y, z) -> y < GROUND)); // charged again, no hostile near
        }
        assertEquals(OptionalInt.of(1), dana.tick(withHostileAt(phantom, false)), "with nobody under it, she swings");
        assertFalse(dana.takeKnockbackHit(), "with nobody under it, she swings - a full swing, its sweep hitting none of ours");
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

        // In the sweep: struck, but never swept - a knockback hit (bot.Strike).
        assertTrue(eli.tick(halInHisBoots).isPresent() && eli.takeKnockbackHit(), "Hal is in the sweep, a tenth of a block from Eli: a knockback hit, no sweep");
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

    /**
     * A zombie on a pillar out of reach, no way to it on foot: let be, and said so - its work goes on. Live (field652),
     * Eli's mine stood five minutes "threatened" by a zombie 7.7 off with no way between them, carrying on to no end.
     */
    @Test
    void aHostileWithNoWayToItIsLetBeAndItsWorkGoesOn() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        WorldView pillar = withHostileAt(new Position(4.5, GROUND + 4, 0.5), (x, y, z) -> y < GROUND || x == 4 && z == 0 && y < GROUND + 4);

        alice.tick(pillar);

        assertTrue(alice.lettingBe(), "let be: " + alice.status().report());
        assertFalse(alice.status().report() instanceof Report.InContact, alice.status().report().toString());
    }

    /**
     * One that fights hand to hand, in the air overhead and higher than a sword reaches: no fight, and its order goes on,
     * though the ground rises under it to where a walk would come within a strike of it; come down within reach, it's
     * fought. Live, phantoms circling took soldiers to and fro between task and fight.
     */
    @Test
    void aMeleeHostileInTheAirOverheadOutOfASwordsReachIsLetBeTillItComesDown() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0));
        alice.receiveOrder(new Order.MoveTo(0.5, GROUND, 20.5, 1, 2, Risk.LOW));
        Position[] phantom = {new Position(4.5, GROUND + 5, 0.5)};
        WorldView night = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < (x >= 3 ? GROUND + 2 : x == 2 ? GROUND + 1 : GROUND); // a rise of two, by a step, to the east
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("phantom"), phantom[0]));
            }
        };

        for (int tick = 0; tick < 10; tick++) {
            // Over the rise as it goes, 4 off and 5 up: 3 over the ground under it.
            Position at = alice.status().position();
            phantom[0] = new Position(at.x() + 4, at.y() + 5, at.z());
            alice.tick(night);
            assertFalse(alice.status().report() instanceof Report.InContact, "tick " + tick + ": " + alice.status().report());
            assertTrue(alice.lettingBe(), "tick " + tick + ": seen, and let be");
        }
        assertTrue(alice.status().position().z() > 1, "on with its order: " + alice.status().position());

        Position at = alice.status().position();
        phantom[0] = new Position(at.x() + 1.5, at.y() + 1, at.z());
        alice.tick(night);

        assertEquals(new Report.InContact(1), alice.status().report(), "down within reach: a fight");
    }

    /** A creeper on its route is gone round, not walked past at arm's length (the engagement doctrine). */
    @Test
    void aRouteGoesRoundACreeperOnIt() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        Position creeper = new Position(10.5, GROUND, 0.5);
        WorldView open = withHostileAt(creeper, (x, y, z) -> y < GROUND);
        WorldView withCreeper = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return open.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return Math.hypot(center.x() - creeper.x(), center.z() - creeper.z()) <= radius
                        ? List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("creeper"), creeper)) : List.of();
            }
        };
        alice.receiveOrder(new Order.MoveTo(20.5, GROUND, 0.5, 1, 2, Risk.LOW));

        double closest = Double.MAX_VALUE;
        for (int tick = 0; tick < 400 && alice.status().position().x() < 19; tick++) {
            alice.tick(withCreeper);
            Position at = alice.status().position();
            closest = Math.min(closest, Math.hypot(at.x() - creeper.x(), at.z() - creeper.z()));
        }

        assertTrue(alice.status().position().x() >= 19, "it gets there: " + alice.status().position());
        assertTrue(closest > DeterministicSoldier.CREEPER_CLEAR, "never inside the blast on the way: " + closest);
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

    /**
     * In a one-wide tunnel with room behind her, a creeper that keeps her inside its reach is engaged
     * once the standoff has gone on (the engagement doctrine): live (field320), a miner stood a
     * minute 2.9 blocks from one, backing off the same block.
     */
    @Test
    void aCreeperHoldingHerInATunnelIsEngagedAfterAStandoff() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, 64, 0.5));
        // A tunnel along x, one wide, open both ways; the creeper always 2.9 east of her.
        java.util.concurrent.atomic.AtomicReference<Position> at = new java.util.concurrent.atomic.AtomicReference<>(alice.status().position());
        WorldView tunnel = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || y <= GROUND + 2 && z != 0;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                Position p = at.get();
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("creeper"), new Position(p.x() + 2.9, 64, 0.5)));
            }
        };

        boolean struck = false;
        for (int tick = 0; tick < DeterministicSoldier.CREEPER_STANDOFF_TICKS + 40 && !struck; tick++) {
            struck = alice.tick(tunnel).isPresent();
            at.set(alice.status().position());
            if (tick < DeterministicSoldier.CREEPER_STANDOFF_TICKS - 1) {
                assertFalse(struck, "not before the standoff has gone on, tick " + tick);
            }
        }
        assertTrue(struck, "engaged once it has");
    }

    /** A creeper outside its blast, the soldier carrying a bow: shot, as any far-away enemy (operator, 2026-11-18). */
    @Test
    void aSoldierWithABowShootsACreeperOutsideItsBlast() {
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 0.5));
        alice.reportKit(new Kit(Optional.of("stone_sword"), 16, 0, 0, 0, 0, 0,
                false, false, 0, true));
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
                return List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("creeper"), new Position(8.5, GROUND, 0.5)));
            }
        };

        int loosed = 0;
        for (int tick = 0; tick < DeterministicSoldier.FULL_DRAW_TICKS; tick++) {
            alice.tick(creeper);
            loosed += alice.takeLoose() ? 1 : 0;
        }

        assertEquals(1, loosed, "shot from 8 blocks, outside its blast");
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

    /**
     * A skeleton 8 below in a pit it can't be closed on in, and not shooting: a healthy soldier armed for melee carries on
     * with its order along the rim, but one at 2 health keeps out of its sight - live (field568), Eli at 1 health walked
     * back to his cave edge in a skeleton's line between its shots, "carries on", and was shot dead.
     */
    @Test
    void aHurtSoldierKeepsOutOfAnArchersSightItCantCloseOn() {
        assertTrue(rimWalk(20) > 4, "healthy: on with its order along the rim");
        assertTrue(rimWalk(2) < 2, "hurt: kept out of the archer's sight, not walked on along the rim");
    }

    /** How far along the rim, toward x=6, a soldier at {@code health} gets in two seconds, a skeleton in the pit below. */
    private static double rimWalk(double health) {
        Position skeleton = new Position(7.5, GROUND - 8, 0.5);
        WorldView pit = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                boolean hole = x >= 2 && x <= 12 && Math.abs(z) <= 3 && y >= GROUND - 8 && y < GROUND;
                return y < GROUND && !hole;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return Math.sqrt(Math.pow(skeleton.x() - center.x(), 2) + Math.pow(skeleton.y() - center.y(), 2) + Math.pow(skeleton.z() - center.z(), 2)) <= radius
                        ? List.of(new TrackedEntity(1, HostileMobRegistry.typeIdOf("skeleton"), skeleton)) : List.of();
            }
        };
        DeterministicSoldier alice = new DeterministicSoldier("Alice", new Position(0.5, GROUND, 4.5));
        alice.reportHealth(health, 16);
        alice.reportKit(new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 0, 0, false));
        alice.receiveOrder(new Order.MoveTo(6.5, GROUND, 4.5, 0.5, 1, Risk.LOW));
        for (int tick = 0; tick < 40; tick++) {
            alice.tick(pit);
        }
        return alice.status().position().x() - 0.5;
    }
}
