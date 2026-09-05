package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ProtocolCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameDecoderTest {

    @Test
    void emitsOneFrameFromAWholeMessage() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder());
        byte[] body = {0x01, 0x02, 0x03};

        channel.writeInbound(frame(body));

        assertArrayEquals(body, readAll(channel.readInbound()));
    }

    @Test
    void waitsForMoreBytesWhenTheFrameArrivesSplitAcrossReads() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder());
        byte[] body = {0x01, 0x02, 0x03, 0x04, 0x05};
        ByteBuf whole = frame(body);
        ByteBuf firstHalf = whole.readRetainedSlice(2);
        ByteBuf secondHalf = whole.readRetainedSlice(whole.readableBytes());
        whole.release();

        channel.writeInbound(firstHalf);
        assertNull(channel.readInbound(), "should not emit a frame until the rest of it arrives");

        channel.writeInbound(secondHalf);
        assertArrayEquals(body, readAll(channel.readInbound()));
    }

    @Test
    void rejectsAFrameLongerThanTheMaximum() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameDecoder());
        ByteBuf oversized = Unpooled.buffer();
        ProtocolCodec.writeVarInt(oversized, ProtocolCodec.MAX_FRAME_LENGTH + 1);

        assertThrows(DecoderException.class, () -> channel.writeInbound(oversized));
    }

    private static ByteBuf frame(byte[] body) {
        ByteBuf frame = Unpooled.buffer();
        ProtocolCodec.writeVarInt(frame, body.length);
        frame.writeBytes(body);
        return frame;
    }

    private static byte[] readAll(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }
}
