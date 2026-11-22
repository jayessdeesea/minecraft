package io.github.jayessdeesea.minecraft.command;

import java.util.Objects;
import java.util.Optional;

/**
 * What the platoon leader's chunk register holds of one chunk (operator, 2026-11-21: the platoon leader tracks what is
 * in each chunk and how mature it is): keyed by the chunk's x and z, what the force has made of it, at what level, and
 * its stage as the world last measured it - a wheat chunk's from SURVEYED to fenced, a tree chunk's from GROUND to
 * FENCED, flattened ground's blocks left. Measured, never asked of a model.
 *
 * @param chunkX     the chunk's x, block x / 16
 * @param chunkZ     the chunk's z, block z / 16
 * @param kind       what the force has made of it
 * @param level      the level its ground is made to: the floor's top, or a template's ground
 * @param stage      its stage as last measured, said as its template says it; empty before the first measure
 * @param measuredAt when it was last measured, in the clock's millis
 */
public record ChunkRecord(int chunkX, int chunkZ, Kind kind, int level, Optional<String> stage, long measuredAt) {

    /** What the force has made of a chunk. */
    public enum Kind { FLATTENED, FLOORED, WHEAT, TREES, SUPPLY_POINT }

    public ChunkRecord {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stage, "stage");
    }
}
