package io.github.jayessdeesea.minecraft.protocol.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FrameStatsHandlerTest {

    @Test
    void passesInboundFramesThroughUnchanged() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameStatsHandler());
        byte[] frame = {0x01, 0x02, 0x03};

        channel.writeInbound(Unpooled.wrappedBuffer(frame));

        assertArrayEquals(frame, readAll(channel.readInbound()));
    }

    @Test
    void passesOutboundFramesThroughUnchanged() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameStatsHandler());
        byte[] frame = {0x04, 0x05};

        channel.writeOutbound(Unpooled.wrappedBuffer(frame));

        assertArrayEquals(frame, readAll(channel.readOutbound()));
    }

    @Test
    void logsConnectAndDisconnectWithoutThrowing() {
        EmbeddedChannel channel = new EmbeddedChannel(new FrameStatsHandler());

        assertDoesNotThrow(() -> { channel.close(); });
    }

    private static byte[] readAll(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }
}
