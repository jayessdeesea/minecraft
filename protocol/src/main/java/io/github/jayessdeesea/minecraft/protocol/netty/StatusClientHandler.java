package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.StatusResult;
import io.github.jayessdeesea.minecraft.protocol.packet.HandshakePacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PingPongPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.github.jayessdeesea.minecraft.protocol.packet.StatusResponsePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Drives Handshake -&gt; Status Request -&gt; Status Response -&gt; Ping Request -&gt; Pong Response
 * (docs/protocol-spec.md § 3.2) on a freshly connected channel, then completes {@code result} and
 * closes the connection.
 */
public final class StatusClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static final Logger LOG = LogManager.getLogger(StatusClientHandler.class);

    /** Status Request (docs/protocol-spec.md § 3.2) has an empty body; only its ID matters. */
    private static final int STATUS_REQUEST_PACKET_ID = 0x00;

    private final int protocolVersion;
    private final String host;
    private final int port;
    private final CompletableFuture<StatusResult> result;

    private String statusJson;

    public StatusClientHandler(int protocolVersion, String host, int port, CompletableFuture<StatusResult> result) {
        this.protocolVersion = protocolVersion;
        this.host = host;
        this.port = port;
        this.result = result;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        PacketWriter.write(ctx, HandshakePacket.PACKET_ID, buf ->
                new HandshakePacket(protocolVersion, host, port, HandshakePacket.NEXT_STATE_STATUS).encode(buf));
        PacketWriter.write(ctx, STATUS_REQUEST_PACKET_ID, buf -> { });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf frame) {
        int packetId = ProtocolCodec.readVarInt(frame);
        if (packetId == StatusResponsePacket.PACKET_ID && statusJson == null) {
            statusJson = StatusResponsePacket.decode(frame).json();
            long sentAtNanos = System.nanoTime();
            PacketWriter.write(ctx, PingPongPacket.PACKET_ID, buf -> new PingPongPacket(sentAtNanos).encode(buf));
        } else if (packetId == PingPongPacket.PACKET_ID) {
            long sentAtNanos = PingPongPacket.decode(frame).payload();
            long roundTripMillis = (System.nanoTime() - sentAtNanos) / 1_000_000;
            result.complete(new StatusResult(statusJson, roundTripMillis));
            ctx.close();
        } else {
            LOG.warn("Unexpected packet ID {} in Status state", packetId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        result.completeExceptionally(cause);
        ctx.close();
    }
}
