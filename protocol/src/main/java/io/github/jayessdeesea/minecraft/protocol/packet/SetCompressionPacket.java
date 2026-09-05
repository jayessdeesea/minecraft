package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Login-state packet (docs/protocol-spec.md § 4.1), packet ID 0x03. Only sent when
 * the server has a non-negative compression threshold configured (our dev server does:
 * {@code network-compression-threshold=256} in server.properties).
 */
public record SetCompressionPacket(int threshold) {

    public static final int PACKET_ID = 0x03;

    public static SetCompressionPacket decode(ByteBuf in) {
        return new SetCompressionPacket(ProtocolCodec.readVarInt(in));
    }
}
