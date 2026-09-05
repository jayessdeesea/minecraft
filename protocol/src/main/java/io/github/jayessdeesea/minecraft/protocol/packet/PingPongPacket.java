package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Status-state ping/pong, packet ID 0x01 in both directions (docs/protocol-spec.md § 3.2): an
 * opaque Long the server echoes back unchanged. Same wire shape serverbound (Ping Request) and
 * clientbound (Pong Response), so one type covers both rather than two identical classes.
 */
public record PingPongPacket(long payload) {

    public static final int PACKET_ID = 0x01;

    public void encode(ByteBuf out) {
        out.writeLong(payload);
    }

    public static PingPongPacket decode(ByteBuf in) {
        return new PingPongPacket(in.readLong());
    }
}
