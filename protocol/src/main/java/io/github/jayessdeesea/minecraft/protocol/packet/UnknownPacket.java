package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Placeholder for any packet ID a state's marshaller doesn't recognize yet. Inbound-only -
 * {@link #encode} throws, since we never construct one of these to send; it exists so an
 * incomplete packet catalog (most of Configuration, all of Play right now) degrades to "log and
 * ignore" instead of failing the connection. See docs/protocol-spec.md § 3.4 on driving these to
 * zero deliberately rather than guessing at formats.
 */
public record UnknownPacket(int packetId, byte[] data) implements Packet {

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("UnknownPacket 0x" + Integer.toHexString(packetId) + " cannot be sent");
    }
}
