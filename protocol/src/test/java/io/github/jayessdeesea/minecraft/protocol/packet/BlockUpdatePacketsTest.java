package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.BlockChange;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockUpdatePacketsTest {

    @Test
    void aBlockUpdateUnpacksANegativePosition() {
        ByteBuf in = Unpooled.buffer();
        int x = -37, y = -12, z = 1025;
        in.writeLong(((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF));
        ProtocolCodec.writeVarInt(in, 0); // air: the block was broken

        assertEquals(List.of(new BlockChange(-37, -12, 1025, 0)), BlockUpdatePackets.decodeBlockUpdate(in).changes());
    }

    @Test
    void aSectionUpdateUnpacksEachBlock() {
        ByteBuf in = Unpooled.buffer();
        int sx = -2, sy = 4, sz = 3;
        in.writeLong(((long) (sx & 0x3FFFFF) << 42) | ((long) (sz & 0x3FFFFF) << 20) | (sy & 0xFFFFF));
        ProtocolCodec.writeVarInt(in, 2);
        ProtocolCodec.writeVarLong(in, (long) 137 << 12 | 1 << 8 | 2 << 4 | 3);   // local (1, 3, 2)
        ProtocolCodec.writeVarLong(in, (long) 9 << 12 | 15 << 8 | 0 << 4 | 15);   // local (15, 15, 0)

        assertEquals(List.of(new BlockChange(-31, 67, 50, 137), new BlockChange(-17, 79, 48, 9)),
                BlockUpdatePackets.decodeSectionUpdate(in).changes());
    }
}
