package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * Reads the packet ID (docs/protocol-spec.md § 2.1) and hands the rest of the frame to whichever
 * {@link PacketMarshaller} is currently active - swapped by the session-manager handler on every
 * state transition (see {@link ProtocolState}). This class itself never knows or asks what state
 * that is; it starts pointed at {@link HandshakeMarshaller} to match the connection's initial
 * state.
 */
public final class MarshallingDecoder extends MessageToMessageDecoder<ByteBuf> {

    private volatile PacketMarshaller marshaller = new HandshakeMarshaller();

    public void setMarshaller(PacketMarshaller marshaller) {
        this.marshaller = marshaller;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        int packetId = ProtocolCodec.readVarInt(frame);
        out.add(marshaller.decode(packetId, frame));
    }
}
