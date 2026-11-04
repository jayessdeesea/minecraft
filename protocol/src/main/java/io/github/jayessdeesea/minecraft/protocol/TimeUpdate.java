package io.github.jayessdeesea.minecraft.protocol;

import java.util.Map;

/**
 * A Set Time: the world's game time, and any world clocks whose state changed, by their index in
 * the synced {@code minecraft:world_clock} registry - the caller resolves the names from
 * {@link PlaySessionListeners#onRegistry}, the way it names enchantments.
 *
 * <p>The server sends every clock on join and whenever one is set or changes rate; once a second
 * after that it sends the game time alone, with no clocks. A clock between its updates is the
 * client's to advance: by its rate, for each tick of game time since.
 */
public record TimeUpdate(long gameTime, Map<Integer, Clock> clocks) {

    /** One clock: ticks since it started, and how fast it runs (0 stopped, 1 normal). */
    public record Clock(long totalTicks, float rate) {
    }
}
