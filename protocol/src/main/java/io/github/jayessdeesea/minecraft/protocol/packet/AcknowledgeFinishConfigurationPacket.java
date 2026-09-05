package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/** Serverbound reply to {@link FinishConfigurationPacket} (docs/protocol-spec.md § 3.4), empty body. */
public record AcknowledgeFinishConfigurationPacket() implements Packet {

    public static final int PACKET_ID = 0x03;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
