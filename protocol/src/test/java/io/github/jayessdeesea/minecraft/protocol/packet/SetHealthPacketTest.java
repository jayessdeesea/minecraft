package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBufUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetHealthPacketTest {

    /** The exact payload a dead Alice received live: health 0.0, food 19, saturation 0.0. */
    @Test
    void decodesTheRealDeadPlayerPayload() {
        ByteBuf buf = Unpooled.wrappedBuffer(ByteBufUtil.decodeHexDump("000000001300000000"));

        SetHealthPacket packet = SetHealthPacket.decode(buf);

        assertEquals(new SetHealthPacket(0.0f, 19, 0.0f), packet);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void clientCommandRespawnEncodesAsASingleZeroVarInt() {
        ByteBuf out = Unpooled.buffer();

        new ClientCommandPacket(ClientCommandPacket.PERFORM_RESPAWN).encode(out);

        assertEquals("00", ByteBufUtil.hexDump(out));
    }
}
