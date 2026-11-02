package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerChatPacketTest {

    private static final UUID JAKE = UUID.fromString("0a9b5e0c-2d8e-3a51-9c3f-6f1b2a4d7e10");

    private static ByteBuf chat(boolean signed, String message) {
        ByteBuf in = Unpooled.buffer();
        ProtocolCodec.writeVarInt(in, 42);    // global index
        ProtocolCodec.writeUuid(in, JAKE);
        ProtocolCodec.writeVarInt(in, 0);     // index
        in.writeBoolean(signed);
        if (signed) {
            in.writeZero(256);
        }
        ProtocolCodec.writeString(in, message);
        in.writeLong(1_700_000_000_000L);     // timestamp, salt, last seen... left unread
        in.writeLong(7);
        ProtocolCodec.writeVarInt(in, 0);
        return in;
    }

    @Test
    void readsSenderAndMessageWhetherSignedOrNot() {
        assertEquals(new PlayerChatPacket(JAKE, "!mission take the hill"), PlayerChatPacket.decode(chat(false, "!mission take the hill")));
        assertEquals(new PlayerChatPacket(JAKE, "hello"), PlayerChatPacket.decode(chat(true, "hello")));
    }

    @Test
    void rejectsAMessageLongerThanTheServerCouldSend() {
        assertThrows(IllegalStateException.class, () -> PlayerChatPacket.decode(chat(false, "x".repeat(257))));
    }
}
