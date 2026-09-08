package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logs every recognized, typed {@link Packet} that crosses this connection, in both directions
 * (docs/protocol-pipeline.md's "Packet logging" tier). Registered directly after {@link
 * MarshallingEncoder} specifically so it operates on the real {@link Packet} type - what
 * happened - not raw bytes - how many bytes happened, which is {@link FrameStatsHandler}'s job, a
 * tier below. Centralizes what used to be scattered {@code LOG.info} calls inside individual
 * control handlers.
 *
 * <p>{@link UnknownPacket} is deliberately excluded here - {@link SessionStateManager} already
 * logs those at WARN, deduplicated once per distinct (state, packet ID) kind, since Play alone can
 * repeat the same unrecognized ID hundreds of times a second. Logging every occurrence again here
 * would just be a second, uncoordinated copy of that same noise problem.
 *
 * <p>Per-packet logs are DEBUG, not INFO, for the same reason {@link FrameStatsHandler}'s are:
 * once more of Play's catalog is decoded, packet volume can be high, and that shouldn't flood INFO
 * by default.
 */
public final class PacketLoggingHandler extends ChannelDuplexHandler {

    private static final Logger LOG = LogManager.getLogger(PacketLoggingHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet packet && !(packet instanceof UnknownPacket)) {
            LOG.debug("Received {}", packet.getClass().getSimpleName());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof Packet packet) {
            LOG.debug("Sending {}", packet.getClass().getSimpleName());
        }
        ctx.write(msg, promise);
    }
}
