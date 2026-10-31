package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoveEntitiesPacketTest {

    @Test
    void decodesCountPrefixedVarIntIds() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 3);
        ProtocolCodec.writeVarInt(buf, 5);
        ProtocolCodec.writeVarInt(buf, 300);
        ProtocolCodec.writeVarInt(buf, 70000);

        RemoveEntitiesPacket packet = RemoveEntitiesPacket.decode(buf);

        assertEquals(List.of(5, 300, 70000), packet.entityIds());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void rejectsACountLargerThanThePayloadCouldHold() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 1_000_000);
        ProtocolCodec.writeVarInt(buf, 1);

        assertThrows(IllegalStateException.class, () -> RemoveEntitiesPacket.decode(buf));
    }
}
