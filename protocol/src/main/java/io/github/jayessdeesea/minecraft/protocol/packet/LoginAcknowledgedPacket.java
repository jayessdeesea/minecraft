package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/** Serverbound Login-state packet (docs/protocol-spec.md § 3.3 step 6), empty body. */
public record LoginAcknowledgedPacket() implements Packet {

    public static final int PACKET_ID = 0x03;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
