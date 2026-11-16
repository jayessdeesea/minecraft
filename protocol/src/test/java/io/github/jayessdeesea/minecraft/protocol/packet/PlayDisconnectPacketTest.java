package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayDisconnectPacketTest {

    @Test
    void aKickForSpammingIsReadAsItsTranslationKey() {
        // As the pinned server sent it, live: {translate: "disconnect.spam"}.
        ByteBuf in = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump("0a0800097472616e736c617465000f646973636f6e6e6563742e7370616d00"));

        assertEquals(new PlayDisconnectPacket("disconnect.spam"), PlayDisconnectPacket.decode(in));
    }
}
