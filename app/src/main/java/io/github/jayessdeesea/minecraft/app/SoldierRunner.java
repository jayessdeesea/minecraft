package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.world.ChunkCache;
import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.MinecraftClient;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The composition root's actual job: connect each named Soldier below, bridge their live
 * {@code ChunkUpdate}s into one shared {@link ChunkCache} and their entity spawns/removals into one
 * shared {@link EntityTracker}, seed each one's real spawn position
 * from the server's own {@code PlayerPosition} confirmation, give each a short walk in a different
 * direction to visibly prove independent movement, and run each one's tick loop.
 *
 * <p>{@code SOLDIERS} is a hardcoded list, not {@code application.yml} - the "app config shape"
 * decision DEVELOPMENT.md's "Known gaps" flagged as open. Deliberately resolved this way for now
 * (AGENTS.md's revisit-on-real-pressure tenet): nothing yet needs to reconfigure soldiers without
 * recompiling (no real deployment, one dev server, always localhost). Revisit once that changes.
 *
 * <p>All soldiers share one {@link ChunkCache} - AGENTS.md's "Behavior modeling" section built its
 * reference counting specifically for this: the block data is one shared ground truth regardless
 * of which soldier's connection loaded it, and a chunk both soldiers can see stays cached until
 * *both* have unloaded it. Each soldier still gets its own connection, tick-loop thread, and
 * {@link DeterministicSoldier} instance - only the world model is shared. Entities work the same
 * way: every connection feeds one {@link EntityTracker}, and each soldier ticks against one
 * {@link WorldModel} combining both, so a hostile seen by Bob's connection makes Alice back away too.
 *
 * <p>Each soldier also reconnects on its own if its connection dies unexpectedly - see {@code
 * PlaySessionListeners.onDisconnect} and {@link #onDisconnected}. A reconnect re-seeds a brand new
 * {@link DeterministicSoldier} from wherever the server places that username on rejoining, rather
 * than trying to resume in-flight state - simplest thing that satisfies "don't hang forever on a
 * silently-dead connection," not a full resume/backoff policy.
 */
@Component
public class SoldierRunner implements CommandLineRunner {

    private static final Logger LOG = LogManager.getLogger(SoldierRunner.class);

    // Hardcoded pending a real deployment need for external config - matches the dev server
    // DEVELOPMENT.md's own setup instructions put on localhost.
    private static final String HOST = "localhost";
    private static final int PORT = 25565;

    private static final long TICK_PERIOD_MILLIS = 50; // 20 Hz, matching Minecraft's own tick rate
    private static final long STATUS_LOG_PERIOD_SECONDS = 5;

    /** Each soldier's name and which direction to walk from spawn - just enough to visibly diverge. */
    private record SoldierDefinition(String name, double walkDx, double walkDz) {
    }

    private static final List<SoldierDefinition> SOLDIERS = List.of(
            new SoldierDefinition("Alice", 10, 10),
            new SoldierDefinition("Bob", -10, 10)
    );

    private final ChunkCache chunkCache = new ChunkCache();
    private final EntityTracker entityTracker = new EntityTracker();
    private final WorldModel world = new WorldModel(chunkCache, entityTracker);

    @Override
    public void run(String... args) {
        for (SoldierDefinition definition : SOLDIERS) {
            connect(definition);
        }
    }

    private void connect(SoldierDefinition definition) {
        String name = definition.name();
        CompletableFuture<PlayerPosition> firstPosition = new CompletableFuture<>();
        // Populated once onJoined's own scheduler starts - onDisconnected reads it to stop the old
        // tick loop before reconnecting. Netty's per-channel event loop ordering guarantees onJoined
        // (which runs from the same PlayLoginPacket dispatch that completes this join) has already
        // set it by the time any later channelInactive on this same connection could fire onDisconnect.
        AtomicReference<ScheduledExecutorService> activeScheduler = new AtomicReference<>();
        // Set by onJoined, which runs on the event loop inside the first Player Position's dispatch -
        // so every later Player Position on this connection already sees it.
        AtomicReference<DeterministicSoldier> activeSoldier = new AtomicReference<>();
        CompletableFuture<PlaySession> sessionReady = new CompletableFuture<>();

        CompletableFuture<PlaySession> joined = MinecraftClient.join(HOST, PORT, name, new PlaySessionListeners(
                update -> chunkCache.load(update.chunkX(), update.chunkZ(), update.sectionBlockStates(), name),
                position -> {
                    if (!firstPosition.complete(position)) {
                        DeterministicSoldier soldier = activeSoldier.get();
                        if (soldier != null) {
                            soldier.correctPosition(new Position(position.x(), position.y(), position.z()));
                        }
                    }
                },
                entity -> entityTracker.spawn(entity.entityId(), entity.typeId(), entity.x(), entity.y(), entity.z(), name),
                move -> entityTracker.move(move.entityId(), move.x(), move.y(), move.z()),
                ids -> entityTracker.remove(ids, name),
                health -> {
                    // A dead player gets no chunks or entities until it respawns, and a soldier has
                    // no reason to stay dead - respawn immediately. The server then teleports it and
                    // sends a Player Position, which arrives above as a correction.
                    if (health <= 0) {
                        LOG.warn("{} is dead - respawning", name);
                        sessionReady.thenAccept(PlaySession::respawn);
                    }
                },
                cause -> onDisconnected(definition, activeScheduler, cause)));
        joined.thenAccept(sessionReady::complete);
        joined.thenAcceptBoth(firstPosition, (session, spawn) -> onJoined(definition, session, spawn, activeScheduler, activeSoldier))
                .exceptionally(cause -> {
                    LOG.error("{} failed to join {}:{} - {}", name, HOST, PORT, cause.getMessage(), cause);
                    return null;
                });
    }

    private void onJoined(SoldierDefinition definition, PlaySession session, PlayerPosition spawn,
                           AtomicReference<ScheduledExecutorService> activeScheduler,
                           AtomicReference<DeterministicSoldier> activeSoldier) {
        String name = definition.name();
        LOG.info("{} joined at real spawn ({}, {}, {}): {}", name, spawn.x(), spawn.y(), spawn.z(), session.playState());

        DeterministicSoldier soldier = new DeterministicSoldier(name, new Position(spawn.x(), spawn.y(), spawn.z()));
        activeSoldier.set(soldier);
        soldier.receiveOrder(new Order.TrendToward(spawn.x() + definition.walkDx(), spawn.z() + definition.walkDz()));

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, name + "-tick"));
        activeScheduler.set(scheduler);
        scheduler.scheduleAtFixedRate(() -> {
            soldier.tick(world).ifPresent(target -> {
                LOG.info("{} attacks entity {}", name, target);
                session.attack(target);
            });
            Position current = soldier.status().position();
            session.reportPosition(current.x(), current.y(), current.z(), soldier.onGround());
        }, 0, TICK_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(
                () -> LOG.info("{} status: {} - {} chunk(s), {} entities tracked, hostiles in contact: {}, session open: {}",
                        name, soldier.status(), chunkCache.loadedChunkCount(), entityTracker.trackedEntityCount(),
                        world.nearbyHostiles(soldier.status().position(), DeterministicSoldier.CONTACT_RADIUS),
                        session.isOpen()),
                STATUS_LOG_PERIOD_SECONDS, STATUS_LOG_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Fires from {@code protocol}'s watchdog (or any other unexpected close - see {@code
     * PlaySessionListeners.onDisconnect}): stops this soldier's now-dead tick loop and reconnects
     * from scratch via {@link #connect}, which re-seeds a brand new {@link DeterministicSoldier}
     * from the new session's own spawn position. Deliberately a single immediate attempt, not a
     * retry loop with backoff - if the server is actually down, {@code connect}'s own {@code
     * exceptionally} already logs that loudly and stops, per AGENTS.md's revisit-on-real-pressure
     * tenet this can grow a backoff/retry-limit policy if a real deployment ever needs one.
     */
    private void onDisconnected(SoldierDefinition definition,
                                 AtomicReference<ScheduledExecutorService> activeScheduler, Throwable cause) {
        String name = definition.name();
        LOG.warn("{} disconnected unexpectedly ({}) - reconnecting", name, cause.getMessage());
        ScheduledExecutorService scheduler = activeScheduler.get();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        // The new connection re-receives Add Entity for everything in view; drop this one's stale view.
        entityTracker.removeOwner(name);
        connect(definition);
    }
}
