package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.Arrays;

/**
 * Decodes one "paletted container" - the shared bit-packing scheme {@link ChunkDataPacket} uses
 * for both a section's block states and its biomes, differing only in entry count and the
 * indirect/direct bits-per-entry threshold. Confirmed against the real (unobfuscated) game server
 * jar's {@code net.minecraft.world.level.chunk} package - {@code Strategy.createForBlockStates}
 * uses 1-8 bits for Indirect (Linear for 1-4, HashMap for 5-8 - identical wire format either way)
 * and 9+ for Direct/Global; {@code Strategy.createForBiomes} uses 1-3 Indirect, 4+ Direct. Passed
 * in by the caller rather than hardcoded here since blocks and biomes differ.
 *
 * <p><strong>The data array has no length prefix on the wire</strong> - {@code
 * PalettedContainer.Data.write} calls {@code buffer.writeFixedSizeLongArray(...)}, not the
 * length-prefixed {@code writeLongArray}. The long count is entirely implicit:
 * {@code ceil(entryCount / (64 / bitsPerEntry))}, per {@code SimpleBitStorage}'s constructor. An
 * earlier version of this class read a VarInt "data array length" here that doesn't exist on the
 * wire at all - it does for {@link ChunkDataPacket}'s Heightmaps field (a genuinely
 * length-prefixed {@code ByteBufCodecs.LONG_ARRAY}), which is a different type with a different
 * encoding; conflating the two broke live decoding before this was checked against the actual server code.
 *
 * <p>Returns raw global IDs (block state IDs, or biome registry IDs), not their meaning - resolving
 * a block state ID to "is this solid" is {@code bot.world.BlockClassRegistry}'s job.
 */
final class PalettedContainer {

    private PalettedContainer() {
    }

    static int[] decode(ByteBuf in, int entryCount, int indirectMaxBits) {
        int bitsPerEntry = in.readUnsignedByte();
        int[] entries = new int[entryCount];

        if (bitsPerEntry == 0) {
            // SingleValuePalette: one VarInt value. Storage is a ZeroBitStorage - zero longs,
            // nothing further to read (no length field of any kind).
            int value = ProtocolCodec.readVarInt(in);
            Arrays.fill(entries, value);
            return entries;
        }

        int[] palette = null;
        if (bitsPerEntry <= indirectMaxBits) {
            // LinearPalette/HashMapPalette: both write identically - VarInt count + that many
            // VarInt global IDs (see LinearPalette.write/HashMapPalette.write).
            int paletteLength = ProtocolCodec.readVarIntCount(in, 1 << bitsPerEntry);
            palette = new int[paletteLength];
            for (int i = 0; i < paletteLength; i++) {
                palette[i] = ProtocolCodec.readVarInt(in);
            }
        }
        // Direct/GlobalPalette (bitsPerEntry > indirectMaxBits): GlobalPalette.write() writes
        // nothing at all - the data array holds global IDs directly, which is exactly what
        // `palette == null` falls through to below.

        int entriesPerLong = 64 / bitsPerEntry;
        int longCount = (entryCount + entriesPerLong - 1) / entriesPerLong;
        long[] data = new long[longCount];
        for (int i = 0; i < longCount; i++) {
            data[i] = in.readLong();
        }

        long mask = (1L << bitsPerEntry) - 1;
        for (int i = 0; i < entryCount; i++) {
            long word = data[i / entriesPerLong];
            int shift = (i % entriesPerLong) * bitsPerEntry;
            int index = (int) ((word >>> shift) & mask);
            entries[i] = palette != null ? palette[index] : index;
        }
        return entries;
    }
}
