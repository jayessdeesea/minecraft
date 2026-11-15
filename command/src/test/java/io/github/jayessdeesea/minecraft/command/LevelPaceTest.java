package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelPaceTest {

    @Test
    void aLevelsBestIsItsSlowestBuildersBest() {
        LevelPace pace = new LevelPace(70, 20_000, Map.of(
                "Alice", new LevelPace.Tally(10, 40, 160, 200, 10, 90, 20, 100),
                "Bob", new LevelPace.Tally(5, 20, 80, 100, 5, 45, 10, 50)));

        assertEquals(10_000, pace.bestMillis(), "Alice's 200 ticks");
        assertEquals(0.5, pace.share(), 1e-9);
        assertTrue(pace.slow());
        assertTrue(pace.summary().contains("BELOW 90%"), pace.summary());
        assertTrue(pace.summary().contains("Alice 10 digs: break 90/40, walk 200/160, hand 10, ack 20, pickup 100, not digging 0"), pace.summary());
    }

    @Test
    void aLevelAtItsBestIsNotLookedInto() {
        LevelPace pace = new LevelPace(70, 10_000, Map.of("Alice", new LevelPace.Tally(10, 40, 160, 160, 0, 40, 0, 0)));

        assertEquals(1.0, pace.share(), 1e-9);
        assertFalse(pace.slow());
    }
}
