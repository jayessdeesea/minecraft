package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Configuration-state packet (docs/protocol-spec.md § 3.4), empty body - confirmed
 * empirically against the live dev server: nothing followed it until we acknowledged it.
 */
public record FinishConfigurationPacket() implements Packet {

    public static final int PACKET_ID = 0x03;

    public static FinishConfigurationPacket decode(ByteBuf in) {
        return new FinishConfigurationPacket();
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
