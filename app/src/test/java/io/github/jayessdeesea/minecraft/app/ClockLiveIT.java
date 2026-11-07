package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The time of day the chain of command would be told, as the app works it out from the server's
 * Set Time updates ({@link OverworldClock}) - printed, to check against {@code time query day} on
 * the console. Needs the server (DEVELOPMENT.md § 1).
 */
class ClockLiveIT {

    @Test
    void timeOfDayAsTheAppSeesIt() throws Exception {
        OverworldClock clock = new OverworldClock();
        AtomicReference<List<String>> clockNames = new AtomicReference<>(List.of());
        AtomicReference<TimeOfDay> seen = new AtomicReference<>();
        PlaySession session = MinecraftClient.join("localhost", 25565, "Clock", new PlaySessionListeners(
                update -> { }, unload -> { }, position -> { }, entity -> { }, move -> { }, ids -> { }, health -> { }, inventory -> { },
                chat -> { }, container -> { }, (registry, entries) -> {
                    if (registry.equals("minecraft:world_clock")) {
                        clockNames.set(List.copyOf(entries));
                    }
                }, blocks -> { }, time -> clock.update(time, clockNames.get()).ifPresent(seen::set), cause -> { }))
                .get(15, TimeUnit.SECONDS);
        try {
            for (int i = 0; i < 40 && seen.get() == null; i++) {
                TimeUnit.MILLISECONDS.sleep(250);
            }
            ChainScenarioLiveIT.console("time query day");
            System.out.printf("%nThe app sees: %s (ticks %d, dark %b)%n", seen.get(), seen.get().ticks(), seen.get().dark());
            assertTrue(seen.get() != null);
        } finally {
            session.close();
        }
    }
}
