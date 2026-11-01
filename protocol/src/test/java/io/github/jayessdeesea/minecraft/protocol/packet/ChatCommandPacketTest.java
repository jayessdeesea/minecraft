package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatCommandPacketTest {

    @Test
    void encodesTheCommandAsOneLengthPrefixedString() {
        ByteBuf out = Unpooled.buffer();

        new ChatCommandPacket("me hi").encode(out);

        assertEquals("056d65206869", ByteBufUtil.hexDump(out));
    }

    @Test
    void refusesCharactersTheServerKicksFor() {
        assertThrows(IllegalArgumentException.class, () -> new ChatCommandPacket("me two\nlines"));
        assertThrows(IllegalArgumentException.class, () -> new ChatCommandPacket("me §cred"));
    }
}
