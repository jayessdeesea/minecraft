package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * The single Handshaking-state packet (docs/protocol-spec.md § 3.1). Serverbound only, packet
 * ID 0x00.
 */
public record HandshakePacket(int protocolVersion, String serverAddress, int serverPort, int nextState) implements Packet {

    public static final int PACKET_ID = 0x00;

    public static final int NEXT_STATE_STATUS = 1;
    public static final int NEXT_STATE_LOGIN = 2;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, protocolVersion);
        ProtocolCodec.writeString(out, serverAddress);
        out.writeShort(serverPort);
        ProtocolCodec.writeVarInt(out, nextState);
    }
}
