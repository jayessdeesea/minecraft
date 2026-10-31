package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state packet, officially "Client Command" (protocol 776), 0x0C - ID from
 * {@code GameProtocols}' serverbound registration order, cross-checked against Accept Teleportation
 * (0x00), Keep Alive (0x1C), and Move Player Pos (0x1E) landing where this codebase already
 * confirmed them. Body is the action enum written as a VarInt ordinal; only {@link #PERFORM_RESPAWN}
 * (ordinal 0) is used.
 */
public record ClientCommandPacket(int action) implements Packet {

    public static final int PACKET_ID = 0x0C;

    public static final int PERFORM_RESPAWN = 0;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, action);
    }
}
