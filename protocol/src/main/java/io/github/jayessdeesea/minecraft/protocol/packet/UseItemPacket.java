package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound "Use Item", 0x43 ({@code ServerboundUseItemPacket}): VarInt hand, VarInt sequence,
 * float yaw, float pitch - right-clicking with what's held, at nothing in particular. For food the
 * server starts eating and finishes on its own once the item's eating time has passed, as long as
 * the player keeps holding it. Always the main hand.
 */
public record UseItemPacket(int sequence, float yaw, float pitch) implements Packet {

    public static final int PACKET_ID = 0x43;
    private static final int MAIN_HAND = 0;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, MAIN_HAND);
        ProtocolCodec.writeVarInt(out, sequence);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
    }
}
