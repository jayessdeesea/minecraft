package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Report;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandScorecardTest {

    @Test
    void eachTaskAndOrderKindIsScoredByWhatItsSoldiersReportedWithTheSameFailureCountedAsOne() {
        CommandScorecard scorecard = new CommandScorecard();
        for (int s = 0; s < 90; s++) {
            scorecard.sample("FORTIFY", "MoveTo", new Report.Fortifying());
        }
        for (int s = 0; s < 30; s++) {
            scorecard.sample("FORTIFY", "MoveTo", new Report.Cantco("no route from (" + s + ", 64, 0) to within 2 of (8, 64, 0)"));
        }
        scorecard.sample("MINE", "Place", new Report.Building(3, 31));
        scorecard.casualty("MINE", "Place");

        assertEquals(List.of(
                "commands, by soldier-minutes and what the soldiers reported:",
                "  task FORTIFY: 2 min - Fortifying 75%, Cantco 25%",
                "    CANTCO 30 s: no route from # to within # of #",
                "  task MINE: 0 min - Building 100%; 1 killed, 3600.0 a soldier-hour",
                "  order MoveTo: 2 min - Fortifying 75%, Cantco 25%",
                "    CANTCO 30 s: no route from # to within # of #",
                "  order Place: 0 min - Building 100%; 1 killed, 3600.0 a soldier-hour"), scorecard.review());
        assertEquals(List.of("commands, by soldier-minutes and what the soldiers reported:"), scorecard.review(), "the tally starts again");
    }

    @Test
    void aReasonsPlacesAndNumbersAreTakenOut() {
        assertEquals("can't make wooden pickaxe: walk to #: expected standing beside the LOG within # ticks",
                CommandScorecard.normalized("can't make wooden pickaxe: walk to BlockPos[x=-49, y=66, z=47]: expected standing beside the LOG within 400 ticks"));
    }

    /** By area too, each with its losses a soldier-hour beside its time (operator, 2026-11-21: losses priced in). */
    @Test
    void eachAreaSaysItsLossesASoldierHour() {
        CommandScorecard scorecard = new CommandScorecard();
        for (int second = 0; second < 1800; second++) {
            scorecard.sample("MINE", "Mine", new io.github.jayessdeesea.minecraft.command.Report.Moving(), "below ground");
        }
        scorecard.casualty("MINE", "Mine", "below ground");
        java.util.List<String> review = scorecard.review();
        assertTrue(review.stream().anyMatch(l -> l.startsWith("  area below ground: 30 min") && l.endsWith("1 killed, 2.0 a soldier-hour")),
                review.toString());
    }
}
