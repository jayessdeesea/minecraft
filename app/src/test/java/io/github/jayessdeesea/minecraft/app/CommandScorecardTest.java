package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Report;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "  task MINE: 0 min - Building 100%; 1 killed",
                "  order MoveTo: 2 min - Fortifying 75%, Cantco 25%",
                "    CANTCO 30 s: no route from # to within # of #",
                "  order Place: 0 min - Building 100%; 1 killed"), scorecard.review());
        assertEquals(List.of("commands, by soldier-minutes and what the soldiers reported:"), scorecard.review(), "the tally starts again");
    }

    @Test
    void aReasonsPlacesAndNumbersAreTakenOut() {
        assertEquals("can't make wooden pickaxe: walk to #: expected standing beside the LOG within # ticks",
                CommandScorecard.normalized("can't make wooden pickaxe: walk to BlockPos[x=-49, y=66, z=47]: expected standing beside the LOG within 400 ticks"));
    }
}
