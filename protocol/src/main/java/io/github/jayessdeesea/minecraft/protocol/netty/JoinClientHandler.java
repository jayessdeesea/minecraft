package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.ObservedPacket;
import io.github.jayessdeesea.minecraft.protocol.PlayState;
import io.github.jayessdeesea.minecraft.protocol.packet.AcknowledgeFinishConfigurationPacket;
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
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.RegistryDataPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Control handler for a full join: sends every reply the join sequence needs (docs/protocol-spec.md
 * § 3.3/§ 3.4) - Login Start on connect, Login Acknowledged + Client Information on Login Success,
 * Known Packs on Select Known Packs, Acknowledge Finish Configuration on Finish Configuration -
 * and owns completion of {@code result} once observation is done.
 *
 * <p>Deliberately holds no {@link ProtocolState} of its own: {@link SessionStateManager}, earlier
 * in the pipeline, is the single source of truth for that, queried here only for the one thing
 * that actually depends on it (whether to keep resetting the quiet-deadline or not). Dispatch
 * below is entirely by packet <em>type</em>, not by state - a decoded packet's type already
 * implies what to do with it.
 */
public final class JoinClientHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOG = LogManager.getLogger(JoinClientHandler.class);

    /** How long to wait after the last Login/Configuration packet before deciding it's gone quiet. */
    private static final long QUIET_PERIOD_MILLIS = 1000;

    /** Fixed window to observe Play traffic once reached - see the note where this is scheduled. */
    private static final long PLAY_OBSERVATION_MILLIS = 2000;

    private final int protocolVersion;
    private final String host;
    private final int port;
    private final String username;
    private final CompletableFuture<List<ObservedPacket>> result;

    private ScheduledFuture<?> quietDeadline;

    public JoinClientHandler(int protocolVersion, String host, int port, String username,
                              CompletableFuture<List<ObservedPacket>> result) {
        this.protocolVersion = protocolVersion;
        this.host = host;
        this.port = port;
        this.username = username;
        this.result = result;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(new HandshakePacket(protocolVersion, host, port, HandshakePacket.NEXT_STATE_LOGIN));
        sessionState(ctx).transitionTo(ctx, ProtocolState.LOGIN);
        // Offline mode derives its own UUID from the username and ignores ours - see LoginStartPacket.
        ctx.writeAndFlush(new LoginStartPacket(username, new UUID(0, 0)));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof LoginDisconnectPacket disconnect) {
            result.completeExceptionally(new IllegalStateException("Server rejected login: " + disconnect.reasonJson()));
            ctx.close();
            return;
        } else if (packet instanceof LoginSuccessPacket success) {
            LOG.info("Login Success: uuid={} username={}", success.uuid(), success.username());
            ctx.writeAndFlush(new LoginAcknowledgedPacket());
            // The server withholds Finish Configuration without this - confirmed against the
            // live dev server. See ClientInformationPacket's javadoc on the ID being unconfirmed.
            ctx.writeAndFlush(ClientInformationPacket.defaults());
        } else if (packet instanceof RegistryDataPacket registryData) {
            LOG.info("Registry Data: {} ({} entries)", registryData.registry(), registryData.entryIds().size());
        } else if (packet instanceof KnownPacksPacket selectKnownPacks) {
            LOG.info("Select Known Packs: {}", selectKnownPacks.packs());
            // Claim we know none of them (we don't bundle any Minecraft data ourselves), so the
            // server sends us as much as it's willing to over the wire rather than assuming we
            // already have it - see KnownPacksPacket's javadoc on the reply ID being unverified.
            ctx.writeAndFlush(new KnownPacksPacket(List.of()));
        } else if (packet instanceof FinishConfigurationPacket) {
            LOG.info("Finish Configuration received - acknowledging and switching to Play");
            ctx.writeAndFlush(new AcknowledgeFinishConfigurationPacket());
            scheduleFixedPlayObservationWindow(ctx);
            return;
        } else if (packet instanceof PlayLoginPacket login) {
            // Field-by-field confirmed against the live dev server - see PlayLoginPacket's javadoc.
            LOG.info("Entered Play: {}", PlayState.fromLogin(login));
        } else if (packet instanceof KeepAliveRequestPacket keepAlive) {
            LOG.info("Keep Alive received: id={} - replying", keepAlive.id());
            ctx.writeAndFlush(new KeepAliveResponsePacket(keepAlive.id()));
        }

        // Only Configuration is a genuine burst-then-pause worth waiting out; Login has nothing to
        // time out on (we're waiting indefinitely for Login Success), and Play is continuous
        // traffic (handled once, by scheduleFixedPlayObservationWindow - never reset thereafter,
        // or every subsequent Play packet falling through to here would keep re-canceling it).
        if (sessionState(ctx).state() == ProtocolState.CONFIGURATION) {
            resetQuietDeadline(ctx);
        }
    }

    /**
     * Unlike Login/Configuration (a genuine burst-then-pause), Play is continuous - the server
     * resumes full tick activity once we're connected, so there's no natural quiet gap to wait
     * for. A fixed, non-resetting window replaces {@link #resetQuietDeadline}'s per-packet reset
     * here; resetting on every Play packet would never let this complete.
     */
    private void scheduleFixedPlayObservationWindow(ChannelHandlerContext ctx) {
        if (quietDeadline != null) {
            quietDeadline.cancel(false);
        }
        quietDeadline = ctx.executor().schedule(() -> complete(ctx, "Play observation window elapsed"),
                PLAY_OBSERVATION_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void resetQuietDeadline(ChannelHandlerContext ctx) {
        if (quietDeadline != null) {
            quietDeadline.cancel(false);
        }
        quietDeadline = ctx.executor().schedule(() -> complete(ctx, "Quiet for " + QUIET_PERIOD_MILLIS + " ms"),
                QUIET_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void complete(ChannelHandlerContext ctx, String reason) {
        List<ObservedPacket> observed = sessionState(ctx).observed();
        LOG.info("{} in state {} with {} unrecognized packet(s) seen total", reason, sessionState(ctx).state(), observed.size());
        result.complete(observed);
        ctx.close();
    }

    private static SessionStateManager sessionState(ChannelHandlerContext ctx) {
        return ctx.pipeline().get(SessionStateManager.class);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Always logged, not just handed to the future - a persistent connection (unlike this
        // diagnostic tool) may have nothing reading the future's failure directly, and per
        // docs/conventions.md's no-fallbacks tenet, a guard violation disconnecting must be loud, not
        // a silently-closed connection with the reason visible only to whoever happens to be
        // watching the CompletableFuture.
        LOG.error("Connection failed, disconnecting: {}", cause.getMessage(), cause);
        result.completeExceptionally(cause);
        ctx.close();
    }
}
