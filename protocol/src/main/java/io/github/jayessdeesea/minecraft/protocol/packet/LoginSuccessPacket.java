package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * Clientbound Login-state packet (docs/protocol-spec.md § 3.3 step 5), packet ID 0x02. The real
 * payload also carries a trailing array of profile properties (skin/cape textures) that we don't
 * decode - we only need identity, and the frame is already isolated by the length prefix, so
 * leaving trailing bytes unread is harmless.
 */
public record LoginSuccessPacket(UUID uuid, String username) {

    public static final int PACKET_ID = 0x02;

    public static LoginSuccessPacket decode(ByteBuf in) {
        UUID uuid = ProtocolCodec.readUuid(in);
        String username = ProtocolCodec.readString(in);
        return new LoginSuccessPacket(uuid, username);
    }
}
