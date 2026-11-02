package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Punch" (protocol 777), 0x2E - the arm swing a vanilla client sends after
 * every attack. Empty body. The server swings the main hand and resets the attack-strength timer
 * ({@code ServerGamePacketListenerImpl.handlePunch}), so it goes after the {@link AttackPacket},
 * never before: an attack after a fresh reset lands at no strength.
 */
public record PunchPacket() implements Packet {

    public static final int PACKET_ID = 0x2E;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
