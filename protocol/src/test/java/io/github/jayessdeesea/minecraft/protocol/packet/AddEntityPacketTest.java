package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hand-built bytes matching the layout confirmed against the real server jar (see
 * {@link AddEntityPacket}'s javadoc). Each test checks decode consumes exactly what was written -
 * the velocity field is variable-length, so a mis-sized skip would leave bytes behind or underflow.
 */
class AddEntityPacketTest {

    @Test
    void decodesIdTypeAndPositionWithZeroVelocity() {
        ByteBuf buf = header(42, 7, 10.5, 64.0, -3.25);
        buf.writeByte(0); // LpVec3 zero vector - one byte total
        trailer(buf);

        AddEntityPacket packet = AddEntityPacket.decode(buf);

        assertEquals(new AddEntityPacket(42, 7, 10.5, 64.0, -3.25), packet);
        assertEquals(0, buf.readableBytes(), "decode should consume exactly what was written");
    }

    @Test
    void skipsNonZeroVelocityWithoutContinuation() {
        ByteBuf buf = header(1, 2, 0, 0, 0);
        buf.writeByte(0b0000_1001).writeByte(0x55).writeInt(0x12345678); // flag bit (4) clear
        trailer(buf);

        AddEntityPacket.decode(buf);

        assertEquals(0, buf.readableBytes());
    }

    @Test
    void skipsNonZeroVelocityWithContinuationVarInt() {
        ByteBuf buf = header(1, 2, 0, 0, 0);
        buf.writeByte(0b0000_0111).writeByte(0x55).writeInt(0x12345678); // flag bit (4) set
        ProtocolCodec.writeVarInt(buf, 300); // two-byte VarInt scale extension
        trailer(buf);

        AddEntityPacket.decode(buf);

        assertEquals(0, buf.readableBytes());
    }

    @Test
    void rejectsTrailingBytes() {
        ByteBuf buf = header(1, 2, 0, 0, 0);
        buf.writeByte(0);
        trailer(buf);
        buf.writeByte(0xFF);

        assertThrows(IllegalStateException.class, () -> AddEntityPacket.decode(buf));
    }

    private static ByteBuf header(int entityId, int typeId, double x, double y, double z) {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeVarInt(buf, entityId);
        ProtocolCodec.writeUuid(buf, UUID.randomUUID());
        ProtocolCodec.writeVarInt(buf, typeId);
        buf.writeDouble(x).writeDouble(y).writeDouble(z);
        return buf;
    }

    private static void trailer(ByteBuf buf) {
        buf.writeByte(10).writeByte(20).writeByte(30); // xRot, yRot, yHeadRot
        ProtocolCodec.writeVarInt(buf, 0);               // data
    }
}
