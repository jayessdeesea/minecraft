package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.ObservedPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.FinishConfigurationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginSuccessPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.SetCompressionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the connection's {@link ProtocolState} and everything that follows from it mechanically -
 * compression on/off, which {@link PacketMarshaller} is active - but never sends a single packet.
 * Sits right after {@link MarshallingDecoder} and observes every decoded packet, reacting to the
 * three that require no decision (Set Compression, Login Success, Finish Configuration all imply
 * a state or compression change with no choice involved), then unconditionally forwards every
 * packet downstream via {@link #channelRead0} calling {@code ctx.fireChannelRead} - the
 * session-manager handler further down the pipeline (e.g. {@link StatusClientHandler},
 * {@link JoinClientHandler}) is what actually replies to things.
 *
 * <p>The one transition with no inbound trigger - HANDSHAKE -&gt; LOGIN/STATUS, since Handshake
 * has no response at all - can't happen autonomously; whoever sends the Handshake packet calls
 * {@link #transitionTo} once, explicitly, from {@code channelActive}.
 */
public final class SessionStateManager extends SimpleChannelInboundHandler<Packet> {

    private static final Logger LOG = LogManager.getLogger(SessionStateManager.class);

    private ProtocolState state = ProtocolState.HANDSHAKE;
    private final List<ObservedPacket> observed = new ArrayList<>();
    private final Set<String> loggedUnknownKinds = new HashSet<>();

    public ProtocolState state() {
        return state;
    }

    /** Snapshot of every {@link UnknownPacket} seen so far, for a session-manager handler to report on. */
    public List<ObservedPacket> observed() {
        return List.copyOf(observed);
    }

    public void transitionTo(ChannelHandlerContext ctx, ProtocolState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal transition " + state + " -> " + next);
        }
        LOG.info("State transition: {} -> {}", state, next);
        state = next;
        ctx.pipeline().get(MarshallingDecoder.class).setMarshaller(Marshallers.forState(next));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        if (packet instanceof SetCompressionPacket setCompression) {
            LOG.info("Set Compression: threshold={}", setCompression.threshold());
            ctx.pipeline().get(CompressionDecoder.class).setCompressionThreshold(setCompression.threshold());
            ctx.pipeline().get(CompressionEncoder.class).setCompressionThreshold(setCompression.threshold());
        } else if (packet instanceof LoginSuccessPacket) {
            transitionTo(ctx, ProtocolState.CONFIGURATION);
        } else if (packet instanceof FinishConfigurationPacket) {
            transitionTo(ctx, ProtocolState.PLAY);
        } else if (packet instanceof UnknownPacket unknown) {
            recordAndLogOnce(unknown);
        }

        ctx.fireChannelRead(packet);
    }

    private void recordAndLogOnce(UnknownPacket unknown) {
        observed.add(new ObservedPacket(state.name(), unknown.packetId(), unknown.data().length));
        // Log the full hex dump only the first time a given (state, packet ID) combination is
        // seen - identifying Configuration's packets meant decoding raw bytes by hand
        // (docs/protocol-spec.md § 3.4), so keep that possible for whatever's still unrecognized.
        // But Play alone can send the same unrecognized ID hundreds of times a second (confirmed:
        // ~1400 in a 2-second window), so repeats are only counted in `observed`, not re-logged.
        String kind = state + " 0x" + Integer.toHexString(unknown.packetId());
        if (loggedUnknownKinds.add(kind)) {
            LOG.warn("Unrecognized packet {} ({} byte(s)): {}", kind, unknown.data().length,
                    ByteBufUtil.hexDump(unknown.data()));
        }
    }
}
