package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.AddEntityPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ChunkDataPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.EntityPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KeepAliveRequestPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.MoveEntityPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.RemoveEntitiesPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.SetHealthPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * Play-state packet catalog (docs/protocol-spec.md § 3.5) - the confirmed packets are exactly the
 * cases in {@link #decode}. Play's catalog is huge; everything else still falls through to
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
            case AddEntityPacket.PACKET_ID -> AddEntityPacket.decode(data);
            case RemoveEntitiesPacket.PACKET_ID -> RemoveEntitiesPacket.decode(data);
            case SetHealthPacket.PACKET_ID -> SetHealthPacket.decode(data);
            case MoveEntityPacket.POS_PACKET_ID -> MoveEntityPacket.decodePos(data);
            case MoveEntityPacket.POS_ROT_PACKET_ID -> MoveEntityPacket.decodePosRot(data);
            case EntityPositionPacket.SYNC_PACKET_ID -> EntityPositionPacket.decodeSync(data);
            case EntityPositionPacket.TELEPORT_PACKET_ID -> EntityPositionPacket.decodeTeleport(data);
            default -> new UnknownPacket(packetId, ByteBufUtil.getBytes(data));
        };
    }
}
