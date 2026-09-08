package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.ChunkDataPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KeepAliveRequestPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * Play-state packet catalog (docs/protocol-spec.md § 3.5) - four confirmed packets so far
 * ({@link PlayLoginPacket}, {@link KeepAliveRequestPacket}, {@link ChunkDataPacket},
 * {@link PlayerPositionPacket}). Play's catalog is huge; everything else still falls through to
 * {@link UnknownPacket}. Fill this in incrementally, packet by confirmed packet, the same way
 * Configuration's was built - not upfront.
 */
public final class PlayMarshaller implements PacketMarshaller {

    @Override
    public Packet decode(int packetId, ByteBuf data) {
        return switch (packetId) {
            case PlayLoginPacket.PACKET_ID -> PlayLoginPacket.decode(data);
            case KeepAliveRequestPacket.PACKET_ID -> KeepAliveRequestPacket.decode(data);
            case ChunkDataPacket.PACKET_ID -> ChunkDataPacket.decode(data);
            case PlayerPositionPacket.PACKET_ID -> PlayerPositionPacket.decode(data);
            default -> new UnknownPacket(packetId, ByteBufUtil.getBytes(data));
        };
    }
}
