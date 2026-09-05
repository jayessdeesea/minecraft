package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Strips the VarInt length prefix (docs/protocol-spec.md § 2.1/2.2) and emits exactly one
 * complete frame per invocation, as a {@link ByteBuf}. Rewinds and waits for more bytes when the
 * length VarInt or the body hasn't fully arrived yet - the only place in this module that has to
 * deal with TCP's arbitrary chunking. Always the plain structure; compression (§ 2.2's extra Data
 * Length field and zlib) is {@link CompressionDecoder}'s job, downstream of this one.
 */
public final class FrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();

        int length;
        try {
            length = ProtocolCodec.readVarInt(in);
        } catch (IndexOutOfBoundsException incompleteVarInt) {
            in.resetReaderIndex();
            return;
        }

        if (length < 0 || length > ProtocolCodec.MAX_FRAME_LENGTH) {
            throw new IllegalStateException("Frame length " + length + " outside allowed range");
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        out.add(in.readRetainedSlice(length));
    }
}
