package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

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
        assertFalse(TimeOfDay.of(TimeOfDay.DAWN, true).dark());
    }

    @Test
    void timeUntilDarkAndLight() {
        assertEquals(TimeOfDay.DUSK - 1000, TimeOfDay.of(1000, true).ticksUntilDark());
        assertEquals(24000 - 23000 + TimeOfDay.DUSK, TimeOfDay.of(23000, true).ticksUntilDark());
        assertEquals(0, TimeOfDay.of(18000, true).ticksUntilDark());
        assertEquals(TimeOfDay.DAWN - 18000, TimeOfDay.of(18000, true).ticksUntilLight());
    }

    @Test
    void aStoppedClockNeverChanges() {
        assertEquals(Long.MAX_VALUE, TimeOfDay.of(6000, false).ticksUntilDark());
        assertEquals(Long.MAX_VALUE, TimeOfDay.of(18000, false).ticksUntilLight());
    }

    @Test
    void outOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TimeOfDay(24000, true));
    }
}
