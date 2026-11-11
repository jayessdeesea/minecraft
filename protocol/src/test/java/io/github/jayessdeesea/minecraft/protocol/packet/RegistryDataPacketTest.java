package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-built bytes matching the format confirmed by decompiling the real server jar (see this
 * class's own javadoc) - not a symmetric round-trip, since {@code encode} deliberately doesn't
 * exist for this clientbound-only packet.
 */
class RegistryDataPacketTest {

    @Test
    void decodesRegistryNameAndEntryIdsSkippingNbtPayloads() {
        ByteBuf buf = Unpooled.buffer();
        ProtocolCodec.writeString(buf, "minecraft:dimension_type");
        ProtocolCodec.writeVarInt(buf, 3); // entry count

        // Entry 1: has an NBT payload - a compound with one int field, to exercise real skipping.
        ProtocolCodec.writeString(buf, "minecraft:overworld");
        buf.writeBoolean(true);
        writeCompoundWithOneInt(buf, "height", 384);

        // Entry 2: no payload at all (relies on a previously-sent known pack).
        ProtocolCodec.writeString(buf, "minecraft:the_nether");
        buf.writeBoolean(false);

        // Entry 3: has a payload again, to confirm decoding resumed correctly after entry 2.
        ProtocolCodec.writeString(buf, "minecraft:the_end");
        buf.writeBoolean(true);
        writeCompoundWithOneInt(buf, "height", 256);

        RegistryDataPacket packet = RegistryDataPacket.decode(buf);

        assertEquals("minecraft:dimension_type", packet.registry());
        assertEquals(3, packet.entryIds().size());
        assertEquals("minecraft:overworld", packet.entryIds().get(0));
        assertEquals("minecraft:the_nether", packet.entryIds().get(1));
        assertEquals("minecraft:the_end", packet.entryIds().get(2));
        assertEquals(0, buf.readableBytes(), "decode should consume exactly what was written");
    }

    /** {@code NbtIo.writeAnyTag}'s shape: type byte, no name, payload - a real, minimal compound. */
    private static void writeCompoundWithOneInt(ByteBuf buf, String fieldName, int value) {
        buf.writeByte(10); // TAG_Compound
        // one named child: TAG_Int
        buf.writeByte(3);
        writeNbtString(buf, fieldName);
        buf.writeInt(value);
        buf.writeByte(0); // TAG_End
    }

    private static void writeNbtString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}
