package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.LoginDisconnectPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.LoginSuccessPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.SetCompressionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/** Login-state packet catalog (docs/protocol-spec.md § 3.3). */
public final class LoginMarshaller implements PacketMarshaller {

    @Override
    public Packet decode(int packetId, ByteBuf data) {
        return switch (packetId) {
            case LoginDisconnectPacket.PACKET_ID -> LoginDisconnectPacket.decode(data);
            case SetCompressionPacket.PACKET_ID -> SetCompressionPacket.decode(data);
            case LoginSuccessPacket.PACKET_ID -> LoginSuccessPacket.decode(data);
            default -> new UnknownPacket(packetId, ByteBufUtil.getBytes(data));
        };
    }
}
