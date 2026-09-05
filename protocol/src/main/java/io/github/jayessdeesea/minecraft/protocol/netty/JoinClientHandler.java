package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.ObservedPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ClientInformationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.HandshakePacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KnownPacksPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginDisconnectPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginStartPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginSuccessPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.github.jayessdeesea.minecraft.protocol.packet.SetCompressionPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.ScheduledFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Drives Login through Configuration to Play (docs/protocol-spec.md § 3.3/3.4/3.5), replying to
 * the two exchanges confirmed to need a reply (Client Information, Known Packs) and recognizing
 * Finish Configuration, but otherwise <em>observes</em> rather than fully implements
 * Configuration and Play - see docs/protocol-spec.md § 3.4 for how each confirmed ID was derived
 * from this handler's own logged output against the real dev server, not from any written spec.
 */
public final class JoinClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger LOG = LogManager.getLogger(JoinClientHandler.class);

    /** Login Acknowledged (docs/protocol-spec.md § 3.3 step 6) has an empty body. */
    private static final int LOGIN_ACKNOWLEDGED_PACKET_ID = 0x03;

    /** Empty-body clientbound Configuration packet; see docs/protocol-spec.md § 3.4. */
    private static final int FINISH_CONFIGURATION_PACKET_ID = 0x03;

    /** Reply to Finish Configuration; same numbering source as the other Configuration IDs below. */
    private static final int ACKNOWLEDGE_FINISH_CONFIGURATION_PACKET_ID = 0x03;

    /** How long to wait after the last packet before deciding the server has gone quiet. */
    private static final long QUIET_PERIOD_MILLIS = 1000;

    private enum State { LOGIN, CONFIGURATION, PLAY }

    private final int protocolVersion;
    private final String host;
    private final int port;
    private final String username;
    private final CompletableFuture<List<ObservedPacket>> result;
    private final List<ObservedPacket> observed = new ArrayList<>();

    private State state = State.LOGIN;
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
        PacketWriter.write(ctx, HandshakePacket.PACKET_ID, buf ->
                new HandshakePacket(protocolVersion, host, port, HandshakePacket.NEXT_STATE_LOGIN).encode(buf));
        // Offline mode derives its own UUID from the username and ignores ours - see LoginStartPacket.
        PacketWriter.write(ctx, LoginStartPacket.PACKET_ID, buf ->
                new LoginStartPacket(username, new UUID(0, 0)).encode(buf));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        int packetId = ProtocolCodec.readVarInt(frame);
        switch (state) {
            case LOGIN -> handleLogin(ctx, packetId, frame);
            case CONFIGURATION -> handleConfiguration(ctx, packetId, frame);
            case PLAY -> handlePlay(packetId, frame);
        }
    }

    private void handleLogin(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        if (packetId == LoginDisconnectPacket.PACKET_ID) {
            String reason = LoginDisconnectPacket.decode(frame).reasonJson();
            result.completeExceptionally(new IllegalStateException("Server rejected login: " + reason));
            ctx.close();
        } else if (packetId == SetCompressionPacket.PACKET_ID) {
            int threshold = SetCompressionPacket.decode(frame).threshold();
            LOG.info("Set Compression: threshold={}", threshold);
            ctx.pipeline().get(VarIntFrameDecoder.class).setCompressionThreshold(threshold);
            ctx.pipeline().get(VarIntFrameEncoder.class).setCompressionThreshold(threshold);
        } else if (packetId == LoginSuccessPacket.PACKET_ID) {
            LoginSuccessPacket success = LoginSuccessPacket.decode(frame);
            LOG.info("Login Success: uuid={} username={}", success.uuid(), success.username());
            PacketWriter.write(ctx, LOGIN_ACKNOWLEDGED_PACKET_ID, buf -> { });
            // The server withholds Finish Configuration without this - confirmed against the
            // live dev server. See ClientInformationPacket's javadoc on the ID being unconfirmed.
            PacketWriter.write(ctx, ClientInformationPacket.PACKET_ID,
                    buf -> ClientInformationPacket.defaults().encode(buf));
            state = State.CONFIGURATION;
            resetQuietDeadline(ctx);
        } else {
            LOG.warn("Unexpected packet ID 0x{} in Login state", Integer.toHexString(packetId));
        }
    }

    private void handleConfiguration(ChannelHandlerContext ctx, int packetId, ByteBuf frame) {
        logAndRecord("CONFIGURATION", packetId, frame);

        if (packetId == KnownPacksPacket.CLIENTBOUND_PACKET_ID) {
            KnownPacksPacket selectKnownPacks = KnownPacksPacket.decode(frame);
            LOG.info("Select Known Packs: {}", selectKnownPacks.packs());
            // Claim we know none of them (we don't bundle any Minecraft data ourselves), so the
            // server sends us as much as it's willing to over the wire rather than assuming we
            // already have it - see KnownPacksPacket's javadoc on the reply ID being unverified.
            PacketWriter.write(ctx, KnownPacksPacket.SERVERBOUND_PACKET_ID,
                    buf -> new KnownPacksPacket(List.of()).encode(buf));
        } else if (packetId == FINISH_CONFIGURATION_PACKET_ID) {
            LOG.info("Finish Configuration received - acknowledging and switching to Play");
            PacketWriter.write(ctx, ACKNOWLEDGE_FINISH_CONFIGURATION_PACKET_ID, buf -> { });
            state = State.PLAY;
        }

        resetQuietDeadline(ctx);
    }

    private void handlePlay(int packetId, ByteBuf frame) {
        logAndRecord("PLAY", packetId, frame);
    }

    private void logAndRecord(String stateName, int packetId, ByteBuf frame) {
        int payloadLength = frame.readableBytes();
        // Temporary diagnostic: dump raw bytes so unrecognized packets can be identified by
        // content instead of guessed at. Trim once the relevant spec section is filled in.
        LOG.info("{} packet: id=0x{} payloadLength={} bytes=[{}]",
                stateName, Integer.toHexString(packetId), payloadLength, ByteBufUtil.hexDump(frame));
        observed.add(new ObservedPacket(stateName, packetId, payloadLength));
    }

    private void resetQuietDeadline(ChannelHandlerContext ctx) {
        if (quietDeadline != null) {
            quietDeadline.cancel(false);
        }
        quietDeadline = ctx.executor().schedule(() -> {
            LOG.info("Quiet for {} ms in state {} with {} packet(s) seen total", QUIET_PERIOD_MILLIS, state, observed.size());
            result.complete(List.copyOf(observed));
            ctx.close();
        }, QUIET_PERIOD_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        result.completeExceptionally(cause);
        ctx.close();
    }
}
