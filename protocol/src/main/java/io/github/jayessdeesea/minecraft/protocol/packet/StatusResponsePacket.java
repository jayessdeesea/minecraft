package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Status-state packet (docs/protocol-spec.md § 3.2), packet ID 0x00. Payload is a
 * single JSON string - we don't parse it here (no JSON library dependency in this module); the
 * caller decides what it needs from the response.
 */
public record StatusResponsePacket(String json) {

    public static final int PACKET_ID = 0x00;

    public static StatusResponsePacket decode(ByteBuf in) {
        return new StatusResponsePacket(ProtocolCodec.readString(in));
    }
}
