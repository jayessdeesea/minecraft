package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleWatchTest {

    private static final Position HERE = new Position(0, 64, 0);

    @Test
    void aSoldierStoodStillRaisesOneAlertThenOneWhenItMovesOn() {
        IdleWatch watch = new IdleWatch();

        assertEquals(Optional.empty(), watch.update("Alice", HERE, 0, false, () -> "Idle"));
        assertEquals(Optional.empty(), watch.update("Alice", new Position(1, 64, 1), 59_000, false, () -> "Idle"), "shuffling isn't moving");
        String alert = watch.update("Alice", HERE, 60_000, false, () -> "order MoveTo, report InPosition").orElseThrow();
        assertTrue(alert.startsWith("Alice stationary 60 s at (0.0, 64.0, 0.0): order MoveTo"), alert);
        assertEquals(Optional.empty(), watch.update("Alice", HERE, 200_000, false, () -> "Idle"), "once a spell");
        assertEquals(Optional.of("Alice moving again after 210 s"), watch.update("Alice", new Position(5, 64, 0), 210_000, false, () -> ""));
    }

    @Test
    void jumpingInPlaceIsStandingStill() {
        IdleWatch watch = new IdleWatch();

        watch.update("Alice", HERE, 0, false, () -> "");
        watch.update("Alice", new Position(0, 65.2, 0), 30_000, false, () -> "");
        assertTrue(watch.update("Alice", HERE, 60_000, false, () -> "").isPresent(), "up and down a block isn't moving");
    }

    /** Holding where its squad leader sent it is the order; stood 60 blocks short of there, it isn't. */
    @Test
    void holdingWhereSentMeansAtTheGoalNotShortOfIt() {
        io.github.jayessdeesea.minecraft.command.Order.MoveTo there = new io.github.jayessdeesea.minecraft.command.Order.MoveTo(0, 78, 0, 3, 3,
                io.github.jayessdeesea.minecraft.command.Risk.LOW);
        assertTrue(IdleWatch.holdingWhereSent(there, new Position(2.5, 79, -1.5)));
        assertTrue(!IdleWatch.holdingWhereSent(there, new Position(-39.7, 31, 42.5)), "waist-deep in gravel 60 blocks off");
        assertTrue(!IdleWatch.holdingWhereSent(new io.github.jayessdeesea.minecraft.command.Order.Idle(), new Position(0.5, 78, 0.5)));
    }

    @Test
    void aSquadHidingOrSleepingInItsPatrolBaseIsNoAlert() {
        IdleWatch watch = new IdleWatch();

        watch.update("Bob", HERE, 0, true, () -> "");
        assertEquals(Optional.empty(), watch.update("Bob", HERE, 300_000, true, () -> ""));
        assertEquals(Optional.empty(), watch.update("Bob", HERE, 310_000, false, () -> ""), "the spell starts when it leaves");
        assertTrue(watch.update("Bob", HERE, 370_000, false, () -> "").isPresent());
    }
}
