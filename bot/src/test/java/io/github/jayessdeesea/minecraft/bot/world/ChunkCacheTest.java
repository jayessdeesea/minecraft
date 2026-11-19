package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkCacheTest {

    /**
     * The chunks round the origin - sixteen soldiers' views - get distinct keys and spread hashes:
     * packed alone they hashed to chunkX ^ chunkZ, a few dozen values, and lookups walked collision
     * trees. In a table of 4,096 buckets, no bucket holds more than a handful.
     */
    @Test
    void chunksRoundTheOriginSpreadAcrossTheMap() {
        java.util.Set<Long> keys = new java.util.HashSet<>();
        int[] buckets = new int[4_096];
        for (int x = -32; x < 32; x++) {
            for (int z = -32; z < 32; z++) {
                long key = ChunkCache.chunkKey(x, z);
                keys.add(key);
                int h = Long.hashCode(key);
                buckets[(h ^ (h >>> 16)) & (buckets.length - 1)]++;
            }
        }
        assertEquals(64 * 64, keys.size(), "a key a chunk");
        assertTrue(Arrays.stream(buckets).max().orElseThrow() <= 8, "no bucket long enough to turn into a tree");
    }

    private static final int AIR = 0;
    private static final int STONE = 1;

    /**
     * A lookup's memo of its last chunk never answers for a chunk that's changed: a block set, the
     * chunk let go, another loaded - each read straight after one before it on the same chunk.
     */
    @Test
    void theLastChunkLookedUpIsNeverStale() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR)), "owner");
        assertFalse(cache.isSolid(5, -60, 5));

        cache.setBlock(5, -60, 5, STONE);
        assertTrue(cache.isSolid(5, -60, 5), "the block set");

        cache.unload(0, 0, "owner");
        assertTrue(cache.isSolid(5, -60, 5), "let go: unknown, so blocked");
        assertTrue(cache.stateIdAt(5, -60, 5).isEmpty());

        cache.load(0, 0, List.of(filled(AIR)), "owner");
        assertFalse(cache.isSolid(5, -60, 5), "loaded again, as sent");

        // From another thread, after this one's write: seen there too.
        cache.setBlock(6, -60, 6, STONE);
        boolean[] seen = new boolean[1];
        Thread other = new Thread(() -> seen[0] = cache.isSolid(6, -60, 6));
        other.start();
        assertTrue(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            other.join();
            return seen[0];
        }), "the other thread sees the block");
    }

    /** A column is loaded while any owner holds its chunk: unknown is not air, and not dug. */
    @Test
    void aColumnIsLoadedWhileItsChunkIsHeld() {
        ChunkCache cache = new ChunkCache();
        assertFalse(cache.isLoaded(16, 5), "nobody's loaded it");
        cache.load(1, 0, List.of(filled(AIR)), "owner");
        assertTrue(cache.isLoaded(16, 5));
        assertFalse(cache.isLoaded(15, 5), "the next chunk west is another");
        cache.unload(1, 0, "owner");
        assertFalse(cache.isLoaded(16, 5), "let go");
    }

    @Test
    void unknownChunksAreTreatedAsSolid() {
        ChunkCache cache = new ChunkCache();

        assertTrue(cache.isSolid(0, 0, 0), "a chunk nothing has loaded should be treated as blocked");
    }

    @Test
    void reportsAirAndStoneCorrectlyWithinALoadedChunk() {
        ChunkCache cache = new ChunkCache();
        // Overworld's bottom section is Y -64..-49 (section index 0, per MIN_SECTION_Y).
        int[] airSection = filled(AIR);
        int[] stoneSection = filled(STONE);
        cache.load(0, 0, List.of(airSection, stoneSection), "owner");

        assertFalse(cache.isSolid(5, -64, 5), "air (section 0) should not be solid");
        assertTrue(cache.isSolid(5, -48, 5), "stone (section 1) should be solid");
    }

    /**
     * A block the client wrote for itself - a pillar step, before the server's update - is taken
     * back if the server never confirms it: live, a step the server refused stood as cobblestone in
     * the model for hours, and a keep's whole dig waited on it.
     */
    @Test
    void aStandInTheServerNeverConfirmsIsTakenBack() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR)), "owner");

        assertTrue(cache.standIn(5, -60, 5, STONE, 1_000), "written over an open cell");
        assertTrue(cache.isSolid(5, -60, 5), "stood on at once");
        assertEquals(0, cache.expireStandIns(1_000 + ChunkCache.STAND_IN_MILLIS - 1));
        assertTrue(cache.isSolid(5, -60, 5), "still waiting on the server's word");
        assertEquals(1, cache.expireStandIns(1_000 + ChunkCache.STAND_IN_MILLIS));
        assertFalse(cache.isSolid(5, -60, 5), "never confirmed: the cell is open again");
    }

    @Test
    void aStandInTheServerConfirmsStaysAsTheServerSaid() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR)), "owner");
        int dirt = 2;

        cache.standIn(5, -60, 5, STONE, 0);
        cache.setBlock(5, -60, 5, dirt);
        assertEquals(0, cache.expireStandIns(ChunkCache.STAND_IN_MILLIS * 2));

        assertEquals(dirt, cache.stateIdAt(5, -60, 5).getAsInt(), "the server's word, not the stand-in");
    }

    /** The simulation's server answers a placement at once: a stand-in never covers what it said landed. */
    @Test
    void aStandInNeverCoversABlockTheModelHolds() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(STONE)), "owner");

        assertFalse(cache.standIn(5, -60, 5, 3, 0));
        assertEquals(STONE, cache.stateIdAt(5, -60, 5).getAsInt());
    }

    @Test
    void treatsCoordinatesOutsideTheDecodedSectionRangeAsSolid() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR)), "owner"); // only one section decoded

        assertTrue(cache.isSolid(0, 1000, 0), "far above the decoded range should be treated as blocked");
    }

    @Test
    void evictsOnlyOnceEveryOwnerHasUnloaded() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR)), "soldier-a");
        cache.load(0, 0, List.of(filled(AIR)), "soldier-b");

        cache.unload(0, 0, "soldier-a");
        assertFalse(cache.isSolid(0, -64, 0), "still loaded while soldier-b holds a reference");

        cache.unload(0, 0, "soldier-b");
        assertTrue(cache.isSolid(0, -64, 0), "evicted once every owner has unloaded");
    }

    @Test
    void aChunkSentAgainKeepsWhatItsOwnersKeptCurrent() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(STONE)), "soldier-a");
        cache.setBlock(0, -64, 0, AIR);                       // dug, as soldier-a was told
        cache.load(0, 0, List.of(filled(STONE)), "soldier-b"); // sent to soldier-b before the dig, arriving after

        assertFalse(cache.isSolid(0, -64, 0), "the dig stands");
    }

    @Test
    void aChunkSentAgainSaysWhereItDiffersFromTheOneKept() {
        ChunkCache cache = new ChunkCache();
        assertEquals(0, cache.load(1, -2, List.of(filled(STONE)), "soldier-a").blocks(), "nothing kept to differ from");
        cache.setBlock(17, -64, -30, AIR);

        ChunkCache.Divergence diverged = cache.load(1, -2, List.of(filled(STONE)), "soldier-b");

        assertEquals(1, diverged.blocks());
        assertEquals(List.of(17, -64, -30, AIR, STONE), Arrays.stream(diverged.samples().getFirst()).boxed().toList());
        int[] asKept = filled(STONE);
        asKept[(2 << 4) | 1] = AIR; // (17, -64, -30): x 1, z 2 in chunk (1, -2)
        assertEquals(0, cache.load(1, -2, List.of(asKept), "soldier-c").blocks(), "the same blocks: no divergence");
    }

    @Test
    void anOwnerThatRespawnedNoLongerKeepsItsChunks() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(STONE)), "soldier-a");
        cache.removeOwner("soldier-a");                        // respawned far off: it no longer sees the chunk
        cache.load(0, 0, List.of(filled(AIR)), "soldier-b");   // the block broken since, as sent now

        assertFalse(cache.isSolid(0, -64, 0), "the chunk as the server sends it now");
    }

    @Test
    void unloadingAnUnknownOwnerOfAnUnloadedChunkIsANoOp() {
        ChunkCache cache = new ChunkCache();

        cache.unload(0, 0, "nobody-ever-loaded-this");

        assertTrue(cache.isSolid(0, -64, 0));
    }

    @Test
    void loadedChunkCountReflectsDistinctChunksNotOwners() {
        ChunkCache cache = new ChunkCache();
        assertEquals(0, cache.loadedChunkCount());

        cache.load(0, 0, List.of(filled(AIR)), "soldier-a");
        cache.load(0, 0, List.of(filled(AIR)), "soldier-b"); // same chunk, second owner
        cache.load(1, 0, List.of(filled(AIR)), "soldier-a");
        assertEquals(2, cache.loadedChunkCount());

        cache.unload(1, 0, "soldier-a");
        assertEquals(1, cache.loadedChunkCount());
    }

    /** A section of air reads open, one with a block in it doesn't, and a block set since is read. */
    @Test
    void anOpenSectionIsReadAgainAfterABlockIsSet() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR), filled(STONE)), "owner");
        assertTrue(cache.openSection(3, -60, 3), "all air");
        assertFalse(cache.openSection(3, -44, 3), "all stone");
        assertFalse(cache.openSection(20, -60, 3), "a chunk not loaded");

        cache.setBlock(7, -55, 7, STONE);
        assertFalse(cache.openSection(3, -60, 3), "a block set in it since");
        cache.setBlock(7, -55, 7, AIR);
        assertTrue(cache.openSection(3, -60, 3), "and taken out again");
    }

    private static int[] filled(int stateId) {
        int[] section = new int[4096];
        Arrays.fill(section, stateId);
        return section;
    }

    /** The ground's stamp holds while nothing round it changes, and changes with a block set within its reach - not beyond. */
    @Test
    void theGroundsStampChangesOnlyWithTheGroundRoundIt() {
        ChunkCache cache = new ChunkCache();
        cache.load(0, 0, List.of(filled(AIR)), "owner");
        cache.load(10, 0, List.of(filled(AIR)), "owner");
        long before = cache.stamp(5, 5, 4);

        assertEquals(before, cache.stamp(5, 5, 4), "nothing changed");
        cache.setBlock(10 * 16 + 1, -60, 1, STONE);
        assertEquals(before, cache.stamp(5, 5, 4), "a block ten chunks off");
        cache.setBlock(6, -60, 6, STONE);
        assertNotEquals(before, cache.stamp(5, 5, 4), "a block in its own chunk");
    }
}
