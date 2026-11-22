package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seconds stood doing nothing, charged to the soldier under orders and to the chain of command without (operator, 2026-11-21). */
class StoodStillTest {

    @Test
    void eachSecondPastThePauseIsTheSoldiersOrTheChainOfCommands() {
        StoodStill tally = new StoodStill();
        long now = 0;
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int second = 0; second <= StoodStill.SUMMARY_MILLIS / 1000; second++, now += 1000) {
            long still = second * 1000L;
            // Ann stood under orders all along; Bob held without work; Cal paused 5 s at a time.
            tally.second("Ann", "Alpha", still, 0, now).ifPresent(lines::add);
            tally.second("Bob", "Bravo", 0, still, now).ifPresent(lines::add);
            tally.second("Cal", "Alpha", still % 5000, 0, now).ifPresent(lines::add);
        }
        // Said on Ann's second at ten minutes: her 591 seconds past the pause, Bob's 590 before his.
        assertEquals(java.util.List.of("over 10 min, 591 under orders - the soldiers' (Ann 591); 590 without work - the chain of command's (Bravo 590)"), lines);
        assertTrue(tally.review().getFirst().contains("Ann 591"), "and the day's at dawn");
    }

    /**
     * Asked many times a second - the tick loop catching up after a stall - a soldier is still counted a second a second:
     * live (field664), soldiers were charged 1086 s stood still in ten minutes.
     */
    @Test
    void aSecondIsCountedOnceHoweverOftenItsAsked() {
        StoodStill tally = new StoodStill();
        java.util.List<String> lines = new java.util.ArrayList<>();
        long now = 0;
        for (int second = 0; second <= StoodStill.SUMMARY_MILLIS / 1000; second++, now += 1000) {
            for (int burst = 0; burst < 3; burst++) {
                tally.second("Ann", "Alpha", 60_000, 0, now + burst * 100).ifPresent(lines::add);
            }
        }
        assertEquals(java.util.List.of("over 10 min, 601 under orders - the soldiers' (Ann 601); 0 without work - the chain of command's"), lines);
    }
}
