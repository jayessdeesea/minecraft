package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelPaceTest {

    /**
     * The best is the work shared evenly over the crew: Alice's 200 ticks and Bob's 100 split two
     * ways is 150 ticks each, 7.5 s - the uneven split's 10 s is shown beside it, a loss of its own.
     */
    @Test
    void aLevelsBestIsItsWorkSharedEvenlyAndItsSplitIsShownBesideIt() {
        LevelPace pace = new LevelPace(70, 20_000, Map.of(
                "Alice", new LevelPace.Tally(10, 40, 160, 200, 10, 90, 20, 100),
                "Bob", new LevelPace.Tally(5, 20, 80, 100, 5, 45, 10, 50)));

        assertEquals(7_500, pace.bestMillis());
        assertEquals(10_000, pace.splitMillis(), "Alice's 200 ticks");
        assertEquals(0.375, pace.share(), 1e-9);
        assertTrue(pace.slow());
        assertTrue(pace.summary().contains("BELOW 90%"), pace.summary());
        assertTrue(pace.summary().contains("Alice 10 digs: break 90/40, walk 200/160, hand 10, ack 20, pickup 100, not digging 0"), pace.summary());
    }

    /** A builder on the crew with no digs still shares the work: three on it, two dug. */
    @Test
    void aBuilderWhoWaitedCountsInTheCrew() {
        LevelPace pace = new LevelPace(70, 10_000, Map.of(
                "Alice", new LevelPace.Tally(10, 40, 160, 200, 10, 90, 20, 100),
                "Bob", new LevelPace.Tally(5, 20, 80, 100, 5, 45, 10, 50)), 3, Map.of());

        assertEquals(5_000, pace.bestMillis());
    }

    @Test
    void aLevelAtItsBestIsNotLookedInto() {
        LevelPace pace = new LevelPace(70, 10_000, Map.of("Alice", new LevelPace.Tally(10, 40, 160, 160, 0, 40, 0, 0)));

        assertEquals(1.0, pace.share(), 1e-9);
        assertFalse(pace.slow());
    }

    /** Where the crew's ticks went, the most first, and the run so far over its levels. */
    @Test
    void theCrewsTicksAreAccountedForAndTheRunAddsUp() {
        LevelPace one = new LevelPace(70, 10_000, Map.of("Alice", new LevelPace.Tally(10, 40, 160, 200, 0, 0, 0, 0)), 2,
                Map.of("Alice", Map.of("clearer", 150, "depositor", 50), "Bob", Map.of("waiting", 200)));
        LevelPace two = new LevelPace(69, 10_000, Map.of("Bob", new LevelPace.Tally(10, 40, 160, 200, 0, 0, 0, 0)), 2,
                Map.of("Alice", Map.of("waiting", 200), "Bob", Map.of("clearer", 200)));

        assertTrue(one.summary().contains("waiting 50%, clearer 38%, depositor 13%"), one.summary());
        LevelPace.Run run = LevelPace.Run.NONE.plus(one).plus(two);
        assertEquals(2, run.levels());
        assertEquals(0.5, run.share(), 1e-9);
        assertTrue(run.summary().contains("BELOW 90%"), run.summary());
        assertTrue(run.summary().contains("waiting 50%, clearer 44%, depositor 6%"), run.summary());
    }
}
