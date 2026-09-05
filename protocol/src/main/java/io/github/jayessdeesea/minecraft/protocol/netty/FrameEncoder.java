package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Prepends the VarInt length prefix (docs/protocol-spec.md § 2.1/2.2) to an already-assembled
 * frame body. Always the plain structure; {@link CompressionEncoder} - earlier in the outbound
 * chain, since Netty processes outbound handlers in reverse registration order - has already
 * applied § 2.2's Data Length field and zlib if enabled.
 */
public final class FrameEncoder extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        ProtocolCodec.writeVarInt(out, msg.readableBytes());
        out.writeBytes(msg);
    }
}
