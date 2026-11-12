package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfterActionReviewTest {

    @Test
    void theReviewComesAtDawnWithTheDaysDeathsAndIdlersThenStartsAgain() {
        AfterActionReview aar = new AfterActionReview();
        Foothold camp = new Foothold(new Position(0, 64, 0), List.of(
                new Foothold.Standard(1, "a patrol base each night", true, "", "PATROL_BASE"),
                new Foothold.Standard(2, "the camp walled in", false, "40 wall blocks left", "FORTIFY")));

        assertEquals(Optional.empty(), aar.time(false, Optional.of(camp)), "the first daylight is no dawn");
        aar.death("Hal", "death.fell.accident.generic");
        aar.death("Dana", "death.fell.accident.generic");
        aar.death("Bob", "death.attack.arrow");
        aar.idle("Gus");
        assertEquals(Optional.empty(), aar.time(true, Optional.of(camp)));
        String review = aar.time(false, Optional.of(camp)).orElseThrow().review();

        assertTrue(review.startsWith("AAR for day 1"), review);
        assertTrue(review.contains("OPEN: the camp walled in - held 0% of the day; now 40 wall blocks left"), review);
        assertTrue(review.contains("casualties: 3") && review.contains("death.fell.accident.generic: Hal, Dana"), review);
        assertTrue(review.contains("idle by day: {Gus=1}"), review);

        aar.time(true, Optional.empty());
        String next = aar.time(false, Optional.empty()).orElseThrow().review();
        assertTrue(next.contains("day 2") && next.contains("casualties: none") && next.contains("idle by day: none"), next);
    }

    @Test
    void aStandardIsScoredByTheShareOfTheDayItHeldNotByTheDawnsOneLook() {
        AfterActionReview aar = new AfterActionReview();
        Foothold lit = camp(true, true);
        Foothold dark = camp(false, true);
        aar.time(true, Optional.empty());
        for (int check = 0; check < 20; check++) {
            aar.measured(check % 2 == 0 ? lit : dark); // lit half the day, walled all of it
        }
        aar.measured(camp(true, false)); // the last check, a wall block knocked out

        Scoreboard.Score score = aar.time(false, Optional.of(camp(true, false)), 0, 8, 0, List.of(), 0).orElseThrow().score();

        assertEquals(1, score.met(), "the wall held 20 of 21 checks, open only at dawn; the light, 11 of 21");
        assertEquals(List.of("phase 2 the camp's ground lit - held 52% of the day"), score.open());
        assertEquals(2, score.phase());
    }

    private static Foothold camp(boolean lit, boolean walled) {
        return new Foothold(new Position(0, 64, 0), List.of(
                new Foothold.Standard(2, "the camp's ground lit", lit, lit ? "" : "20 cells dark", "SECURE"),
                new Foothold.Standard(2, "the camp walled in", walled, walled ? "" : "3 wall blocks left", "FORTIFY")));
    }
}
