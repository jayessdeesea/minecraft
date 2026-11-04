package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The overworld's time of day, followed from Set Time updates. The server sends the overworld clock
 * only on join and when it changes; between those, each once-a-second update carries the game time
 * alone, and the clock has advanced by its rate for every tick of game time since it was last sent
 * ({@link TimeUpdate}). One connection's updates only: game times from two connections agree, but
 * nothing needs both.
 */
final class OverworldClock {

    private static final String OVERWORLD = "minecraft:overworld";

    /** The clock as last sent: its ticks and rate, at that game time. */
    private record Sent(long ticks, float rate, long gameTime) {
    }

    private Sent sent;

    /**
     * The time of day as of {@code time}, its clocks named through this session's synced registry -
     * empty until the overworld clock has been sent once.
     */
    synchronized Optional<TimeOfDay> update(TimeUpdate time, List<String> clockNames) {
        for (Map.Entry<Integer, TimeUpdate.Clock> clock : time.clocks().entrySet()) {
            int id = clock.getKey();
            if (id < 0 || id >= clockNames.size()) {
                throw new IllegalStateException("world clock " + id + " isn't in the synced registry of " + clockNames.size());
            }
            if (clockNames.get(id).equals(OVERWORLD)) {
                sent = new Sent(clock.getValue().totalTicks(), clock.getValue().rate(), time.gameTime());
            }
        }
        if (sent == null) {
            return Optional.empty();
        }
        long ticks = sent.ticks() + Math.round(sent.rate() * (time.gameTime() - sent.gameTime()));
        return Optional.of(TimeOfDay.of(ticks, sent.rate() > 0));
    }
}
