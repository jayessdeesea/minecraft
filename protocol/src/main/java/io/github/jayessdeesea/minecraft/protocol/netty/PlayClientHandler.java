package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.ChunkUpdate;
import io.github.jayessdeesea.minecraft.protocol.EntityMove;
import io.github.jayessdeesea.minecraft.protocol.EntityUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.PlayState;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import io.github.jayessdeesea.minecraft.protocol.packet.AcceptTeleportationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.AddEntityPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.AcknowledgeFinishConfigurationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ChunkDataPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.EntityPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ClientInformationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.FinishConfigurationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.HandshakePacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KeepAliveRequestPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KeepAliveResponsePacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KnownPacksPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginAcknowledgedPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginDisconnectPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginStartPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginSuccessPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.MoveEntityPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.RegistryDataPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.RemoveEntitiesPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.SetHealthPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Control handler for a persistent connection: drives the same join sequence as
 * {@link JoinClientHandler} (Login Start on connect, Login Acknowledged + Client Information on
 * Login Success, Known Packs on Select Known Packs, Acknowledge Finish Configuration on Finish
 * Configuration - docs/protocol-spec.md § 3.3/§ 3.4), but instead of observing Play for a fixed
 * window and self-closing, completes {@code result} with a {@link PlaySession} the moment Play is
 * reached and then stays connected indefinitely - replying to Keep Alive so the server doesn't
 * time the connection out. Each decoded {@link ChunkDataPacket} is handed to {@code onChunkUpdate}
 * as a Netty/packet-free {@link ChunkUpdate} - delivered on this connection's own event loop
 * thread. No dedicated dispatcher/actor sits between this and the listener: the listener is
 * expected to call straight into an already-thread-safe shared structure (e.g. {@code
 * bot.world.ChunkCache}'s {@code ConcurrentHashMap.compute()}-based cache), which is exactly what
 * lets multiple soldiers' independent connections - each on its own event loop - write into one
 * shared world model with no additional synchronization. Each {@link PlayerPositionPacket} is
 * confirmed with {@link AcceptTeleportationPacket} (mandatory, protocol-level, zero degrees of
 * freedom - fits the same control-plane tenets as Keep Alive) and handed to {@code
 * onPlayerPosition} as a {@link PlayerPosition}.
 *
 * <p>The join sequence here is intentionally a separate copy of {@link JoinClientHandler}'s, not a
 * shared helper: the two classes complete their future at different points for different reasons
 * (this one hands off control once Play starts; that one keeps cataloging Play packets after Play
 * starts) and each stays a self-contained control-plane reply per docs/protocol-pipeline.md's
 * tenets. The shared source of truth for the sequence itself is the spec doc above, not either
 * class.
 *
 * <p>{@code listeners.onDisconnect()} fires from {@link #channelInactive} - which runs whenever
 * this connection's channel closes, for <em>any</em> reason (the {@link ConnectionWatchdogHandler}
 * closing it after an idle timeout, an exception, or the server closing its end) - unless the
 * channel carries {@link #INTENTIONAL_CLOSE}, the marker {@link NettyPlaySession#close()} sets
 * before closing so a caller's own deliberate shutdown isn't mistaken for an unexpected one.
 */
public final class PlayClientHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOG = LogManager.getLogger(PlayClientHandler.class);

    /** Set by {@link NettyPlaySession#close()} before closing, to distinguish a deliberate close
     * from an unexpected one - see {@link #channelInactive}. */
    static final AttributeKey<Boolean> INTENTIONAL_CLOSE = AttributeKey.valueOf("minecraft.intentionalClose");

    private final int protocolVersion;
    private final String host;
    private final int port;
    private final String username;
    private final CompletableFuture<PlaySession> result;
    private final PlaySessionListeners listeners;

    // Both written and read only from this channel's own event loop thread (Netty's per-channel
    // single-threading guarantee - see AGENTS.md's "Behavior modeling" section) - no volatile needed.
    private boolean playReached = false;
    private Throwable failureCause = null;

    // Last server-sent position per entity on this connection - Move Entity packets are changes
    // relative to it (see MoveEntityPacket). Kept here, per connection, rather than in the shared
    // bot-side tracker: every connection watching an entity receives the same changes, so applying
    // them to one shared position would move it once per watching soldier. Event-loop confined.
    private final Map<Integer, double[]> entityPositions = new HashMap<>();

    public PlayClientHandler(int protocolVersion, String host, int port, String username,
                              CompletableFuture<PlaySession> result, PlaySessionListeners listeners) {
        this.protocolVersion = protocolVersion;
        this.host = host;
        this.port = port;
        this.username = username;
        this.result = result;
        this.listeners = listeners;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new HandshakePacket(protocolVersion, host, port, HandshakePacket.NEXT_STATE_LOGIN));
        ctx.pipeline().get(SessionStateManager.class).transitionTo(ctx, ProtocolState.LOGIN);
        // Offline mode derives its own UUID from the username and ignores ours - see LoginStartPacket.
        ctx.writeAndFlush(new LoginStartPacket(username, new UUID(0, 0)));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof LoginDisconnectPacket disconnect) {
            result.completeExceptionally(new IllegalStateException("Server rejected login: " + disconnect.reasonJson()));
            ctx.close();
        } else if (packet instanceof LoginSuccessPacket success) {
            LOG.info("Login Success: uuid={} username={}", success.uuid(), success.username());
            ctx.writeAndFlush(new LoginAcknowledgedPacket());
            ctx.writeAndFlush(ClientInformationPacket.defaults());
        } else if (packet instanceof RegistryDataPacket registryData) {
            LOG.debug("Registry Data received: {} ({} entries)", registryData.registry(), registryData.entryIds().size());
        } else if (packet instanceof KnownPacksPacket selectKnownPacks) {
            LOG.info("Select Known Packs: {}", selectKnownPacks.packs());
            ctx.writeAndFlush(new KnownPacksPacket(List.of()));
        } else if (packet instanceof FinishConfigurationPacket) {
            LOG.info("Finish Configuration received - acknowledging and switching to Play");
            ctx.writeAndFlush(new AcknowledgeFinishConfigurationPacket());
        } else if (packet instanceof PlayLoginPacket login) {
            PlayState playState = PlayState.fromLogin(login);
            LOG.info("Entered Play: {}", playState);
            playReached = true;
            result.complete(new NettyPlaySession(ctx.channel(), playState));
        } else if (packet instanceof KeepAliveRequestPacket keepAlive) {
            LOG.debug("Keep Alive received: id={} - replying", keepAlive.id());
            ctx.writeAndFlush(new KeepAliveResponsePacket(keepAlive.id()));
        } else if (packet instanceof ChunkDataPacket chunkData) {
            LOG.debug("Chunk Data received: ({}, {}), {} section(s)",
                    chunkData.chunkX(), chunkData.chunkZ(), chunkData.sectionBlockStates().size());
            listeners.onChunkUpdate().accept(new ChunkUpdate(chunkData.chunkX(), chunkData.chunkZ(), chunkData.sectionBlockStates()));
        } else if (packet instanceof PlayerPositionPacket position) {
            LOG.info("Player Position received: ({}, {}, {}) - accepting", position.x(), position.y(), position.z());
            ctx.writeAndFlush(new AcceptTeleportationPacket(position.teleportId()));
            listeners.onPlayerPosition().accept(new PlayerPosition(position.x(), position.y(), position.z()));
        } else if (packet instanceof AddEntityPacket entity) {
            LOG.debug("Add Entity received: id={} type={} at ({}, {}, {})",
                    entity.entityId(), entity.typeId(), entity.x(), entity.y(), entity.z());
            entityPositions.put(entity.entityId(), new double[]{entity.x(), entity.y(), entity.z()});
            listeners.onEntitySpawn().accept(new EntityUpdate(entity.entityId(), entity.typeId(), entity.x(), entity.y(), entity.z()));
        } else if (packet instanceof MoveEntityPacket move) {
            double[] position = entityPositions.get(move.entityId());
            if (position == null) {
                LOG.warn("Move Entity for entity {} this connection never saw added - ignoring", move.entityId());
                return;
            }
            position[0] = MoveEntityPacket.apply(position[0], move.deltaX());
            position[1] = MoveEntityPacket.apply(position[1], move.deltaY());
            position[2] = MoveEntityPacket.apply(position[2], move.deltaZ());
            listeners.onEntityMove().accept(new EntityMove(move.entityId(), position[0], position[1], position[2]));
        } else if (packet instanceof EntityPositionPacket sync) {
            double[] position = entityPositions.get(sync.entityId());
            if (position == null) {
                LOG.warn("Entity position 0x{} for entity {} this connection never saw added - ignoring",
                        Integer.toHexString(sync.packetId()), sync.entityId());
                return;
            }
            position[0] = sync.isRelative(0) ? position[0] + sync.x() : sync.x();
            position[1] = sync.isRelative(1) ? position[1] + sync.y() : sync.y();
            position[2] = sync.isRelative(2) ? position[2] + sync.z() : sync.z();
            listeners.onEntityMove().accept(new EntityMove(sync.entityId(), position[0], position[1], position[2]));
        } else if (packet instanceof RemoveEntitiesPacket removal) {
            LOG.debug("Remove Entities received: {}", removal.entityIds());
            removal.entityIds().forEach(entityPositions::remove);
            listeners.onEntitiesRemoved().accept(removal.entityIds());
        } else if (packet instanceof SetHealthPacket health) {
            LOG.debug("Set Health received: health={} food={}", health.health(), health.food());
            listeners.onHealth().accept(health.health());
        }
        // Everything else recognized-but-unhandled or unrecognized is already logged/cataloged by
        // PacketLoggingHandler/SessionStateManager earlier in the pipeline - nothing further to do
        // here until a real reason to react to it exists (AGENTS.md's revisit-on-real-pressure tenet).
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Always logged, not just handed to the future - once result has already completed with a
        // PlaySession, nothing may be listening to a second completion attempt, so the failure
        // must be visible some other way too (AGENTS.md's no-fallbacks tenet: loud, not silent).
        // The actual reconnect signal for a caller past that point is channelInactive below, not
        // this method - result.completeExceptionally is a no-op once already completed.
        LOG.error("Connection failed, disconnecting: {}", cause.getMessage(), cause);
        failureCause = cause;
        result.completeExceptionally(cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        boolean intentional = Boolean.TRUE.equals(ctx.channel().attr(INTENTIONAL_CLOSE).get());
        if (playReached && !intentional) {
            Throwable cause = failureCause != null
                    ? failureCause
                    : new IOException("Connection closed unexpectedly (idle timeout or the server closed it)");
            listeners.onDisconnect().accept(cause);
        }
    }
}
