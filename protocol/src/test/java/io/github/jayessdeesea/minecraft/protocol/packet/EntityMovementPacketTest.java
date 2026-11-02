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

    /** {@code ClientboundMoveEntityPacket.packProperties}: bit 0 on ground, the rest the step count. */
    private static int properties(boolean onGround, int steps) {
        return (onGround ? 1 : 0) | steps << 1;
    }

    @Test
    void decodesMoveEntityPos() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        ProtocolCodec.writeVarInt(buf, properties(true, 0));
        buf.writeShort(4096).writeShort(-2048).writeShort(1);

        assertEquals(new MoveEntityPacket(0x36, 42, 4096, -2048, 1), MoveEntityPacket.decodePos(buf));
    }

    @Test
    void decodesMoveEntityPosRotSkippingRotation() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        ProtocolCodec.writeVarInt(buf, properties(false, 0));
        buf.writeShort(10).writeShort(20).writeShort(30);
        buf.writeByte(5).writeByte(6);

        assertEquals(new MoveEntityPacket(0x37, 42, 10, 20, 30), MoveEntityPacket.decodePosRot(buf));
    }

    @Test
    void aSteppedMoveEndsAtTheSumOfItsSteps() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        ProtocolCodec.writeVarInt(buf, properties(true, 2));
        ProtocolCodec.writeVarInt(buf, 1); // ticks
        buf.writeShort(30000).writeShort(0).writeShort(-5);
        ProtocolCodec.writeVarInt(buf, 1);
        buf.writeShort(30000).writeShort(10).writeShort(-5);

        assertEquals(new MoveEntityPacket(0x36, 42, 60000, 10, -10), MoveEntityPacket.decodePos(buf));
    }

    @Test
    void rejectsMoreStepsThanThePacketCanHold() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        ProtocolCodec.writeVarInt(buf, properties(true, 1_000_000));
        buf.writeShort(0).writeShort(0).writeShort(0);

        assertThrows(IllegalStateException.class, () -> MoveEntityPacket.decodePos(buf));
    }

    @Test
    void rejectsTrailingBytesAfterAMove() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 42);
        ProtocolCodec.writeVarInt(buf, properties(true, 0));
        buf.writeShort(0).writeShort(0).writeShort(0).writeByte(0);

        assertThrows(IllegalStateException.class, () -> MoveEntityPacket.decodePos(buf));
    }

    @Test
    void changesAreFourThousandNinetySixthsOfABlockFromAQuantizedBase() {
        assertEquals(11.0, MoveEntityPacket.apply(10.0, 4096));
        assertEquals(9.5, MoveEntityPacket.apply(10.0, -2048));
        // The base is rounded to the 1/4096 grid first, exactly like VecDeltaCodec.decode.
        assertEquals((Math.round(0.3 * 4096) + 1) / 4096.0, MoveEntityPacket.apply(0.3, 1));
    }

    @Test
    void decodesALinearPositionSyncAsAbsolute() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 7);
        ProtocolCodec.writeVarInt(buf, 0); // linear
        buf.writeDouble(1.5).writeDouble(64.0).writeDouble(-2.25);
        buf.writeFloat(90f).writeFloat(0f).writeBoolean(true);

        EntityPositionPacket packet = EntityPositionPacket.decodeSync(buf);

        assertEquals(new EntityPositionPacket(0x23, 7, 1.5, 64.0, -2.25, 0), packet);
        assertFalse(packet.isRelative(0));
    }

    @Test
    void aSteppedPositionSyncEndsAtItsLastPoint() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 7);
        ProtocolCodec.writeVarInt(buf, 1); // stepped
        ProtocolCodec.writeVarInt(buf, 2);
        buf.writeDouble(1).writeDouble(64).writeDouble(1);
        ProtocolCodec.writeVarInt(buf, 1);
        buf.writeDouble(2).writeDouble(65).writeDouble(3);
        ProtocolCodec.writeVarInt(buf, 1);
        buf.writeFloat(0f).writeFloat(0f).writeBoolean(false);

        assertEquals(new EntityPositionPacket(0x23, 7, 2, 65, 3, 0), EntityPositionPacket.decodeSync(buf));
    }

    @Test
    void decodesTeleportWithRelativeFlags() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, 7);
        buf.writeDouble(1.0).writeDouble(0.0).writeDouble(0.0);
        buf.writeDouble(0).writeDouble(0).writeDouble(0); // velocity
        buf.writeFloat(90f).writeFloat(0f);                // yRot, xRot
        buf.writeInt(0b001); // x relative
        buf.writeBoolean(true);

        EntityPositionPacket packet = EntityPositionPacket.decodeTeleport(buf);

        assertTrue(packet.isRelative(0));
        assertFalse(packet.isRelative(1));
        assertFalse(packet.isRelative(2));
    }
}
