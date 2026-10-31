package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state packet, officially "Set Health" (protocol 776), 0x68 - ID from
 * {@code GameProtocols}' clientbound registration order (see {@link AddEntityPacket}'s javadoc for
 * the cross-check), layout from {@code ClientboundSetHealthPacket}'s wire constructor: float
 * health, VarInt food, float saturation. Sent on join and whenever any of them changes. A health of
 * zero or less means the player is dead - and a dead player is sent no chunks or entities until it
 * respawns (see {@link ClientCommandPacket}).
 */
public record SetHealthPacket(float health, int food, float saturation) implements Packet {

    public static final int PACKET_ID = 0x68;

    public static SetHealthPacket decode(ByteBuf in) {
        float health = in.readFloat();
        int food = ProtocolCodec.readVarInt(in);
        float saturation = in.readFloat();
        return new SetHealthPacket(health, food, saturation);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("SetHealthPacket is clientbound-only and never sent by this client");
    }
}
