package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeOfDayTest {

    @Test
    void theClockWrapsIntoDays() {
        assertEquals(new TimeOfDay(6000, true), TimeOfDay.of(3 * 24000 + 6000, true));
    }

    @Test
    void darkRunsFromDuskToDawn() {
        assertFalse(TimeOfDay.of(TimeOfDay.DUSK - 1, true).dark());
        assertTrue(TimeOfDay.of(TimeOfDay.DUSK, true).dark());
        assertTrue(TimeOfDay.of(18000, true).dark());
        assertTrue(TimeOfDay.of(23000, true).dark(), "no spawning by now, but what spawned walks till sunrise");
        assertFalse(TimeOfDay.of(0, true).dark());
    }

    @Test
    void timeUntilDarkAndLight() {
        assertEquals(TimeOfDay.DUSK - 1000, TimeOfDay.of(1000, true).ticksUntilDark());
        assertEquals(0, TimeOfDay.of(23000, true).ticksUntilDark());
        assertEquals(0, TimeOfDay.of(18000, true).ticksUntilDark());
        assertEquals(TimeOfDay.DAWN - 18000, TimeOfDay.of(18000, true).ticksUntilLight());
    }

    @Test
    void aStoppedClockNeverChanges() {
        assertEquals(Long.MAX_VALUE, TimeOfDay.of(6000, false).ticksUntilDark());
        assertEquals(Long.MAX_VALUE, TimeOfDay.of(18000, false).ticksUntilLight());
    }

    @Test
    void theNextDawnAndDuskAndTheClock() {
        TimeOfDay noon = TimeOfDay.of(6000, true);
        assertEquals(18000, noon.ticksToDawn());
        assertEquals(TimeOfDay.DUSK - 6000, noon.ticksToDusk());
        assertEquals(LocalTime.of(12, 0), noon.clock());
        TimeOfDay midnight = TimeOfDay.of(18000, true);
        assertEquals(6000, midnight.ticksToDawn());
        assertEquals(6000 + TimeOfDay.DUSK, midnight.ticksToDusk(), "tomorrow's, while it's dark");
        assertEquals(LocalTime.of(0, 0), midnight.clock());
        assertEquals(LocalTime.of(6, 0), TimeOfDay.of(0, true).clock());
        assertEquals(LocalTime.of(5, 59), TimeOfDay.of(23999, true).clock());
    }

    @Test
    void outOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TimeOfDay(24000, true));
    }
}
