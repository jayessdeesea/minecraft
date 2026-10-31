package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Player Loaded" (protocol 776), 0x2C - empty body. A vanilla client sends
 * it once it has loaded in; until then the server ignores some actions entirely, attacks included
 * ({@code ServerGamePacketListenerImpl.handleAttack} checks {@code hasClientLoaded()} first).
 */
public record PlayerLoadedPacket() implements Packet {

    public static final int PACKET_ID = 0x2C;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
