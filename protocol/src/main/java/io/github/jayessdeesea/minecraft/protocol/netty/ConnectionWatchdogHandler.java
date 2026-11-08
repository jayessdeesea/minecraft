package io.github.jayessdeesea.minecraft.protocol.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reacts to the {@link io.netty.handler.timeout.IdleStateHandler} registered immediately ahead of
 * it in the pipeline (docs/protocol-pipeline.md): if no inbound frame has arrived within the
 * configured reader-idle window, the connection is presumed dead - a socket that's still
 * technically open but has stopped actually talking to us (server hung, network partition) - and
 * closed outright. This is the same "terminate, don't degrade" policy docs/conventions.md's no-fallbacks
 * tenet already applies to the module's size-based resource-exhaustion guards (oversized frame,
 * compression bomb): a silently-stalled connection is a violated invariant (vanilla sends Keep
 * Alive every ~15s - see {@code KeepAliveRequestPacket}), not something safe to keep waiting on
 * indefinitely.
 *
 * <p>Closing here is deliberately the full extent of this handler's job - it doesn't decide what
 * happens next (reconnect, give up, alert someone). That decision belongs one layer up: closing
 * the channel triggers {@code channelInactive} in whichever control handler is registered, which is
 * what actually surfaces the failure outward (for {@link PlayClientHandler}, via its
 * {@code onDisconnect} listener - see {@code PlaySessionListeners}).
 */
public final class ConnectionWatchdogHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LogManager.getLogger(ConnectionWatchdogHandler.class);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) {
        if (event instanceof IdleStateEvent idleEvent && idleEvent.state() == IdleState.READER_IDLE) {
            LOG.warn("No inbound traffic for the configured idle window - closing (server unresponsive or connection silently dead)");
            ctx.close();
        } else {
            ctx.fireUserEventTriggered(event);
        }
    }
}
