package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkStreamingPacketTest {

    /** Packed like {@code ChunkPos.pack}: x in the low 32 bits, z in the high 32 - negatives included. */
    @Test
    void unpacksNegativeChunkCoordinates() {
        int x = -5;
        int z = -300;
        ByteBuf buf = Unpooled.buffer().writeLong(((long) z << 32) | (x & 0xFFFFFFFFL));

        assertEquals(new ForgetLevelChunkPacket(-5, -300), ForgetLevelChunkPacket.decode(buf));
    }

    @Test
    void unpacksMixedSignChunkCoordinates() {
        ByteBuf buf = Unpooled.buffer().writeLong(((long) 7 << 32) | (-1 & 0xFFFFFFFFL));

        assertEquals(new ForgetLevelChunkPacket(-1, 7), ForgetLevelChunkPacket.decode(buf));
    }

    @Test
    void batchAcknowledgementIsABareFloat() {
        ByteBuf out = Unpooled.buffer();

        new ChunkBatchReceivedPacket(32f).encode(out);

        assertEquals("42000000", ByteBufUtil.hexDump(out));
    }
}
