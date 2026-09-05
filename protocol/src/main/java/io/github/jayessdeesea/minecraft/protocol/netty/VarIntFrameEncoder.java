package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;

/**
 * Prepends the VarInt length prefix (docs/protocol-spec.md § 2.1/2.2) to an already-assembled
 * packet body (Packet ID + Data). Callers just write a complete body and flush it; this is the
 * only handler on the outbound side that knows about the length prefix, and (once
 * {@link #setCompressionThreshold} has been called) the Data Length field and zlib compression.
 */
public final class VarIntFrameEncoder extends MessageToByteEncoder<ByteBuf> {

    private volatile int compressionThreshold = -1;

    public void setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        if (compressionThreshold < 0) {
            ProtocolCodec.writeVarInt(out, msg.readableBytes());
            out.writeBytes(msg);
            return;
        }

        int dataLength = msg.readableBytes();
        ByteBuf body = ctx.alloc().buffer();
        try {
            if (dataLength < compressionThreshold) {
                ProtocolCodec.writeVarInt(body, 0);
                body.writeBytes(msg);
            } else {
                ProtocolCodec.writeVarInt(body, dataLength);
                body.writeBytes(compress(msg));
            }
            ProtocolCodec.writeVarInt(out, body.readableBytes());
            out.writeBytes(body);
        } finally {
            body.release();
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
