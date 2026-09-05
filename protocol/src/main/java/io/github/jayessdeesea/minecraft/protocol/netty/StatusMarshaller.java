package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PingPongPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.StatusResponsePacket;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/** Status-state packet catalog (docs/protocol-spec.md § 3.2) - only ever two clientbound types. */
public final class StatusMarshaller implements PacketMarshaller {

    @Override
    public Packet decode(int packetId, ByteBuf data) {
        return switch (packetId) {
            case StatusResponsePacket.PACKET_ID -> StatusResponsePacket.decode(data);
            case PingPongPacket.PACKET_ID -> PingPongPacket.decode(data);
            default -> new UnknownPacket(packetId, ByteBufUtil.getBytes(data));
        };
    }
}
