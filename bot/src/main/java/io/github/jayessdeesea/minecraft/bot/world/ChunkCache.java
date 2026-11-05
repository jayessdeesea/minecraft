package io.github.jayessdeesea.minecraft.bot.world;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The terrain half of {@link WorldModel} - a shared, reference-counted chunk cache, per
 * AGENTS.md's "Behavior modeling" section: the block data is one shared ground truth for every
 * soldier, but chunk *visibility* is per-connection, so a chunk is evicted only once every
 * connection that loaded it has also unloaded it, not on the first unload. {@link
 * ConcurrentHashMap#compute} gives atomic, per-chunk-key read-modify-write for that bookkeeping
 * directly - {@link #isSolid} (called every tick, from every soldier) stays a lock-free {@code
 * get()}.
 *
 * <p>Deliberately decoupled from {@code protocol}'s packet types - {@code load}/{@code unload}
 * take plain ints and {@code List<int[]>}, not a {@code ChunkDataPacket}, so this class has no
 * dependency on the {@code protocol} module at all. {@code app} is the glue: it bridges {@code
 * MinecraftClient.join}'s
 * {@code ChunkUpdate} listener into {@link #load} for each connected Soldier.
 *
 * <p>{@code isSolid}/{@code isHazard} are backed by {@link BlockClassRegistry}, a classification
 * generated from the server's own data. A tall block (fence, wall: 1.5-high collision) reports the
 * cell above it as solid too - the simplest model that stops anything treating a fence as a
 * one-block step.
 */
public final class ChunkCache {

    private static final int SECTION_SIZE = 16;

    /** Overworld only for now - see class javadoc on why this isn't a constructor parameter yet. */
    private static final int MIN_SECTION_Y = Math.floorDiv(-64, SECTION_SIZE);

    /** Sentinel for "no data at this coordinate" - never a real block-state ID. */
    private static final int UNKNOWN = Integer.MIN_VALUE;

    private final ConcurrentHashMap<Long, LoadedChunk> chunks = new ConcurrentHashMap<>();

    /**
     * Records {@code owner} as holding a reference to chunk ({@code chunkX}, {@code chunkZ}) with
     * the given per-section block-state arrays (bottom section first). If the chunk is already
     * cached, only the ownership is recorded - the existing block data is kept as-is, since nothing
     * decodes chunk-update packets yet to know it might be stale.
     */
    public void load(int chunkX, int chunkZ, List<int[]> sectionBlockStates, Object owner) {
        chunks.compute(chunkKey(chunkX, chunkZ), (key, existing) -> {
            LoadedChunk chunk = existing != null ? existing : new LoadedChunk(sectionBlockStates);
            chunk.owners.add(owner);
            return chunk;
        });
    }

    /** Removes {@code owner}'s reference; the chunk is evicted once no owner remains. */
    public void unload(int chunkX, int chunkZ, Object owner) {
        chunks.compute(chunkKey(chunkX, chunkZ), (key, existing) -> {
            if (existing == null) {
                return null;
            }
            existing.owners.remove(owner);
            return existing.owners.isEmpty() ? null : existing;
        });
    }

    /** Drops {@code owner}'s view entirely - for a connection that died without unloading what it saw. */
    public void removeOwner(Object owner) {
        for (Long key : chunks.keySet()) {
            chunks.computeIfPresent(key, (k, chunk) -> {
                chunk.owners.remove(owner);
                return chunk.owners.isEmpty() ? null : chunk;
            });
        }
    }

    public boolean isSolid(int x, int y, int z) {
        BlockClassRegistry.BlockClass here = classAt(x, y, z);
        // Every class but these two has collision - so a class added later is solid unless it says otherwise.
        return here != BlockClassRegistry.BlockClass.PASSABLE && here != BlockClassRegistry.BlockClass.HAZARD
                || classAt(x, y - 1, z) == BlockClassRegistry.BlockClass.TALL;
    }

    /** Whether the block at (x, y, z) is a torch. False for a block nobody has cached. */
    public boolean isTorch(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.isTorch(stateId);
    }

    /** The workstation at (x, y, z), if there is one. Empty for a block nobody has cached. */
    public java.util.Optional<Workstation> workstation(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId == UNKNOWN ? java.util.Optional.empty() : BlockClassRegistry.workstation(stateId);
    }

    /** Whether the block is a log - unknown blocks aren't. */
    public boolean isLog(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.LOG;
    }

    /** Whether the block is plain stone - unknown blocks aren't. */
    public boolean isStone(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.STONE;
    }

    /** Whether the block is iron ore - unknown blocks aren't. */
    public boolean isIronOre(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.IRON_ORE;
    }

    /** Whether the block is soil, dug by hand - unknown blocks aren't. */
    public boolean isSoil(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.SOIL;
    }

    public boolean isHazard(int x, int y, int z) {
        return classAt(x, y, z) == BlockClassRegistry.BlockClass.HAZARD;
    }

    private BlockClassRegistry.BlockClass classAt(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        // Unknown (unloaded chunk, or outside the decoded vertical range) is treated as solid, not
        // walkable, until proven otherwise - same policy as an unrecognized block state.
        return stateId == UNKNOWN ? BlockClassRegistry.BlockClass.SOLID : BlockClassRegistry.classOf(stateId);
    }

    /** Number of distinct chunks currently cached - observability only, not used by any query. */
    public int loadedChunkCount() {
        return chunks.size();
    }

    /**
     * One block in a cached chunk became {@code stateId} - a block broken, placed, or grown. A
     * block in a chunk nobody has cached is ignored: there's nothing to keep current, and the chunk
     * arrives whole when it loads. Written inside the chunk's {@code compute} like loading; the
     * tick loops' lock-free reads see it at their next read (an int write is atomic).
     */
    public void setBlock(int x, int y, int z, int stateId) {
        chunks.computeIfPresent(chunkKey(Math.floorDiv(x, SECTION_SIZE), Math.floorDiv(z, SECTION_SIZE)), (key, chunk) -> {
            int sectionIndex = Math.floorDiv(y, SECTION_SIZE) - MIN_SECTION_Y;
            if (sectionIndex >= 0 && sectionIndex < chunk.sectionBlockStates.size()) {
                int index = (Math.floorMod(y, SECTION_SIZE) << 8) | (Math.floorMod(z, SECTION_SIZE) << 4) | Math.floorMod(x, SECTION_SIZE);
                chunk.sectionBlockStates.get(sectionIndex)[index] = stateId;
            }
            return chunk;
        });
    }

    /**
     * The raw global block-state ID at ({@code x}, {@code y}, {@code z}), or empty if unknown -
     * observability/debugging only, not used by {@link #isSolid}. Useful for checking
     * {@link BlockClassRegistry}'s classification against real data without guessing.
     */
    public OptionalInt stateIdAt(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId == UNKNOWN ? OptionalInt.empty() : OptionalInt.of(stateId);
    }

    private int rawStateIdAt(int x, int y, int z) {
        LoadedChunk chunk = chunks.get(chunkKey(Math.floorDiv(x, SECTION_SIZE), Math.floorDiv(z, SECTION_SIZE)));
        if (chunk == null) {
            return UNKNOWN;
        }

        int sectionIndex = Math.floorDiv(y, SECTION_SIZE) - MIN_SECTION_Y;
        if (sectionIndex < 0 || sectionIndex >= chunk.sectionBlockStates.size()) {
            return UNKNOWN;
        }

        int localX = Math.floorMod(x, SECTION_SIZE);
        int localY = Math.floorMod(y, SECTION_SIZE);
        int localZ = Math.floorMod(z, SECTION_SIZE);
        int index = (localY << 8) | (localZ << 4) | localX;
        return chunk.sectionBlockStates.get(sectionIndex)[index];
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static final class LoadedChunk {
        final List<int[]> sectionBlockStates;
        // Only ever mutated inside a ConcurrentHashMap#compute remapping function for this chunk's
        // key, which runs with exclusive access to that key's entry - a plain HashSet is safe here,
        // it doesn't need its own concurrency control on top of that.
        final Set<Object> owners = new HashSet<>();

        LoadedChunk(List<int[]> sectionBlockStates) {
            this.sectionBlockStates = sectionBlockStates;
        }
    }
}
