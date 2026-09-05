package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Strips the VarInt length prefix (docs/protocol-spec.md § 2.1/2.2) and emits exactly one
 * complete packet body (Packet ID + Data) per frame, as a {@link ByteBuf}. Rewinds and waits for
 * more bytes when the length VarInt or the body hasn't fully arrived yet - the only place in this
 * module that has to deal with TCP's arbitrary chunking.
 *
 * <p>{@link #setCompressionThreshold} switches this decoder from the plain structure (§ 2.1) to
 * the compressed one (§ 2.2) from that point on. It's safe to flip mid-stream even if more
 * already-buffered frames are waiting behind the packet that triggered it: Netty's
 * {@code ByteToMessageDecoder} fires each decoded frame down the pipeline (running whatever
 * handler reacts to Set Compression) before calling {@code decode()} again for the next one, so
 * this field is always read fresh per frame - no separate synchronization needed.
 */
public final class VarIntFrameDecoder extends ByteToMessageDecoder {

    /** 2 MiB - generous for Status/Login/Configuration; revisit once large Play packets (chunk data) exist. */
    private static final int MAX_FRAME_LENGTH = 1 << 21;

    private volatile int compressionThreshold = -1;

    public void setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

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

        if (length < 0 || length > MAX_FRAME_LENGTH) {
            throw new IllegalStateException("Frame length " + length + " outside allowed range");
        }
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        ByteBuf frame = in.readRetainedSlice(length);
        out.add(compressionThreshold < 0 ? frame : decompress(frame));
    }

    private static ByteBuf decompress(ByteBuf compressedFrame) {
        int dataLength = ProtocolCodec.readVarInt(compressedFrame);
        if (dataLength == 0) {
            // Below the server's threshold - sent uncompressed despite compression being enabled
            // overall (docs/protocol-spec.md § 2.2). Ownership of compressedFrame passes to the
            // caller unchanged; nothing to release here.
            return compressedFrame;
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
        } finally {
            // Only reached on the dataLength > 0 path - we've copied what we need out of it.
            compressedFrame.release();
        }
    }
}
