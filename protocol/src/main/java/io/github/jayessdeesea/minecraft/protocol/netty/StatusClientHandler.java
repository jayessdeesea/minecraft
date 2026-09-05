package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.StatusResult;
import io.github.jayessdeesea.minecraft.protocol.packet.HandshakePacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PingPongPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.StatusRequestPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.StatusResponsePacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Control handler for a Status-only connection: sends Handshake -&gt; Status Request -&gt; (once
 * Status Response arrives) Ping Request, and completes {@code result} on the Pong Response
 * (docs/protocol-spec.md § 3.2). {@link SessionStateManager}, earlier in the pipeline, owns the
 * HANDSHAKE -&gt; STATUS transition and unknown-packet logging - this class holds no state of its
 * own beyond the in-flight JSON it's waiting to pair with a round-trip time.
 */
public final class StatusClientHandler extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOG = LogManager.getLogger(StatusClientHandler.class);

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
        ctx.writeAndFlush(new HandshakePacket(protocolVersion, host, port, HandshakePacket.NEXT_STATE_STATUS));
        ctx.pipeline().get(SessionStateManager.class).transitionTo(ctx, ProtocolState.STATUS);
        ctx.writeAndFlush(new StatusRequestPacket());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof StatusResponsePacket response && statusJson == null) {
            statusJson = response.json();
            ctx.writeAndFlush(new PingPongPacket(System.nanoTime()));
        } else if (packet instanceof PingPongPacket pong) {
            long roundTripMillis = (System.nanoTime() - pong.payload()) / 1_000_000;
            result.complete(new StatusResult(statusJson, roundTripMillis));
            ctx.close();
        } else {
            LOG.warn("Unhandled recognized packet {}", packet);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // See JoinClientHandler's exceptionCaught for why this is always logged, not just handed
        // to the future.
        LOG.error("Connection failed, disconnecting: {}", cause.getMessage(), cause);
        result.completeExceptionally(cause);
        ctx.close();
    }
}
