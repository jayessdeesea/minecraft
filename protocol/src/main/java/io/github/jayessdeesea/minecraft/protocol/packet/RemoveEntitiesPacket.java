package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound Play-state packet, officially "Remove Entities" (protocol 777), 0x4E - ID confirmed
 * the same way as {@link AddEntityPacket}'s; layout is {@code FriendlyByteBuf.readIntIdList}: a
 * VarInt count followed by that many VarInt entity IDs.
 */
public record RemoveEntitiesPacket(List<Integer> entityIds) implements Packet {

    public static final int PACKET_ID = 0x4E;

    public static RemoveEntitiesPacket decode(ByteBuf in) {
        // Every VarInt is at least one byte, so the remaining payload length is a hard upper bound.
        int count = ProtocolCodec.readVarIntCount(in, in.readableBytes());
        List<Integer> entityIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entityIds.add(ProtocolCodec.readVarInt(in));
        }
        return new RemoveEntitiesPacket(List.copyOf(entityIds));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("RemoveEntitiesPacket is clientbound-only and never sent by this client");
    }
}
