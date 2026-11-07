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
            return List.copyOf(game);
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

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
