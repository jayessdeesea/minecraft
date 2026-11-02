package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.BlockChange;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The clientbound Play packets that change blocks in loaded chunks (protocol 777), IDs from
 * {@code GameProtocols}' registration order:
 * <ul>
 *   <li>Block Update, 0x08 ({@code ClientboundBlockUpdatePacket}): packed block position (long),
 *       VarInt global block-state ID;</li>
 *   <li>Section Blocks Update, 0x56 ({@code ClientboundSectionBlocksUpdatePacket}): packed section
 *       position (long: x 22 bits, z 22 bits, y 20 bits), VarInt count, then per block a VarLong of
 *       state ID shifted left 12 bits, or-ed with the position in the section (x, z, y - 4 bits
 *       each).</li>
 * </ul>
 * Either decodes to the list of {@link BlockChange}s it carries.
 */
public record BlockUpdatePackets(int packetId, List<BlockChange> changes) implements Packet {

    public static final int BLOCK_UPDATE_PACKET_ID = 0x08;
    public static final int SECTION_UPDATE_PACKET_ID = 0x56;

    /** A section holds 4096 blocks; a section update can't name more. */
    static final int MAX_SECTION_CHANGES = 4096;

    public static BlockUpdatePackets decodeBlockUpdate(ByteBuf in) {
        long pos = in.readLong();
        int x = (int) (pos >> 38);
        int y = (int) (pos << 52 >> 52);
        int z = (int) (pos << 26 >> 38);
        return new BlockUpdatePackets(BLOCK_UPDATE_PACKET_ID, List.of(new BlockChange(x, y, z, ProtocolCodec.readVarInt(in))));
    }

    public static BlockUpdatePackets decodeSectionUpdate(ByteBuf in) {
        long section = in.readLong();
        int sectionX = (int) (section >> 42);
        int sectionY = (int) (section << 44 >> 44);
        int sectionZ = (int) (section << 22 >> 42);
        int count = ProtocolCodec.readVarIntCount(in, MAX_SECTION_CHANGES);
        List<BlockChange> changes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long entry = ProtocolCodec.readVarLong(in);
            int local = (int) (entry & 0xFFF);
            changes.add(new BlockChange(sectionX * 16 + (local >>> 8 & 15), sectionY * 16 + (local & 15),
                    sectionZ * 16 + (local >>> 4 & 15), (int) (entry >>> 12)));
        }
        return new BlockUpdatePackets(SECTION_UPDATE_PACKET_ID, List.copyOf(changes));
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("block updates are clientbound-only and never sent by this client");
    }
}
