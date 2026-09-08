package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-built bytes matching the format confirmed by decompiling the real server jar (see this
 * package's {@code PlayerPositionPacket} javadoc) - not a symmetric round-trip, since {@code
 * encode} deliberately doesn't exist for this clientbound-only packet.
 */
class PlayerPositionPacketTest {

    @Test
    void decodesTeleportIdPositionAndSkipsVelocityAndRotation() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 7);   // teleport ID
        buf.writeDouble(4.5);                // x
        buf.writeDouble(81.0);                // y
        buf.writeDouble(42.5);               // z
        buf.writeDouble(0.0).writeDouble(0.0).writeDouble(0.0); // delta movement - unread, must still align
        buf.writeFloat(90.0f);               // yRot
        buf.writeFloat(0.0f);                // xRot
        buf.writeInt(0);                     // relative flags - none set, all fields absolute

        PlayerPositionPacket packet = PlayerPositionPacket.decode(buf);

        assertEquals(7, packet.teleportId());
        assertEquals(4.5, packet.x());
        assertEquals(81.0, packet.y());
        assertEquals(42.5, packet.z());
        assertEquals(90.0f, packet.yRot());
        assertEquals(0, packet.relativeFlags());
        assertEquals(0, buf.readableBytes(), "decode should consume exactly what was written");
    }
}
