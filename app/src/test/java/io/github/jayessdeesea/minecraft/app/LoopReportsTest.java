package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.ActivityTrack;
import io.github.jayessdeesea.minecraft.bot.SoldierActivity;
import io.github.jayessdeesea.minecraft.command.Report;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A soldier's own report of a loop: to and fro between two activities, or the same block over and over. */
class LoopReportsTest {

    /** One cell broken three times in ten minutes is counted as that - and not with another cell's, or after the window. */
    @Test
    void theSameCellOverAndOverIsCounted() {
        LoopReports loops = new LoopReports();
        assertEquals(1, loops.cell("Dana", "breaks", 43, 67, 57, "CLEARER", 0));
        assertEquals(1, loops.cell("Dana", "puts", 43, 67, 57, "route step", 1_000), "putting it is another thing than breaking it");
        assertEquals(1, loops.cell("Dana", "breaks", 44, 67, 57, "CLEARER", 2_000), "another cell");
        assertEquals(2, loops.cell("Dana", "breaks", 43, 67, 57, "CLEARER", 60_000));
        assertEquals(LoopReports.SAME_CELL, loops.cell("Dana", "breaks", 43, 67, 57, "CLEARER", 120_000), "a loop");
        assertEquals(1, loops.cell("Dana", "breaks", 43, 67, 57, "CLEARER", 120_000 + LoopReports.CELL_MILLIS + 1), "the old ones out of the window");
    }

    /** To and fro between two activities, each held under ten seconds: a loop at the eighth return inside five minutes. */
    @Test
    void toAndFroBetweenTwoActivitiesIsALoopAtTheEighthReturn() {
        ActivityTrack track = new ActivityTrack();
        SoldierActivity[] two = {SoldierActivity.of(new Report.Moving(), Optional.empty()), SoldierActivity.of(new Report.InContact(1), Optional.empty())};
        long now = 0;
        track.observe(now, two[0], Optional.empty());
        int changes = 0;
        ActivityTrack.Change last = null;
        while ((last == null || !last.loops()) && changes < 40) {
            now += 2_000;
            changes++;
            last = track.observe(now, two[changes % 2], Optional.empty()).orElseThrow();
        }
        assertTrue(last.loops() && last.returned(), "a loop said: " + last);
        assertEquals(ActivityTrack.LOOP_RETURNS, last.returnsLately(), "at the eighth return");
        // Held long enough, a change is a piece of work, no return - and no loop.
        ActivityTrack.Change work = track.observe(now + ActivityTrack.RETURN_MILLIS + 1, two[(changes + 1) % 2], Optional.empty()).orElseThrow();
        assertFalse(work.loops(), "a piece of work: " + work);
    }
}
