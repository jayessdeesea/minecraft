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
     * Level by level: each builder is cleared to a step under the highest top in and beside its
     * strip - the spire's own strip and its neighbour held up there, the strip beyond on its own
     * ground - and is told again only when its floor moves.
     */
    @Test
    void eachBuilderIsClearedAStepUnderTheHighestTopBesideIt() {
        Foreman foreman = new Foreman();
        foreman.heard("A", new Foreman.Top(103));
        foreman.heard("B", new Foreman.Top(70));
        foreman.heard("C", new Foreman.Top(70));

        Map<String, Foreman.Clear> first = foreman.decide(strips());

        assertEquals(new Foreman.Clear(102), first.get("A"));
        assertEquals(new Foreman.Clear(102), first.get("B"), "beside the spire: held to a step under it");
        assertEquals(new Foreman.Clear(69), first.get("C"), "two strips off: its own ground");
        assertEquals(Map.of(), foreman.decide(strips()), "said once: nothing new to say");

        foreman.heard("A", new Foreman.Top(70));
        assertEquals(Map.of("A", new Foreman.Clear(69), "B", new Foreman.Clear(69)), foreman.decide(strips()),
                "the spire down: both cleared on down");
    }

    /**
     * A spire inside a strip, away from its edges, holds back no one but its own builder: its
     * neighbour is held only to the edge it faces. Live, held to its neighbours' highest ground
     * anywhere, every builder but one stood waiting.
     */
    @Test
    void aSpireInsideAStripHoldsBackOnlyItsOwnBuilder() {
        Foreman foreman = new Foreman();
        // A's top, 103, in its middle; its east edge, facing B, at 70.
        foreman.heard("A", new Foreman.Top(103, 70, 70, 70, 70));
        foreman.heard("B", new Foreman.Top(70));
        foreman.heard("C", new Foreman.Top(70));

        Map<String, Foreman.Clear> clear = foreman.decide(strips());

        assertEquals(new Foreman.Clear(102), clear.get("A"));
        assertEquals(new Foreman.Clear(69), clear.get("B"), "held only to A's edge facing it");
    }

    @Test
    void itsLinesWithEdgesAreReadAsTheyreSaid() {
        Foreman.Top top = new Foreman.Top(89, 85, Foreman.NONE, 89, 84);
        assertEquals("KEEP TOP 89 EDGES 85 NONE 89 84", top.text());
        assertEquals(Optional.of(top), Foreman.parse(top.text()));
    }

    /** A builder beside one that hasn't said its top waits on it: that ground isn't known. */
    @Test
    void aBuilderBesideOneNotHeardFromWaits() {
        Foreman foreman = new Foreman();
        foreman.heard("C", new Foreman.Top(70));

        Map<String, Foreman.Clear> clear = foreman.decide(strips());

        assertTrue(!clear.containsKey("C"), "B, beside it, hasn't spoken");
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
