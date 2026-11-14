package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForemanTest {

    /** Three strips across x: A beside B beside C. */
    private static Map<String, Box> strips() {
        Map<String, Box> strips = new LinkedHashMap<>();
        strips.put("A", new Box(0, 64, 0, 4, 319, 11));
        strips.put("B", new Box(5, 64, 0, 9, 319, 11));
        strips.put("C", new Box(10, 64, 0, 15, 319, 11));
        return strips;
    }

    /**
     * Lock step (operator, 2026-11-14): the site's level is the highest top any builder says, and
     * every builder is cleared to it and no lower - the strip with the spire digs that level, the
     * rest wait - and told again only when the level moves. It falls a level at a time as the tops do.
     */
    @Test
    void everyBuilderIsClearedToTheSitesHighestLevelAndNoLower() {
        Foreman foreman = new Foreman();
        foreman.heard("A", new Foreman.Top(103));
        foreman.heard("B", new Foreman.Top(70));
        foreman.heard("C", new Foreman.Top(70));

        Map<String, Foreman.Clear> first = foreman.decide(strips());

        assertEquals(Map.of("A", new Foreman.Clear(103), "B", new Foreman.Clear(103), "C", new Foreman.Clear(103)), first,
                "one level for the site: only A has ground at it");
        assertEquals(Map.of(), foreman.decide(strips()), "said once: nothing new to say");

        foreman.heard("A", new Foreman.Top(102));
        assertEquals(new Foreman.Clear(102), foreman.decide(strips()).get("C"), "A's level done: the site's level falls one");
        foreman.heard("A", new Foreman.Top(70));
        assertEquals(Map.of("A", new Foreman.Clear(70), "B", new Foreman.Clear(70), "C", new Foreman.Clear(70)), foreman.decide(strips()),
                "the spire down: all three on the same level");
    }

    /**
     * A block put into a strip already done - a neighbour's step, a pillar left - is that strip's top
     * again: the level goes back up to it, and it's cleared before anyone goes down.
     */
    @Test
    void aBlockPutIntoAStripAlreadyDoneIsClearedBeforeAnyoneGoesDown() {
        Foreman foreman = new Foreman();
        foreman.heard("A", new Foreman.Top(70));
        foreman.heard("B", new Foreman.Top(70));
        foreman.heard("C", new Foreman.Top(70));
        foreman.decide(strips());

        foreman.heard("B", new Foreman.Top(72));

        assertEquals(Map.of("A", new Foreman.Clear(72), "B", new Foreman.Clear(72), "C", new Foreman.Clear(72)), foreman.decide(strips()));
    }

    /** A strip with nothing left is told nothing, and holds nobody; never under the slab's bottom. */
    @Test
    void aStripWithNothingLeftHoldsNobodyAndTheFloorStopsAtTheSlab() {
        Foreman foreman = new Foreman();
        foreman.heard("A", new Foreman.Top(Foreman.NONE));
        foreman.heard("B", new Foreman.Top(Keep.BOTTOM));
        foreman.heard("C", new Foreman.Top(Foreman.NONE));

        assertEquals(Map.of("B", new Foreman.Clear(Keep.BOTTOM)), foreman.decide(strips()));
    }

    @Test
    void itsLinesWithEdgesAreReadAsTheyreSaid() {
        Foreman.Top top = new Foreman.Top(89, 85, Foreman.NONE, 89, 84);
        assertEquals("KEEP TOP 89 EDGES 85 NONE 89 84", top.text());
        assertEquals(Optional.of(top), Foreman.parse(top.text()));
    }

    /** Nothing is decided until every builder has said its top: one not heard from may hold the highest ground. */
    @Test
    void nobodyIsClearedUntilEveryBuilderHasSpoken() {
        Foreman foreman = new Foreman();
        foreman.heard("A", new Foreman.Top(70));
        foreman.heard("C", new Foreman.Top(70));

        assertEquals(Map.of(), foreman.decide(strips()), "B hasn't spoken");
    }

    @Test
    void itsLinesAreReadAsTheyreSaid() {
        assertEquals(Optional.of(new Foreman.Top(88)), Foreman.parse(new Foreman.Top(88).text()));
        assertEquals(Optional.of(new Foreman.Top(Foreman.NONE)), Foreman.parse("KEEP TOP NONE"));
        assertEquals(Optional.of(new Foreman.Clear(87)), Foreman.parse("KEEP CLEAR TO 87"));
        assertEquals(Optional.empty(), Foreman.parse("hello"));
    }

    @Test
    void aBuilderSaysItsTopWhenItChangesAndAgainNowAndThen() {
        Foreman.Reporter reporter = new Foreman.Reporter();
        assertEquals(Optional.of(new Foreman.Top(90)), reporter.report(90, 0));
        assertEquals(Optional.empty(), reporter.report(90, 1_000));
        assertEquals(Optional.of(new Foreman.Top(89)), reporter.report(89, 2_000));
        assertEquals(Optional.of(new Foreman.Top(89)), reporter.report(89, 2_000 + Foreman.REPEAT_MILLIS), "a lost line made up");
    }
}
