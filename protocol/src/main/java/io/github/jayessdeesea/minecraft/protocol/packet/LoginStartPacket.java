package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * Serverbound Login-state packet (docs/protocol-spec.md § 3.3 step 1), packet ID 0x00. In
 * offline mode the vanilla server derives its own UUID from the username and ignores whatever we
 * send here, so any value works; we still have to send a syntactically valid one.
 */
public record LoginStartPacket(String name, UUID uuid) {

    public static final int PACKET_ID = 0x00;

    public void encode(ByteBuf out) {
        ProtocolCodec.writeString(out, name);
        ProtocolCodec.writeUuid(out, uuid);
    }
}
