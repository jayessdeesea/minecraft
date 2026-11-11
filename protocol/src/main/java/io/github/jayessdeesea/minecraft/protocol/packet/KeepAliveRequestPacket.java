package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state packet, ID 0x2D (docs/protocol-spec.md § 3.5) - an opaque Long the client
 * must echo back via {@link KeepAliveResponsePacket} to avoid being kicked for timing out.
 * Confirmed empirically against the live dev server by content and timing, not any external spec:
 * an 8-byte payload appearing in isolation ~15 seconds after Play began (well after the initial
 * burst of registry/world-init packets at Play's start), matching vanilla's known Keep Alive
 * cadence. The payload's numeric meaning doesn't matter to us - like {@link
 * PingPongPacket}'s Status-state cousin, we only
 * ever need to copy it, never interpret it.
 */
public record KeepAliveRequestPacket(long id) implements Packet {

    public static final int PACKET_ID = 0x2D;

    public static KeepAliveRequestPacket decode(ByteBuf in) {
        return new KeepAliveRequestPacket(in.readLong());
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeLong(id);
    }
}
