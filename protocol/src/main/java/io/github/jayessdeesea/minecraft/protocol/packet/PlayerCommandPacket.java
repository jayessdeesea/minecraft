package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound "Player Command", 0x2A ({@code ServerboundPlayerCommandPacket}): VarInt entity ID (the
 * player's own), VarInt action ({@code Action} ordinal), VarInt data (0 for sprinting). The server
 * sets the player's sprinting from it, and charges sprint exhaustion only while it's set.
 */
public record PlayerCommandPacket(int entityId, int action) implements Packet {

    public static final int PACKET_ID = 0x2A;
    public static final int START_SPRINTING = 1;
    public static final int STOP_SPRINTING = 2;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, entityId);
        ProtocolCodec.writeVarInt(out, action);
        ProtocolCodec.writeVarInt(out, 0);
    }
}
