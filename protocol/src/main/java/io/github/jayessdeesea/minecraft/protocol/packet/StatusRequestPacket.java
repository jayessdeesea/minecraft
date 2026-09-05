package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/** Serverbound Status-state packet (docs/protocol-spec.md § 3.2), packet ID 0x00, empty body. */
public record StatusRequestPacket() implements Packet {

    public static final int PACKET_ID = 0x00;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
