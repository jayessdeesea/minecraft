package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Builds packet bytes by hand (not via a symmetric {@code encode()}, which {@link ChunkDataPacket}
 * deliberately doesn't have - see its class javadoc) matching the real format confirmed against
 * the decompiled game server jar, so a bug shared between encode and decode couldn't hide a
 * mistake the way a self-round-trip test would.
 */
class ChunkDataPacketTest {

    @Test
    void decodesSingleValuedAndIndirectSections() {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(5);   // chunk X
        buf.writeInt(-3);  // chunk Z
        writeHeightmaps(buf);

        ByteBuf data = Unpooled.buffer();
        writeSingleValuedSection(data, 0, 0);   // all air
        writeSingleValuedSection(data, 1, 0);   // all stone
        writeIndirectSection(data, new int[]{0, 1, 2}, 4, 1); // palette-indirect, every block = index 1 (stone)

        ProtocolCodec.writeVarInt(buf, data.readableBytes());
        buf.writeBytes(data);

        ChunkDataPacket packet = ChunkDataPacket.decode(buf);

        assertEquals(5, packet.chunkX());
        assertEquals(-3, packet.chunkZ());
        List<int[]> sections = packet.sectionBlockStates();
        assertEquals(3, sections.size());

        assertAllEqual(sections.get(0), 0);
        assertAllEqual(sections.get(1), 1);
        assertAllEqual(sections.get(2), 1);
    }

    private static void assertAllEqual(int[] section, int expected) {
        for (int value : section) {
            assertEquals(expected, value);
        }
    }

    /** {@code ByteBufCodecs.map(...)}: VarInt count, then per entry [VarInt type, length-prefixed long array]. */
    private static void writeHeightmaps(ByteBuf buf) {
        ProtocolCodec.writeVarInt(buf, 2);
        for (int type = 0; type < 2; type++) {
            ProtocolCodec.writeVarInt(buf, type);
            ProtocolCodec.writeVarInt(buf, 3); // long count
            buf.writeLong(0).writeLong(0).writeLong(0);
        }
    }

    private static void writeSingleValuedSection(ByteBuf data, int blockStateId, int biomeId) {
        data.writeShort(0); // nonEmptyBlockCount
        data.writeShort(0); // fluidCount
        writeSingleValuedPalettedContainer(data, blockStateId);
        writeSingleValuedPalettedContainer(data, biomeId);
    }

    /** SingleValuePalette: bits-per-entry 0, then one VarInt value - no data array at all. */
    private static void writeSingleValuedPalettedContainer(ByteBuf data, int value) {
        data.writeByte(0);
        ProtocolCodec.writeVarInt(data, value);
    }

    /** Every one of the 4096 block-state entries is {@code paletteIndex} into {@code palette}. */
    private static void writeIndirectSection(ByteBuf data, int[] palette, int bitsPerEntry, int paletteIndex) {
        data.writeShort(0); // nonEmptyBlockCount
        data.writeShort(0); // fluidCount

        data.writeByte(bitsPerEntry);
        ProtocolCodec.writeVarInt(data, palette.length);
        for (int value : palette) {
            ProtocolCodec.writeVarInt(data, value);
        }
        int entriesPerLong = 64 / bitsPerEntry;
        int longCount = (4096 + entriesPerLong - 1) / entriesPerLong; // no length prefix - implicit on the wire
        long word = 0;
        for (int i = 0; i < entriesPerLong; i++) {
            word |= ((long) paletteIndex) << (i * bitsPerEntry);
        }
        for (int i = 0; i < longCount; i++) {
            data.writeLong(word);
        }

        writeSingleValuedPalettedContainer(data, 0); // biomes - single-valued, not exercised here
    }
}
