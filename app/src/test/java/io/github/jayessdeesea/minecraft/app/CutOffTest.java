package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutOffTest {

    private static SoldierStatus eli(double x, Report report) {
        return new SoldierStatus("Eli", new Position(x, 20, 91.5), new Order.Idle(), report, Optional.empty());
    }

    /** Ten minutes at one spot with a "no route" between - a moment's other report too - is cut off; moved off, not. */
    @Test
    void tenMinutesAtOneSpotWithNoRouteIsCutOff() {
        CutOff cut = new CutOff();
        cut.update(eli(-19.6, new Report.Cantco("no route from (-20, 20, 91) to within 0 of (-19, 20, 97)")), 0);
        cut.update(eli(-19.4, new Report.Moving()), CutOff.MILLIS / 2);
        assertFalse(cut.cutOff("Eli", CutOff.MILLIS / 2));

        assertTrue(cut.cutOff("Eli", CutOff.MILLIS));

        cut.update(eli(-30, new Report.Moving()), CutOff.MILLIS + 1);
        assertFalse(cut.cutOff("Eli", CutOff.MILLIS + 1), "moved off its spot");
    }

    /** Stood still at work, never a "no route", isn't cut off. */
    @Test
    void stoodStillAtWorkIsNotCutOff() {
        CutOff cut = new CutOff();
        cut.update(eli(-19.6, new Report.InPosition()), 0);

        assertFalse(cut.cutOff("Eli", 2 * CutOff.MILLIS));
    }

    /** Seen armed, a moment without - its sword swapped out for blocks - is armed still; a minute on, not. */
    @Test
    void aMomentWithoutItsSwordIsArmedStill() {
        CutOff cut = new CutOff();
        assertTrue(cut.armed("Ned", true, 0));
        assertTrue(cut.armed("Ned", false, 30_000));
        assertFalse(cut.armed("Ned", false, CutOff.ARMED_MILLIS + 1));
        assertFalse(cut.armed("Eve", false, 0), "never seen armed");
    }
}
