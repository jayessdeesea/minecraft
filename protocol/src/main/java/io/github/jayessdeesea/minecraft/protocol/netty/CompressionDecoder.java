package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Applies docs/protocol-spec.md § 2.2's Data Length field and zlib inflation once
 * {@link #setCompressionThreshold} has been called with a non-negative value - the session-manager
 * handler calls this in response to Set Compression (docs/protocol-spec.md § 4.1). Before that,
 * every frame passes through unchanged; most of a connection's life (Handshake/Status, and Login
 * up until Set Compression arrives) has compression disabled.
 *
 * <p>{@code MessageToMessageDecoder} always releases its input frame once {@code decode()}
 * returns, regardless of what happened inside it - unlike the old merged frame+compression
 * handler this replaced, code here must {@code retain()} the input explicitly on any path where
 * the same object (not a newly allocated one) is what gets passed downstream.
 */
public final class CompressionDecoder extends MessageToMessageDecoder<ByteBuf> {

    private volatile int compressionThreshold = -1;

    public void setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf frame, List<Object> out) {
        if (compressionThreshold < 0) {
            out.add(frame.retain());
            return;
        }
        out.add(decompress(frame));
    }

    private static ByteBuf decompress(ByteBuf compressedFrame) {
        int dataLength = ProtocolCodec.readVarInt(compressedFrame);
        if (dataLength == 0) {
            // Below the server's threshold - sent uncompressed despite compression being enabled
            // overall (docs/protocol-spec.md § 2.2).
            return compressedFrame.retain();
        }
        if (dataLength < 0 || dataLength > ProtocolCodec.MAX_FRAME_LENGTH) {
            // Guard against a "compression bomb": a small compressed frame declaring a huge
            // decompressed size would otherwise allocate that size unconditionally, before we've
            // verified a single byte of it (docs/protocol-spec.md § 4.3/§ 8).
            throw new IllegalStateException("Declared Data Length " + dataLength
                    + " outside allowed range (max " + ProtocolCodec.MAX_FRAME_LENGTH + ")");
        }

        try {
            byte[] compressedBytes = new byte[compressedFrame.readableBytes()];
            compressedFrame.readBytes(compressedBytes);

            Inflater inflater = new Inflater();
            try {
                inflater.setInput(compressedBytes);
                byte[] decompressedBytes = new byte[dataLength];
                int actualLength = inflater.inflate(decompressedBytes);
                if (actualLength != dataLength) {
                    throw new IllegalStateException("Decompressed size " + actualLength
                            + " did not match declared Data Length " + dataLength);
                }
                return Unpooled.wrappedBuffer(decompressedBytes);
            } finally {
                inflater.end();
            }
        } catch (DataFormatException e) {
            throw new IllegalStateException("Failed to decompress packet", e);
        }
    }
}
