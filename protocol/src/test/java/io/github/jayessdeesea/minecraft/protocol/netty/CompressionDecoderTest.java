package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.zip.Deflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Note this decoder receives content with the outer frame length prefix already stripped -
 * that's {@link FrameDecoder}'s job, tested separately - so these tests feed it
 * {@code [Data Length][body]} directly, per docs/protocol-spec.md § 2.2.
 */
class CompressionDecoderTest {

    @Test
    void passesThroughUncompressedBodyBelowThreshold() {
        CompressionDecoder decoder = new CompressionDecoder();
        decoder.setCompressionThreshold(256);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        byte[] body = {0x00, 'h', 'i'};
        // Data Length 0 means "sent uncompressed despite compression being enabled" (§ 2.2).
        channel.writeInbound(content(out -> {
            ProtocolCodec.writeVarInt(out, 0);
            out.writeBytes(body);
        }));

        assertArrayEquals(body, readAll(channel.readInbound()));
    }

    @Test
    void decompressesARealCompressedBody() {
        byte[] plaintext = "hello, minecraft".getBytes();
        byte[] compressed = deflate(plaintext);

        CompressionDecoder decoder = new CompressionDecoder();
        decoder.setCompressionThreshold(0);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        channel.writeInbound(content(out -> {
            ProtocolCodec.writeVarInt(out, plaintext.length);
            out.writeBytes(compressed);
        }));

        assertArrayEquals(plaintext, readAll(channel.readInbound()));
    }

    @Test
    void rejectsAnImplausiblyLargeDeclaredDataLengthInsteadOfAllocatingIt() {
        // A "compression bomb": a tiny message declaring a huge decompressed size. Must be
        // rejected before any allocation sized by it - docs/protocol-spec.md § 4.3/§ 8.
        CompressionDecoder decoder = new CompressionDecoder();
        decoder.setCompressionThreshold(0);
        EmbeddedChannel channel = new EmbeddedChannel(decoder);

        ByteBuf bomb = content(out -> ProtocolCodec.writeVarInt(out, ProtocolCodec.MAX_FRAME_LENGTH + 1));

        assertThrows(DecoderException.class, () -> channel.writeInbound(bomb));
    }

    private static ByteBuf content(Consumer<ByteBuf> writer) {
        ByteBuf buf = Unpooled.buffer();
        writer.accept(buf);
        return buf;
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(input);
            deflater.finish();
            byte[] buffer = new byte[Math.max(64, input.length)];
            int length = deflater.deflate(buffer);
            byte[] result = new byte[length];
            System.arraycopy(buffer, 0, result, 0, length);
            return result;
        } finally {
            deflater.end();
        }
    }

    private static byte[] readAll(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }
}
