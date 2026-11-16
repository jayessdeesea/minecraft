package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Disconnect", 0x20 ({@code ClientboundDisconnectPacket}): the server's
 * reason for closing the connection, a text component (NBT) - a kick for spamming reads
 * {@code disconnect.spam}. Read as plain text.
 */
public record PlayDisconnectPacket(String reason) implements Packet {

    public static final int PACKET_ID = 0x20;

    public static PlayDisconnectPacket decode(ByteBuf in) {
        return new PlayDisconnectPacket(Nbt.readText(in));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("PlayDisconnectPacket is clientbound-only and never sent by this client");
    }
}
