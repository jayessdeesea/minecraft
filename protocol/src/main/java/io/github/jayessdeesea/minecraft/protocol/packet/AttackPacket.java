package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Attack" (protocol 776), 0x01 - ID from {@code GameProtocols}' serverbound
 * registration order; body is a single VarInt entity ID ({@code ServerboundAttackPacket.STREAM_CODEC}).
 * The server only honours it once the client has sent {@link PlayerLoadedPacket}, and only within
 * attack range ({@code ServerPlayer.isWithinAttackRange}) - an out-of-range attack is silently ignored.
 */
public record AttackPacket(int entityId) implements Packet {

    public static final int PACKET_ID = 0x01;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, entityId);
    }
}
