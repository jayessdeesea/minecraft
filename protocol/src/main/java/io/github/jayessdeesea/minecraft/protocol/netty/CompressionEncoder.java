package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

/**
 * Applies docs/protocol-spec.md § 2.2's Data Length field and zlib compression once
 * {@link #setCompressionThreshold} has been set. Before that, every outbound body passes through
 * with no Data Length field at all - that's {@link FrameEncoder}'s plain § 2.1 structure.
 */
public final class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {

    private volatile int compressionThreshold = -1;

    public void setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        if (compressionThreshold < 0) {
            out.writeBytes(msg);
            return;
        }

        int dataLength = msg.readableBytes();
        if (dataLength < compressionThreshold) {
            ProtocolCodec.writeVarInt(out, 0);
            out.writeBytes(msg);
        } else {
            ProtocolCodec.writeVarInt(out, dataLength);
            out.writeBytes(compress(msg));
        }
    }

    private static byte[] compress(ByteBuf msg) {
        byte[] uncompressed = new byte[msg.readableBytes()];
        msg.readBytes(uncompressed);

        Deflater deflater = new Deflater();
        try {
            deflater.setInput(uncompressed);
            deflater.finish();
            byte[] buffer = new byte[Math.max(64, uncompressed.length)];
            ByteArrayOutputStream compressed = new ByteArrayOutputStream(buffer.length);
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                compressed.write(buffer, 0, count);
            }
            return compressed.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
