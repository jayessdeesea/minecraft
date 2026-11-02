package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetTimePacketTest {

    @Test
    void decodesGameTimeAndEachClock() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeLong(123_456L);
        ProtocolCodec.writeVarInt(buf, 2);
        ProtocolCodec.writeVarInt(buf, 1);          // overworld clock, say
        ProtocolCodec.writeVarLong(buf, 3 * 24000L + 6000);
        buf.writeFloat(0.5f);                       // partial tick
        buf.writeFloat(1.0f);                       // rate
        ProtocolCodec.writeVarInt(buf, 0);
        ProtocolCodec.writeVarLong(buf, 0);
        buf.writeFloat(0f);
        buf.writeFloat(0f);

        SetTimePacket packet = SetTimePacket.decode(buf);

        assertEquals(new SetTimePacket(123_456L, Map.of(
                1, new TimeUpdate.Clock(3 * 24000L + 6000, 1.0f),
                0, new TimeUpdate.Clock(0, 0f))), packet);
        assertEquals(0, buf.readableBytes());
    }
}
