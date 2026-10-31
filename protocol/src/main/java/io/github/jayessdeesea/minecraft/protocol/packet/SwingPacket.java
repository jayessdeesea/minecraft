package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Swing" (protocol 776), 0x3F - the arm-swing animation a vanilla client
 * sends alongside every attack. Body is the hand as a VarInt enum ordinal; only the main hand
 * (ordinal 0) is used.
 */
public record SwingPacket() implements Packet {

    public static final int PACKET_ID = 0x3F;

    private static final int MAIN_HAND = 0;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, MAIN_HAND);
    }
}
