package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OverworldClockTest {

    private static final List<String> CLOCKS = List.of("minecraft:the_end", "minecraft:overworld");

    @Test
    void theClockAdvancesWithGameTimeBetweenTheUpdatesThatCarryIt() {
        OverworldClock clock = new OverworldClock();

        assertEquals(Optional.of(new TimeOfDay(6000, true)),
                clock.update(new TimeUpdate(500_000, Map.of(1, new TimeUpdate.Clock(6000, 1f))), CLOCKS));
        // Every second after: the game time alone, as the server sends it.
        assertEquals(Optional.of(new TimeOfDay(6020, true)), clock.update(new TimeUpdate(500_020, Map.of()), CLOCKS));
        assertEquals(Optional.of(new TimeOfDay(13200, true)), clock.update(new TimeUpdate(507_200, Map.of()), CLOCKS),
                "dark: it was 6000 at sunrise-plus-6000, 7200 ticks ago");
        assertEquals(Optional.of(new TimeOfDay(200, true)), clock.update(new TimeUpdate(518_200, Map.of()), CLOCKS),
                "the next morning");
    }

    @Test
    void aStoppedClockStaysPut() {
        OverworldClock clock = new OverworldClock();
        clock.update(new TimeUpdate(100, Map.of(1, new TimeUpdate.Clock(18000, 0f))), CLOCKS);

        assertEquals(Optional.of(new TimeOfDay(18000, false)), clock.update(new TimeUpdate(5000, Map.of()), CLOCKS));
    }

    @Test
    void nothingUntilTheClockIsSentAndAnUnknownClockFailsLoudly() {
        OverworldClock clock = new OverworldClock();

        assertEquals(Optional.empty(), clock.update(new TimeUpdate(100, Map.of()), CLOCKS));
        assertThrows(IllegalStateException.class,
                () -> clock.update(new TimeUpdate(100, Map.of(7, new TimeUpdate.Clock(0, 1f))), CLOCKS));
    }
}
