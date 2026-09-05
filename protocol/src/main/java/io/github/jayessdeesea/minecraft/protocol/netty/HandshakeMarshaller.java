package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.netty.buffer.ByteBuf;

/**
 * Nothing is ever clientbound during Handshaking (docs/protocol-spec.md § 3.1) - the client sends
 * one packet and immediately moves on. If this ever actually gets invoked, that's a protocol
 * anomaly worth failing loudly on rather than silently misinterpreting.
 */
public final class HandshakeMarshaller implements PacketMarshaller {

    @Override
    public Packet decode(int packetId, ByteBuf data) {
        throw new IllegalStateException(
                "Received packet ID 0x" + Integer.toHexString(packetId) + " during Handshake - nothing should arrive here");
    }
}
