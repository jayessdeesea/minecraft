package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkCacheTest {

    private static final int AIR = 0;
    private static final int STONE = 1;

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

    private static int[] filled(int stateId) {
        int[] section = new int[4096];
        Arrays.fill(section, stateId);
        return section;
    }
}
