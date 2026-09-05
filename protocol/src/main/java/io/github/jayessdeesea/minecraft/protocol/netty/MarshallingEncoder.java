package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Writes a packet's ID (docs/protocol-spec.md § 2.1) followed by its own body. Stateless, and the
 * same regardless of connection state: unlike decoding, encoding a {@link Packet} is never
 * ambiguous - the caller already knows exactly which concrete type it's sending.
 */
public final class MarshallingEncoder extends MessageToByteEncoder<Packet> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) {
        ProtocolCodec.writeVarInt(out, packet.packetId());
        packet.encode(out);
    }
}
