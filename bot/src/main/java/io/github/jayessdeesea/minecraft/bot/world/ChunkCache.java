package io.github.jayessdeesea.minecraft.bot.world;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The terrain half of {@link WorldModel} - a shared, reference-counted chunk cache, per
 * docs/architecture.md's \"Behavior modeling\" section: the block data is one shared ground truth for every
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
     * Bumped after every change to the cache - a chunk loaded, let go or written: a lookup's memo
     * of its thread's last chunk holds only while it's unchanged. Read before the memo, it's also
     * the happens-before edge a block written in place is seen across threads by.
     */
    private final java.util.concurrent.atomic.AtomicInteger version = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * The chunks last looked up, a slot each by their key's low bits, with the {@link #version} each
     * was looked up at. Live (commit 480's profile), the map's {@code get} was 47% of the garrison's
     * CPU: every block a path search reads looked its chunk up again, and nearly all are the chunk of
     * the block before. A slot holds an immutable record, so a thread reads one whole or not at all;
     * threads on different chunks keep different slots. Kept per thread instead (commit 512's
     * profile), the thread-local lookup was itself 21% of the CPU.
     */
    private final Memo[] memo = new Memo[MEMO_SLOTS];
    private static final int MEMO_SLOTS = 256;

    private record Memo(long chunk, int version, LoadedChunk loaded) {
    }

    /**
     * Records {@code owner} as holding a reference to chunk ({@code chunkX}, {@code chunkZ}) with
     * the given per-section block-state arrays (bottom section first). A chunk another owner holds
     * already keeps its blocks: that owner's block updates have kept them current, and the
     * connections are separate streams - a chunk one was sent before an update can arrive after
     * another's update and undo it. A soldier that respawns lets its chunks go first
     * ({@link #removeOwner}), so none is kept on the word of an owner that no longer sees it.
     */
    public Divergence load(int chunkX, int chunkZ, List<int[]> sectionBlockStates, Object owner) {
        Divergence[] diverged = {Divergence.NONE};
        chunks.compute(chunkKey(chunkX, chunkZ), (key, existing) -> {
            if (existing != null) {
                diverged[0] = Divergence.of(chunkX, chunkZ, existing.sectionBlockStates, sectionBlockStates);
            }
            LoadedChunk chunk = existing != null ? existing : new LoadedChunk(sectionBlockStates);
            chunk.owners.add(owner);
            return chunk;
        });
        version.incrementAndGet();
        return diverged[0];
    }

    /**
     * Where a chunk sent again differs from the one kept: how many blocks, and the first few - each
     * {x, y, z, kept state, sent state}. The world model's check on itself: a kept chunk its owners'
     * block updates have kept current differs only by an update still on its way to the new owner;
     * one that differs for long has gone stale (backlog/world-model-stale.md).
     */
    public record Divergence(int blocks, List<int[]> samples) {
        static final Divergence NONE = new Divergence(0, List.of());
        static final int SAMPLES = 5;

        public Divergence {
            samples = List.copyOf(samples);
        }

        static Divergence of(int chunkX, int chunkZ, List<int[]> kept, List<int[]> sent) {
            int blocks = 0;
            List<int[]> samples = new java.util.ArrayList<>();
            for (int section = 0; section < Math.min(kept.size(), sent.size()); section++) {
                int[] a = kept.get(section);
                int[] b = sent.get(section);
                for (int i = 0; i < Math.min(a.length, b.length); i++) {
                    if (a[i] != b[i]) {
                        blocks++;
                        if (samples.size() < SAMPLES) {
                            samples.add(new int[]{chunkX * SECTION_SIZE + (i & 15), (section + MIN_SECTION_Y) * SECTION_SIZE + (i >>> 8),
                                    chunkZ * SECTION_SIZE + (i >>> 4 & 15), a[i], b[i]});
                        }
                    }
                }
            }
            return blocks == 0 ? NONE : new Divergence(blocks, samples);
        }
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
        version.incrementAndGet();
    }

    /** Drops {@code owner}'s view entirely - for a connection that died without unloading what it saw. */
    public void removeOwner(Object owner) {
        for (Long key : chunks.keySet()) {
            chunks.computeIfPresent(key, (k, chunk) -> {
                chunk.owners.remove(owner);
                return chunk.owners.isEmpty() ? null : chunk;
            });
        }
        version.incrementAndGet();
    }

    /** Whether the block here is solid itself, the cell over a fence not counted ({@link WorldView#solidHere}). */
    public boolean solidHere(int x, int y, int z) {
        BlockClassRegistry.BlockClass here = classAt(x, y, z);
        return here != BlockClassRegistry.BlockClass.PASSABLE && here != BlockClassRegistry.BlockClass.HAZARD;
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

    /** Whether the block at (x, y, z) is a fence gate, open or shut. False for a block nobody has cached. */
    public boolean isGate(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.isGate(stateId);
    }

    /** Whether the block at (x, y, z) is a chest. False for a block nobody has cached. */
    public boolean isChest(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.isChest(stateId);
    }

    /** The workstation at (x, y, z), if there is one. Empty for a block nobody has cached. */
    public Optional<Workstation> workstation(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId == UNKNOWN ? Optional.empty() : BlockClassRegistry.workstation(stateId);
    }

    /** Whether the block is a log - unknown blocks aren't. */
    public boolean isLog(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.LOG;
    }

    /** Whether the block is a stalagmite's tip - unknown blocks aren't. */
    public boolean isStalagmite(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.isStalagmite(stateId);
    }

    /** Whether the block is cobblestone, plain or mossy - unknown blocks aren't. */
    public boolean isCobblestone(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.isCobblestone(stateId);
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

    /** Whether the block is a water source - unknown blocks aren't. */
    public boolean isWaterSource(int x, int y, int z) {
        return BlockClassRegistry.isWaterSource(rawStateIdAt(x, y, z));
    }

    /** What digging the block takes, if it's one a soldier digs - unknown blocks aren't. */
    public Optional<Hardness> hardness(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId == UNKNOWN ? Optional.empty() : BlockClassRegistry.hardness(stateId);
    }

    /** The tree whose leaves the block is - unknown blocks aren't any. */
    public Optional<String> leaves(int x, int y, int z) {
        return BlockClassRegistry.leaves(rawStateIdAt(x, y, z));
    }

    /** The tree the block's sapling grows into - unknown blocks aren't any. */
    public Optional<String> sapling(int x, int y, int z) {
        return BlockClassRegistry.sapling(rawStateIdAt(x, y, z));
    }

    /** What ore the block is - unknown blocks are none. */
    public Optional<String> oreOf(int x, int y, int z) {
        return BlockClassRegistry.oreOf(rawStateIdAt(x, y, z));
    }

    /** Whether column ({@code x}, {@code z})'s chunk is loaded by any owner. */
    public boolean isLoaded(int x, int z) {
        return chunks.containsKey(chunkKey(Math.floorDiv(x, SECTION_SIZE), Math.floorDiv(z, SECTION_SIZE)));
    }

    /** Whether the block is gravel - unknown blocks aren't. */
    public boolean isGravel(int x, int y, int z) {
        return BlockClassRegistry.isGravel(rawStateIdAt(x, y, z));
    }

    /** Whether the block falls - grit: sand, gravel. Unknown blocks don't. */
    public boolean falls(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.GRIT;
    }

    /** Whether the block is ore of any kind - unknown blocks aren't. */
    public boolean isOre(int x, int y, int z) {
        return BlockClassRegistry.isOre(rawStateIdAt(x, y, z));
    }

    /** Whether the block is water, source or flowing - unknown blocks aren't. */
    public boolean isWater(int x, int y, int z) {
        return BlockClassRegistry.isWater(rawStateIdAt(x, y, z));
    }

    /** Whether a hoe tills the block into farmland - unknown blocks aren't. */
    public boolean isTillable(int x, int y, int z) {
        return BlockClassRegistry.isTillable(rawStateIdAt(x, y, z));
    }

    /** Whether the block is short or tall grass - unknown blocks aren't. */
    public boolean isGrassPlant(int x, int y, int z) {
        return BlockClassRegistry.isGrassPlant(rawStateIdAt(x, y, z));
    }

    /** Whether the block is farmland - unknown blocks aren't. */
    public boolean isFarmland(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId != UNKNOWN && BlockClassRegistry.classOf(stateId) == BlockClassRegistry.BlockClass.FARMLAND;
    }

    /** The wheat's growth stage, if the block is wheat. */
    public OptionalInt wheatAge(int x, int y, int z) {
        return BlockClassRegistry.wheatAge(rawStateIdAt(x, y, z));
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
        standIns.remove(cellKey(x, y, z));
        write(x, y, z, stateId);
    }

    /**
     * How long a stand-in stands unconfirmed: the server's update for a block a soldier placed
     * comes within a tick or two, so one still unconfirmed after this was never placed.
     */
    public static final long STAND_IN_MILLIS = 2_000;

    /** A block the client wrote itself, before the server said so: what the cell held, and when the word is overdue. */
    private record StandIn(int x, int y, int z, int before, long until) {
    }

    private final ConcurrentHashMap<Long, StandIn> standIns = new ConcurrentHashMap<>();

    /**
     * A block the soldier has just placed at ({@code x}, {@code y}, {@code z}) stands in the model as
     * {@code stateId} before the server's update says what landed there, so the soldier lands on its
     * step at once - but only over a cell that's open, never over a block the model already holds
     * (the simulation's server answers a placement at once, and a dirt step would read as stone for
     * good), and only until {@link #STAND_IN_MILLIS} from {@code now}: a placement the server refused
     * is never confirmed, and the stand-in is taken back ({@link #expireStandIns}). Live, a step the
     * server wouldn't place held as cobblestone in the model for hours, the top of a keep builder's
     * strip, and every strip beside it waited on a block nobody could dig. Whether it stood.
     */
    public boolean standIn(int x, int y, int z, int stateId, long now) {
        if (isSolid(x, y, z)) {
            return false;
        }
        standIns.put(cellKey(x, y, z), new StandIn(x, y, z, rawStateIdAt(x, y, z), now + STAND_IN_MILLIS));
        write(x, y, z, stateId);
        return true;
    }

    /** Takes back every stand-in the server hasn't confirmed by {@code now}: the cells are as they were. How many. */
    public int expireStandIns(long now) {
        int taken = 0;
        for (StandIn s : standIns.values()) {
            if (s.until() <= now && standIns.remove(cellKey(s.x(), s.y(), s.z()), s)) {
                write(s.x(), s.y(), s.z(), s.before());
                taken++;
            }
        }
        return taken;
    }

    private static long cellKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y + 2048) & 0xFFF;
    }

    private void write(int x, int y, int z, int stateId) {
        chunks.computeIfPresent(chunkKey(Math.floorDiv(x, SECTION_SIZE), Math.floorDiv(z, SECTION_SIZE)), (key, chunk) -> {
            int sectionIndex = Math.floorDiv(y, SECTION_SIZE) - MIN_SECTION_Y;
            if (sectionIndex >= 0 && sectionIndex < chunk.sectionBlockStates.size()) {
                int index = (Math.floorMod(y, SECTION_SIZE) << 8) | (Math.floorMod(z, SECTION_SIZE) << 4) | Math.floorMod(x, SECTION_SIZE);
                chunk.sectionBlockStates.get(sectionIndex)[index] = stateId;
                chunk.writes.incrementAndGet(sectionIndex); // after the block: a scan begun before it reads stale
            }
            return chunk;
        });
        version.incrementAndGet();
    }

    /**
     * Whether every block of the section holding ({@code x}, {@code y}, {@code z}) is passable -
     * the dig's scan of a site skips the open sky in one step. Commit 608's profile: the squad
     * thread's scan of the flatten site, 33 by 129 columns up to y=319, was a third of its time,
     * most of it reading air. Worked out once a section and kept, against the section's count of
     * block writes: a write since makes it worked out again.
     */
    public boolean openSection(int x, int y, int z) {
        LoadedChunk chunk = chunks.get(chunkKey(Math.floorDiv(x, SECTION_SIZE), Math.floorDiv(z, SECTION_SIZE)));
        if (chunk == null) {
            return false;
        }
        int i = Math.floorDiv(y, SECTION_SIZE) - MIN_SECTION_Y;
        if (i < 0 || i >= chunk.sectionBlockStates.size()) {
            return false;
        }
        int writes = chunk.writes.get(i);
        long kept = chunk.open.get(i);
        if (kept != 0 && (int) (kept >>> 2) == writes) {
            return (kept & 1) == 1;
        }
        boolean open = true;
        for (int stateId : chunk.sectionBlockStates.get(i)) {
            if (BlockClassRegistry.classOf(stateId) != BlockClassRegistry.BlockClass.PASSABLE) {
                open = false;
                break;
            }
        }
        // Bit 1 says it's worked out; the writes it was worked out against above it.
        chunk.open.set(i, ((long) writes << 2) | 2 | (open ? 1 : 0));
        return open;
    }

    /**
     * The raw global block-state ID at ({@code x}, {@code y}, {@code z}), or empty if unknown -
     * observability/debugging only, not used by {@link #isSolid}. Useful for checking
     * {@link BlockClassRegistry}'s classification against real data without guessing.
     */
    /**
     * The block's name at (x, y, z) and its class - "wildflowers P" - or "unknown" for a block nobody
     * has cached: what's said of a block that refused a step or stalled a soldier, so the next round of
     * fixes knows what stopped it (operator, 2026-11-16).
     */
    public String describeAt(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId == UNKNOWN ? "unknown" : BlockClassRegistry.nameOf(stateId).orElse("state " + stateId) + " "
                + BlockClassRegistry.classOf(stateId);
    }

    public OptionalInt stateIdAt(int x, int y, int z) {
        int stateId = rawStateIdAt(x, y, z);
        return stateId == UNKNOWN ? OptionalInt.empty() : OptionalInt.of(stateId);
    }

    private int rawStateIdAt(int x, int y, int z) {
        int chunkX = Math.floorDiv(x, SECTION_SIZE), chunkZ = Math.floorDiv(z, SECTION_SIZE);
        long packed = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        int now = version.get();
        long key = chunkKey(chunkX, chunkZ);
        int slot = (int) key & (MEMO_SLOTS - 1);
        Memo last = memo[slot];
        LoadedChunk chunk;
        if (last != null && last.chunk() == packed && last.version() == now) {
            chunk = last.loaded();
        } else {
            chunk = chunks.get(key);
            memo[slot] = new Memo(packed, now, chunk);
        }
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

    /**
     * The map key for chunk ({@code chunkX}, {@code chunkZ}): the two packed, then mixed (the
     * SplitMix64 finaliser, a bijection, so no two chunks share a key). Packed alone, a {@code Long}
     * hashes to {@code chunkX ^ chunkZ}, a few dozen values over the chunks round the origin: live,
     * with sixteen soldiers' views loaded, every block lookup walked a collision tree, and that was
     * 72% of the garrison's CPU.
     */
    static long chunkKey(int chunkX, int chunkZ) {
        long k = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        k = (k ^ (k >>> 30)) * 0xBF58476D1CE4E5B9L;
        k = (k ^ (k >>> 27)) * 0x94D049BB133111EBL;
        return k ^ (k >>> 31);
    }

    private static final class LoadedChunk {
        final List<int[]> sectionBlockStates;
        // Only ever mutated inside a ConcurrentHashMap#compute remapping function for this chunk's
        // key, which runs with exclusive access to that key's entry - a plain HashSet is safe here,
        // it doesn't need its own concurrency control on top of that.
        final Set<Object> owners = new HashSet<>();

        /** Block writes into each section, and whether each is open as last worked out (ChunkCache#openSection). */
        final java.util.concurrent.atomic.AtomicIntegerArray writes;
        final java.util.concurrent.atomic.AtomicLongArray open;

        LoadedChunk(List<int[]> sectionBlockStates) {
            this.sectionBlockStates = sectionBlockStates;
            this.writes = new java.util.concurrent.atomic.AtomicIntegerArray(sectionBlockStates.size());
            this.open = new java.util.concurrent.atomic.AtomicLongArray(sectionBlockStates.size());
        }
    }
}
