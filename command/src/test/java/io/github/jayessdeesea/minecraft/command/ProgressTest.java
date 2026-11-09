package io.github.jayessdeesea.minecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProgressTest {

    private static SoldierStatus soldier(String name, Report report) {
        return new SoldierStatus(name, new Position(0, 64, 0), new Order.Idle(), report, Optional.empty());
    }

    @Test
    void aTaskTheWorldShowsDoneIsDoneWhateverTheSoldiersSay() {
        Progress p = Progress.of(Optional.of("the stairs reach y=16"), Optional.empty(),
                List.of(soldier("a", new Report.Mining(3, 40))), 0);
        assertEquals(Progress.State.DONE, p.state());
        assertEquals("the stairs reach y=16", p.detail());
    }

    @Test
    void aSoldierAtWorkKeepsItUnderWayWithWhatsLeft() {
        Progress p = Progress.of(Optional.empty(), Optional.of("51 of 170 wall blocks to place"),
                List.of(soldier("a", new Report.Fortifying()), soldier("b", new Report.InPosition())), 600_000);
        assertEquals(new Progress(Progress.State.UNDER_WAY, "51 of 170 wall blocks to place"), p);
    }

    @Test
    void nobodyWorkingForAMinuteIsStalledWithTheReasonsCountedMostFirst() {
        Progress p = Progress.of(Optional.empty(), Optional.of("51 of 170 wall blocks to place"), List.of(
                soldier("a", new Report.Cantco("nothing left to gather")),
                soldier("b", new Report.Cantco("no route")),
                soldier("c", new Report.Cantco("nothing left to gather")),
                soldier("d", new Report.InPosition())), 180_000);
        assertEquals(Progress.State.STALLED, p.state());
        assertEquals("nobody has worked at it for 3 min; 2 of 4 report CANTCO: nothing left to gather; 1 of 4 report CANTCO: no route;"
                + " 1 of 4 stand with nothing to do; still 51 of 170 wall blocks to place", p.detail());
    }

    @Test
    void aMomentsPauseIsNotAStall() {
        Progress p = Progress.of(Optional.empty(), Optional.empty(), List.of(soldier("a", new Report.InPosition())), 20_000);
        assertEquals(Progress.State.UNDER_WAY, p.state());
        assertTrue(Progress.working(new Report.Moving()));
    }
}
