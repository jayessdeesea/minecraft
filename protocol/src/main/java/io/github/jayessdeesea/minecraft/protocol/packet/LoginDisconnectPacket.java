package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Login-state packet, packet ID 0x00: the server rejected the login attempt (e.g.
 * bad name, server full). Payload is a JSON chat component, same as the Status Response body -
 * not parsed here, just surfaced to the caller.
 */
public record LoginDisconnectPacket(String reasonJson) implements Packet {

    public static final int PACKET_ID = 0x00;

    public static LoginDisconnectPacket decode(ByteBuf in) {
        return new LoginDisconnectPacket(ProtocolCodec.readString(in));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeString(out, reasonJson);
    }
}
