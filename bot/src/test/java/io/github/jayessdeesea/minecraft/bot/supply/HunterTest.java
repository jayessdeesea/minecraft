package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hungry soldier and a cow: chase, strike when charged, and stand where it fell for the meat. */
class HunterTest {

    private static final int COW = 30;

    private final List<TrackedEntity> game = new ArrayList<>();
    /** Where the force keeps the animals it brought in: within 16 of it one is stock. */
    private final List<Position> stockKept = new ArrayList<>();

    private final WorldView field = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y < 64;
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> gameNear(Position center, double radius) {
            return game.stream().filter(e -> Math.hypot(e.position().x() - center.x(), e.position().z() - center.z()) <= radius).toList();
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }

        @Override
        public boolean stockAt(Position at) {
            return stockKept.stream().anyMatch(p -> Math.hypot(p.x() - at.x(), p.z() - at.z()) <= 16);
        }
    };

    /**
     * An animal the force brought in is stock, not game (operator, 2026-11-25: scouting brings animals back): in the
     * squad simulation a squad led two sheep to its camp and, the mission over, killed both for wool. Starving or
     * not, it isn't seen as game and isn't chased; one off that ground still is.
     */
    @Test
    void anAnimalTheForceBroughtInIsStockAndNeverHunted() {
        game.add(new TrackedEntity(7, COW, new Position(5.5, 64, 0.5)));
        stockKept.add(new Position(8.5, 64, 4.5));
        Position camp = new Position(0.5, 64, 0.5);
        Hunter hunter = new Hunter("Alice");

        assertFalse(Hunter.gameInSight(field, camp, Hunter.STARVING_FOOD, kind -> true), "no game in sight");
        hunter.tick(camp, camp, Hunter.STARVING_RADIUS, kind -> true, field, true, 10);
        assertFalse(hunter.active(), "not chased");

        game.add(new TrackedEntity(8, COW, new Position(-20.5, 64, 0.5)));
        assertTrue(Hunter.gameInSight(field, camp, Hunter.STARVING_FOOD, kind -> true), "one off the stock's ground is game");
        hunter.tick(camp, camp, Hunter.STARVING_RADIUS, kind -> true, field, true, 10);
        assertEquals(java.util.Optional.of(new Position(-20.5, 64, 0.5)), hunter.standAt(), "and it's the one chased");
    }

    @Test
    void aStarvingSoldierLooksTwiceAsFarFromItsHuntingGround() {
        game.add(new TrackedEntity(7, COW, new Position(70.5, 64, 0.5)));
        Position camp = new Position(0.5, 64, 0.5);

        assertFalse(Hunter.gameInSight(field, camp, 10, kind -> true), "hungry: the camp's ground is hunted out");
        assertTrue(Hunter.gameInSight(field, camp, Hunter.STARVING_FOOD, kind -> true), "starving: further out");
        assertFalse(Hunter.gameInSight(field, camp, Hunter.STARVING_FOOD, kind -> kind != COW), "only the kinds wanted");
    }

    @Test
    void anAnimalInACaveUnderTheGroundIsNoGame() {
        game.add(new TrackedEntity(7, COW, new Position(5.5, 21, 0.5)));
        Position camp = new Position(0.5, 56, 0.5);
        Hunter hunter = new Hunter("Alice");

        assertFalse(Hunter.gameInSight(field, camp, Hunter.STARVING_FOOD, kind -> true));
        hunter.tick(camp, camp, Hunter.STARVING_RADIUS, kind -> true, field, true, 10);
        assertFalse(hunter.active(), "not chased");
    }

    /**
     * One animal, one hunter (docs/echelons/tenets.md, "work in parallel"): live, eight soldiers chased
     * one animal and built steps toward it. Two sharing claims, one cow - one hunts it; with a second
     * cow, the other hunts that one.
     */
    @Test
    void oneAnimalIsOneHuntersAndTheNextGoesToTheNext() {
        game.add(new TrackedEntity(7, COW, new Position(10.5, 64, 0.5)));
        java.util.Map<Integer, String> claims = new java.util.concurrent.ConcurrentHashMap<>();
        Hunter alice = new Hunter("Alice", claims), bob = new Hunter("Bob", claims);
        Position at = new Position(0.5, 64, 0.5);

        alice.tick(at, at, Hunter.SEARCH_RADIUS, kind -> true, field, true, 10);
        bob.tick(at, at, Hunter.SEARCH_RADIUS, kind -> true, field, true, 10);

        assertTrue(alice.active(), "Alice has the cow");
        assertFalse(bob.active(), "Bob leaves it to her");
        game.add(new TrackedEntity(8, COW, new Position(-10.5, 64, 0.5)));
        bob.tick(at, at, Hunter.SEARCH_RADIUS, kind -> true, field, true, 10);
        assertTrue(bob.active(), "the second cow is Bob's");
    }

    @Test
    void aChaseGivenUpLeavesThatAnimalForAWhile() {
        game.add(new TrackedEntity(7, COW, new Position(10.5, 64, 0.5)));
        Hunter hunter = new Hunter("Alice");
        Position stuck = new Position(0.5, 64, 0.5); // never gets closer

        for (int tick = 0; tick <= Hunter.HUNT_TICKS + 1; tick++) {
            hunter.tick(stuck, stuck, Hunter.SEARCH_RADIUS, kind -> true, field, true, 10);
        }
        assertFalse(hunter.active(), "gave up");
        hunter.tick(stuck, stuck, Hunter.SEARCH_RADIUS, kind -> true, field, true, 10);
        assertFalse(hunter.active(), "and doesn't take it up again at once");
    }

    @Test
    void chasesStrikesWhenChargedAndStandsWhereItFell() {
        game.add(new TrackedEntity(7, COW, new Position(10.5, 64, 0.5)));
        Hunter hunter = new Hunter("Alice");
        Position soldier = new Position(0.5, 64, 0.5);

        assertEquals(OptionalInt.empty(), hunter.tick(soldier, field, true, 12), "out of reach: walk to it first");
        assertEquals(new Position(10.5, 64, 0.5), hunter.standAt().orElseThrow());

        soldier = new Position(9, 64, 0.5);
        assertEquals(OptionalInt.of(7), hunter.tick(soldier, field, true, 12));
        assertEquals(OptionalInt.empty(), hunter.tick(soldier, field, true, 12), "not charged again yet");

        game.clear(); // killed
        hunter.tick(soldier, field, true, 12);
        assertTrue(hunter.active(), "stands where it fell, for the meat");
        assertEquals(new Position(10.5, 64, 0.5), hunter.standAt().orElseThrow());
        for (int tick = 0; tick < Hunter.PICKUP_TICKS + 1; tick++) {
            hunter.tick(soldier, field, true, 12);
        }
        assertFalse(hunter.active());
    }

    @Test
    void aSoldierNotFreeToHuntLetsItGo() {
        game.add(new TrackedEntity(7, COW, new Position(2.5, 64, 0.5)));
        Hunter hunter = new Hunter("Alice");
        hunter.tick(new Position(0.5, 64, 0.5), field, true, 12);

        assertEquals(OptionalInt.empty(), hunter.tick(new Position(0.5, 64, 0.5), field, false, 12));
        assertFalse(hunter.active());
    }
}
