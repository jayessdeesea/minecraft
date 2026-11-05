package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound "Use Item", 0x43 ({@code ServerboundUseItemPacket}): VarInt hand, VarInt sequence,
 * float yaw, float pitch - right-clicking with what's held, at nothing in particular. For food the
 * server starts eating and finishes on its own once the item's eating time has passed, as long as
 * the player keeps holding it. A shield in the off hand is raised and stays raised until a Player
 * Action releases it.
 */
public record UseItemPacket(int hand, int sequence, float yaw, float pitch) implements Packet {

    public static final int PACKET_ID = 0x43;
    /** {@code InteractionHand}'s ordinals. */
    public static final int MAIN_HAND = 0;
    public static final int OFF_HAND = 1;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, hand);
        ProtocolCodec.writeVarInt(out, sequence);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
    }
}
