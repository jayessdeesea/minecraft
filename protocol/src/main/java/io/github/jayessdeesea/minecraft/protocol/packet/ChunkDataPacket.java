package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound Play-state packet, officially "Chunk Data and Update Light" (protocol 777,
 * docs/world-data-spec.md § 1/§ 2). Decodes chunk X/Z and, per vertical section, its block-state
 * array (4096 global state IDs, one per block) - exactly what {@code bot.world}'s chunk cache
 * needs for collision queries.
 *
 * <p>Confirmed against the real (unobfuscated - see DEVELOPMENT.md's "Confirming wire formats") game server
 * jar's decompiled source, not just the wire spec, after a first attempt (built from wiki
 * descriptions alone) threw decoder exceptions on live data. Two things the wiki-derived version
 * got wrong, per {@code net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData} and
 * {@code net.minecraft.world.level.chunk.LevelChunkSection}:
 * <ul>
 *   <li>Heightmaps is <strong>not NBT</strong> - it's {@code ByteBufCodecs.map(...)}: a VarInt
 *   count, then per entry a VarInt heightmap type and a length-prefixed long array (VarInt count +
 *   that many longs). Nothing here reads the values, just skips past them the same way trailing
 *   fields elsewhere in this codebase are skipped.</li>
 *   <li>Each section writes <strong>two</strong> shorts before its block-states container -
 *   {@code nonEmptyBlockCount} and {@code fluidCount} ({@code LevelChunkSection.write}) - not one.
 *   Neither is needed for solidity queries.</li>
 * </ul>
 *
 * <p>Biome data is decoded (not merely skipped) because it's interleaved between sections' block
 * states rather than trailing at the end - {@link PalettedContainer} handles both with the same
 * logic. Block entities and light data are left entirely unread, same "frame is already
 * length-isolated, trailing fields can wait" precedent as {@link PlayLoginPacket}.
 *
 * <p>Clientbound-only: the client never constructs one to send, so unlike most packets here
 * {@link #encode} isn't a real inverse of {@link #decode} (see {@link UnknownPacket} for the same
 * pattern). Section count isn't hardcoded to a particular dimension's height - the Data field is
 * itself length-prefixed, so decoding just keeps reading sections until that many bytes are
 * consumed, whatever the actual count turns out to be.
 */
public record ChunkDataPacket(int chunkX, int chunkZ, List<int[]> sectionBlockStates) implements Packet {

    public static final int PACKET_ID = 0x2E;

    private static final int BLOCK_STATES_PER_SECTION = 4096;
    private static final int BLOCK_STATES_INDIRECT_MAX_BITS = 8;
    private static final int BIOMES_PER_SECTION = 64;
    private static final int BIOMES_INDIRECT_MAX_BITS = 3;

    /** Generous cap on section count - vanilla currently sends 24 (Overworld's -64..319 range). */
    private static final int MAX_SECTIONS = 64;

    /** Generous cap on heightmap entries - vanilla currently sends at most a handful of types. */
    private static final int MAX_HEIGHTMAPS = 16;

    public static ChunkDataPacket decode(ByteBuf in) {
        int chunkX = in.readInt();
        int chunkZ = in.readInt();
        skipHeightmaps(in);

        int dataLength = ProtocolCodec.readVarIntCount(in, ProtocolCodec.MAX_FRAME_LENGTH);
        ByteBuf data = in.readSlice(dataLength);

        List<int[]> sections = new ArrayList<>();
        while (data.isReadable()) {
            if (sections.size() >= MAX_SECTIONS) {
                throw new IllegalStateException("Chunk data has more than " + MAX_SECTIONS + " sections");
            }
            data.skipBytes(4); // nonEmptyBlockCount + fluidCount (both shorts) - not needed here
            int[] blockStates = PalettedContainer.decode(data, BLOCK_STATES_PER_SECTION, BLOCK_STATES_INDIRECT_MAX_BITS);
            PalettedContainer.decode(data, BIOMES_PER_SECTION, BIOMES_INDIRECT_MAX_BITS); // biomes - decoded to stay aligned, not retained
            sections.add(blockStates);
        }

        // Block entities and light data follow - unread, same as PlayLoginPacket's trailing
        // fields (the frame is already length-isolated by FrameDecoder).
        return new ChunkDataPacket(chunkX, chunkZ, List.copyOf(sections));
    }

    /**
     * {@code ByteBufCodecs.map(constructor, Heightmap.Types.STREAM_CODEC, ByteBufCodecs.LONG_ARRAY)}
     * - a VarInt entry count, then per entry a VarInt type (opaque here) and a length-prefixed long
     * array ({@code FriendlyByteBuf.writeLongArray}: VarInt count + that many raw longs).
     */
    private static void skipHeightmaps(ByteBuf in) {
        int count = ProtocolCodec.readVarIntCount(in, MAX_HEIGHTMAPS);
        for (int i = 0; i < count; i++) {
            ProtocolCodec.readVarInt(in); // heightmap type
            int longCount = ProtocolCodec.readVarIntCount(in, ProtocolCodec.MAX_FRAME_LENGTH / 8);
            in.skipBytes(longCount * 8);
        }
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("ChunkDataPacket is clientbound-only and never sent by this client");
    }
}
