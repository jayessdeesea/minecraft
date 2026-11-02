package io.github.jayessdeesea.minecraft.protocol;

import java.util.Map;

/**
 * The server's world clocks (Set Time), by their index in the synced {@code minecraft:world_clock}
 * registry - the caller resolves the names from {@link PlaySessionListeners#onRegistry}, the way it
 * names enchantments. Sent on join and then every second.
 */
public record TimeUpdate(Map<Integer, Clock> clocks) {

    /** One clock: ticks since it started, and how fast it runs (0 stopped, 1 normal). */
    public record Clock(long totalTicks, float rate) {
    }
}
