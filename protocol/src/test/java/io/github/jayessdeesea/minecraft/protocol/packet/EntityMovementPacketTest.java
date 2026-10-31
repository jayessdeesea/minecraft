package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Layouts per {@link MoveEntityPacket}/{@link EntityPositionPacket} javadoc, confirmed with javap. */
class EntityMovementPacketTest {

    @Test
    void decodesMoveEntityPos() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        buf.writeShort(4096).writeShort(-2048).writeShort(1);
        buf.writeBoolean(true);

        assertEquals(new MoveEntityPacket(0x35, 42, (short) 4096, (short) -2048, (short) 1), MoveEntityPacket.decodePos(buf));
    }

    @Test
    void decodesMoveEntityPosRotSkippingRotation() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        buf.writeShort(10).writeShort(20).writeShort(30);
        buf.writeByte(5).writeByte(6);
        buf.writeBoolean(false);

        assertEquals(new MoveEntityPacket(0x36, 42, (short) 10, (short) 20, (short) 30), MoveEntityPacket.decodePosRot(buf));
    }

    @Test
    void rejectsTrailingBytesAfterAMove() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        buf.writeShort(0).writeShort(0).writeShort(0).writeBoolean(true).writeByte(0);

        assertThrows(IllegalStateException.class, () -> MoveEntityPacket.decodePos(buf));
    }

    @Test
    void changesAreFourThousandNinetySixthsOfABlockFromAQuantizedBase() {
        assertEquals(11.0, MoveEntityPacket.apply(10.0, (short) 4096));
        assertEquals(9.5, MoveEntityPacket.apply(10.0, (short) -2048));
        // The base is rounded to the 1/4096 grid first, exactly like VecDeltaCodec.decode.
        assertEquals((Math.round(0.3 * 4096) + 1) / 4096.0, MoveEntityPacket.apply(0.3, (short) 1));
    }

    @Test
    void decodesPositionSyncAsAbsolute() {
        ByteBuf buf = positionBody(7, 1.5, 64.0, -2.25);
        buf.writeBoolean(true);

        EntityPositionPacket packet = EntityPositionPacket.decodeSync(buf);

        assertEquals(new EntityPositionPacket(0x23, 7, 1.5, 64.0, -2.25, 0), packet);
        assertFalse(packet.isRelative(0));
    }

    @Test
    void decodesTeleportWithRelativeFlags() {
        ByteBuf buf = positionBody(7, 1.0, 0.0, 0.0);
        buf.writeInt(0b001); // x relative
        buf.writeBoolean(true);

        EntityPositionPacket packet = EntityPositionPacket.decodeTeleport(buf);

        assertTrue(packet.isRelative(0));
        assertFalse(packet.isRelative(1));
        assertFalse(packet.isRelative(2));
    }

    private static ByteBuf positionBody(int entityId, double x, double y, double z) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, entityId);
        buf.writeDouble(x).writeDouble(y).writeDouble(z);
        buf.writeDouble(0).writeDouble(0).writeDouble(0); // velocity
        buf.writeFloat(90f).writeFloat(0f);                // yRot, xRot
        return buf;
    }
}
