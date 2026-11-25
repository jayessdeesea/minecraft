package io.github.jayessdeesea.minecraft.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which chunks the simulated server has sent one soldier, and which it sends next: the square of chunks in view round
 * the one it stands in, each once - on joining, and those that come into view as it crosses a chunk line. Ground never
 * sent is unknown to a soldier, and unknown ground reads as solid: without these a soldier sent past its spawn's
 * chunks stops at their edge with no route, for good. None is unloaded behind it (backlog/sim-served-chunks.md).
 */
final class SimChunks {

    private final Set<Long> sent = new HashSet<>();
    private long standsIn = Long.MIN_VALUE;

    /** The chunks, as {x, z}, within {@code view} chunks of the one block ({@code x}, {@code z}) is in that this soldier hasn't been sent: now sent. */
    List<int[]> comeIntoView(double x, double z, int view) {
        int cx = Math.floorDiv((int) Math.floor(x), 16), cz = Math.floorDiv((int) Math.floor(z), 16);
        if (key(cx, cz) == standsIn) {
            return List.of();
        }
        standsIn = key(cx, cz);
        List<int[]> come = new ArrayList<>();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                if (sent.add(key(cx + dx, cz + dz))) {
                    come.add(new int[]{cx + dx, cz + dz});
                }
            }
        }
        return come;
    }

    private static long key(int cx, int cz) {
        return (long) cx << 32 | cz & 0xFFFFFFFFL;
    }
}
