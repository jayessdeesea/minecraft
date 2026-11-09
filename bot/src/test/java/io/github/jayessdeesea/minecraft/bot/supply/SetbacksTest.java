package io.github.jayessdeesea.minecraft.bot.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SetbacksTest {

    private final Setbacks<String> setbacks = new Setbacks<>(100, 400);

    @Test
    void eachFailureOnTheSameTargetLeavesItAloneTwiceAsLongUpToTheMost() {
        setbacks.failed("cell", 0);
        assertTrue(setbacks.barred("cell", 99));
        assertFalse(setbacks.barred("cell", 100));

        setbacks.failed("cell", 100);
        assertTrue(setbacks.barred("cell", 299));
        assertFalse(setbacks.barred("cell", 300));

        setbacks.failed("cell", 300);
        setbacks.failed("cell", 300);
        assertTrue(setbacks.barred("cell", 699), "capped at the most");
        assertFalse(setbacks.barred("cell", 700));
        assertEquals(4, setbacks.failures("cell"));
    }

    @Test
    void aTargetThatWorksIsForgotten() {
        setbacks.failed("cell", 0);
        setbacks.failed("cell", 100);
        setbacks.worked("cell");
        setbacks.failed("cell", 1000);

        assertFalse(setbacks.barred("cell", 1100), "back to the first wait");
        assertEquals(1, setbacks.failures("cell"));
    }

    @Test
    void threeFailuresInARowOnAnythingAskForHelp() {
        setbacks.failed("a", 0, "no route");
        setbacks.failed("b", 0, "no route");
        assertEquals(java.util.Optional.empty(), setbacks.stuck(), "another way still to try");

        setbacks.failed("c", 0, "the chest's contents never came");

        assertEquals(java.util.Optional.of("3 tries in a row failed, the last at c: the chest's contents never came"), setbacks.stuck());
    }

    @Test
    void oneThatWorksOrWorkGoneEndsTheRun() {
        for (String t : List.of("a", "b", "c")) {
            setbacks.failed(t, 0, "no route");
        }
        setbacks.worked("d");
        assertEquals(java.util.Optional.empty(), setbacks.stuck());

        for (String t : List.of("a", "b", "c")) {
            setbacks.failed(t, 0, "no route");
        }
        setbacks.noWork();
        assertEquals(java.util.Optional.empty(), setbacks.stuck());
    }

    @Test
    void theOthersAreStillTried() {
        setbacks.failed("cell", 0);

        assertFalse(setbacks.barred("other", 0));
        assertEquals(Set.of("cell"), setbacks.barredNow(50));
        assertEquals(Set.of(), setbacks.barredNow(100));
    }
}
