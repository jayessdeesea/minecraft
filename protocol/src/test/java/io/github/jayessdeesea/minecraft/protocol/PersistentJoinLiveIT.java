package io.github.jayessdeesea.minecraft.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connects to a real, locally running Minecraft server and stays connected through Play, proving
 * {@link MinecraftClient#join} - unlike {@link JoinObservationLiveIT}'s diagnostic {@code
 * observeJoin} - does not self-close: the connection must survive at least one Keep Alive
 * round-trip (vanilla's cadence is ~15s) without the server timing it out, then close cleanly when
 * asked to. See DEVELOPMENT.md for how to have a server running on localhost:25565.
 *
 * <p>Named {@code *IT} so {@code mvn verify} skips it by default (see StatusPingLiveIT's javadoc
 * for why). Run explicitly:
 *
 * <pre>{@code mvn -pl protocol test -Dtest=PersistentJoinLiveIT}</pre>
 *
 * <p>Each test below uses its own distinct username, deliberately - the dev server's chunk
 * tracking for a given player UUID appears to persist across rapid reconnects (confirmed by
 * elimination: the same username that had just gotten zero chunk updates in 20 real seconds got
 * one in under a second under a fresh username, with no other change). Reusing a username across
 * tests run back-to-back risks exactly this kind of flakiness, not a real bug.
 */
class PersistentJoinLiveIT {

    private static final Logger LOG = LogManager.getLogger(PersistentJoinLiveIT.class);

    @Test
    void staysConnectedThroughAKeepAliveRoundTrip() throws Exception {
        PlaySession session = MinecraftClient.join("localhost", 25565, "KeepAliveTestBot")
                .get(15, TimeUnit.SECONDS);

        LOG.info("Entered Play: {}", session.playState());
        assertTrue(session.isOpen(), "session should be open immediately after joining");

        // Vanilla's Keep Alive cadence is ~15s (see KeepAliveRequestPacket's javadoc) - wait past
        // one full interval so a wrong/missing reply would have already gotten us timed out.
        Thread.sleep(20_000);
        assertTrue(session.isOpen(), "session should still be open after a Keep Alive round-trip");

        session.close();
        // Netty's close() is asynchronous - give it a moment to actually complete.
        for (int i = 0; i < 50 && session.isOpen(); i++) {
            Thread.sleep(100);
        }
        assertFalse(session.isOpen(), "session should be closed after close()");
    }

    @Test
    void deliversChunkUpdatesForRealChunksAroundSpawn() throws Exception {
        List<ChunkUpdate> updates = new CopyOnWriteArrayList<>();

        PlaySession session = MinecraftClient.join("localhost", 25565, "ChunkTestBot",
                        new PlaySessionListeners(updates::add, unload -> { }, position -> { }, entity -> { }, move -> { }, ids -> { }, health -> { }, inventory -> { }, cause -> { }))
                .get(15, TimeUnit.SECONDS);

        // Chunks around spawn arrive within the first second or two of reaching Play - no need to
        // wait anywhere near as long as the Keep Alive test above.
        for (int i = 0; i < 50 && updates.isEmpty(); i++) {
            Thread.sleep(100);
        }
        session.close();

        assertFalse(updates.isEmpty(), "expected at least one chunk update after joining");
        ChunkUpdate first = updates.get(0);
        LOG.info("First chunk update: ({}, {}), {} section(s)",
                first.chunkX(), first.chunkZ(), first.sectionBlockStates().size());
        assertEquals(24, first.sectionBlockStates().size(), "Overworld chunks should have 24 sections");
        for (int[] section : first.sectionBlockStates()) {
            assertEquals(4096, section.length, "each section should have 4096 block-state entries");
        }
    }

    /**
     * Any entity proves the decode - a passive mob, a dropped item, another player. Doesn't need a
     * hostile mob nearby. Also proves the variable-length velocity skip is right against real
     * traffic: {@code AddEntityPacket.decode} rejects trailing bytes, which closes the connection.
     */
    @Test
    void deliversEntityUpdatesForEntitiesAroundSpawn() throws Exception {
        List<EntityUpdate> entities = new CopyOnWriteArrayList<>();

        PlaySession session = MinecraftClient.join("localhost", 25565, "EntityTestBot",
                        new PlaySessionListeners(update -> { }, unload -> { }, position -> { }, entities::add, move -> { }, ids -> { }, health -> { }, inventory -> { }, cause -> { }))
                .get(15, TimeUnit.SECONDS);

        for (int i = 0; i < 50 && entities.isEmpty(); i++) {
            Thread.sleep(100);
        }
        assertTrue(session.isOpen(), "session should survive decoding Add Entity packets");
        session.close();

        assertFalse(entities.isEmpty(), "expected at least one entity in view after joining");
        LOG.info("{} entity update(s); first: {}", entities.size(), entities.get(0));
        for (EntityUpdate entity : entities) {
            assertTrue(entity.y() > -64 && entity.y() < 320, "entity Y should be within the Overworld's height range: " + entity);
        }
    }

    /**
     * Wandering mobs around spawn send a steady stream of relative moves. Each resolved position
     * should be a small step from that entity's previous one - a wrong scale or a wrong base would
     * show up as jumps of many blocks (or drift off to nonsense coordinates) within seconds.
     */
    @Test
    void resolvesEntityMovesIntoContinuousAbsolutePositions() throws Exception {
        Map<Integer, double[]> last = new ConcurrentHashMap<>();
        List<String> jumps = new CopyOnWriteArrayList<>();
        AtomicInteger moves = new AtomicInteger();

        PlaySession session = MinecraftClient.join("localhost", 25565, "MoveTestBot",
                        new PlaySessionListeners(update -> { }, unload -> { }, position -> { },
                                entity -> last.put(entity.entityId(), new double[]{entity.x(), entity.y(), entity.z()}),
                                move -> {
                                    moves.incrementAndGet();
                                    double[] previous = last.put(move.entityId(), new double[]{move.x(), move.y(), move.z()});
                                    if (previous != null && Math.abs(previous[0] - move.x()) + Math.abs(previous[1] - move.y())
                                            + Math.abs(previous[2] - move.z()) > 8) {
                                        jumps.add(move + " from " + previous[0] + "," + previous[1] + "," + previous[2]);
                                    }
                                },
                                ids -> { }, health -> { }, inventory -> { }, cause -> { }))
                .get(15, TimeUnit.SECONDS);

        Thread.sleep(10_000);
        assertTrue(session.isOpen(), "session should survive decoding movement packets");
        session.close();

        LOG.info("{} entity move(s) across {} entities", moves.get(), last.size());
        assertTrue(moves.get() > 0, "expected wandering mobs to send at least one move in 10s");
        assertTrue(jumps.isEmpty(), "implausible jumps: " + jumps);
    }

    @Test
    void receivesRealSpawnPositionAndStaysConnectedAfterAcceptingIt() throws Exception {
        List<PlayerPosition> positions = new CopyOnWriteArrayList<>();

        PlaySession session = MinecraftClient.join("localhost", 25565, "PositionTestBot",
                        new PlaySessionListeners(update -> { }, unload -> { }, positions::add, entity -> { }, move -> { }, ids -> { }, health -> { }, inventory -> { }, cause -> { }))
                .get(15, TimeUnit.SECONDS);

        for (int i = 0; i < 50 && positions.isEmpty(); i++) {
            Thread.sleep(100);
        }

        assertFalse(positions.isEmpty(), "expected a Player Position packet after joining");
        PlayerPosition first = positions.get(0);
        LOG.info("First confirmed position: ({}, {}, {})", first.x(), first.y(), first.z());
        assertTrue(first.y() > -64 && first.y() < 320, "spawn Y should be within the Overworld's height range");

        // If AcceptTeleportationPacket's ID or content were wrong, the server would eventually
        // give up on us - wait past a Keep Alive interval to be sure it didn't.
        Thread.sleep(16_000);
        assertTrue(session.isOpen(), "session should still be open after accepting the position");

        session.reportPosition(first.x(), first.y(), first.z(), true);
        Thread.sleep(500);
        assertTrue(session.isOpen(), "session should still be open after reporting a position back");

        session.close();
    }
}
