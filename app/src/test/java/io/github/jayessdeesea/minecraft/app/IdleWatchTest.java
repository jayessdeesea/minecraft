package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleWatchTest {

    private static final Position HERE = new Position(0, 64, 0);

    @Test
    void aSoldierStoodStillByDayRaisesOneAlarmThenOneWhenItMovesOn() {
        IdleWatch watch = new IdleWatch();

        assertEquals(Optional.empty(), watch.update("Alice", HERE, 0, true, () -> "Idle"));
        assertEquals(Optional.empty(), watch.update("Alice", new Position(1, 64, 1), 119_000, true, () -> "Idle"), "shuffling isn't moving");
        String alarm = watch.update("Alice", HERE, 120_000, true, () -> "order MoveTo, report InPosition").orElseThrow();
        assertTrue(alarm.startsWith("Alice stationary 120 s by day at (0.0, 64.0, 0.0): order MoveTo"), alarm);
        assertEquals(Optional.empty(), watch.update("Alice", HERE, 200_000, true, () -> "Idle"), "once a spell");
        assertEquals(Optional.of("Alice moving again after 210 s"), watch.update("Alice", new Position(5, 64, 0), 210_000, true, () -> ""));
    }

    @Test
    void nightIsNoTimeToBeAtWork() {
        IdleWatch watch = new IdleWatch();

        watch.update("Bob", HERE, 0, false, () -> "");
        assertEquals(Optional.empty(), watch.update("Bob", HERE, 300_000, false, () -> ""));
        assertEquals(Optional.empty(), watch.update("Bob", HERE, 310_000, true, () -> ""), "the day's spell starts at dawn");
        assertTrue(watch.update("Bob", HERE, 430_000, true, () -> "").isPresent());
    }
}
