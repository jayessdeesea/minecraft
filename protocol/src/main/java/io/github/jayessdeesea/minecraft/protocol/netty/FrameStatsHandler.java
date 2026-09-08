package io.github.jayessdeesea.minecraft.protocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Counts frames, not raw bytes - registered directly after {@link FrameDecoder}/{@link
 * FrameEncoder} (docs/protocol-pipeline.md's "Frame stats" tier) specifically so it always sees
 * one complete, already-delimited frame per event in both directions. Sitting any closer to the
 * socket would mean re-detecting frame boundaries itself - that windowing is {@link
 * FrameDecoder}'s job, already done by the time this handler sees anything.
 *
 * <p>Per-frame counts are logged at DEBUG - Play alone can carry hundreds of frames/second once
 * more of its packet catalog is decoded, and that volume shouldn't flood INFO by default (the same
 * reasoning {@link SessionStateManager} already applies to unrecognized-packet logging). Connect
 * and disconnect are lifecycle events, not per-frame noise, so those stay at INFO.
 */
public final class FrameStatsHandler extends ChannelDuplexHandler {

    private static final Logger LOG = LogManager.getLogger(FrameStatsHandler.class);

    private long framesReceived;
    private long framesSent;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOG.info("Connected: {}", ctx.channel().remoteAddress());
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LOG.info("Disconnected: {} ({} frame(s) received, {} sent)", ctx.channel().remoteAddress(),
                framesReceived, framesSent);
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf frame) {
            framesReceived++;
            LOG.debug("Received frame {} ({} byte(s))", framesReceived, frame.readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf frame) {
            framesSent++;
            LOG.debug("Sending frame {} ({} byte(s))", framesSent, frame.readableBytes());
        }
        ctx.write(msg, promise);
    }
}
