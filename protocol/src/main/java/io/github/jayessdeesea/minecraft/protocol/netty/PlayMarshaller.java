package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.AddEntityPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.BlockUpdatePackets;
import io.github.jayessdeesea.minecraft.protocol.packet.ChunkBatchFinishedPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ChunkDataPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ContainerPackets;
import io.github.jayessdeesea.minecraft.protocol.packet.DisguisedChatPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.EntityPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ForgetLevelChunkPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.InventoryPackets;
import io.github.jayessdeesea.minecraft.protocol.packet.KeepAliveRequestPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.MoveEntityPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerChatPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerCombatKillPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerPositionPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.RemoveEntitiesPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.SetHealthPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.SetTimePacket;
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
            case SetTimePacket.PACKET_ID -> SetTimePacket.decode(data);
            case PlayerChatPacket.PACKET_ID -> PlayerChatPacket.decode(data);
            case DisguisedChatPacket.PACKET_ID -> DisguisedChatPacket.decode(data);
            case PlayerCombatKillPacket.PACKET_ID ->
                    PlayerCombatKillPacket.decode(data);
            case ChunkBatchFinishedPacket.PACKET_ID -> ChunkBatchFinishedPacket.decode(data);
            case ForgetLevelChunkPacket.PACKET_ID -> ForgetLevelChunkPacket.decode(data);
            case MoveEntityPacket.POS_PACKET_ID -> MoveEntityPacket.decodePos(data);
            case MoveEntityPacket.POS_ROT_PACKET_ID -> MoveEntityPacket.decodePosRot(data);
            case EntityPositionPacket.SYNC_PACKET_ID -> EntityPositionPacket.decodeSync(data);
            case EntityPositionPacket.TELEPORT_PACKET_ID -> EntityPositionPacket.decodeTeleport(data);
            case InventoryPackets.SET_CONTENT_PACKET_ID -> InventoryPackets.decodeSetContent(data);
            case InventoryPackets.SET_SLOT_PACKET_ID -> InventoryPackets.decodeSetSlot(data);
            case InventoryPackets.SET_HELD_SLOT_PACKET_ID -> InventoryPackets.decodeSetHeldSlot(data);
            case ContainerPackets.OpenScreen.PACKET_ID -> ContainerPackets.OpenScreen.decode(data);
            case BlockUpdatePackets.BLOCK_UPDATE_PACKET_ID -> BlockUpdatePackets.decodeBlockUpdate(data);
            case BlockUpdatePackets.SECTION_UPDATE_PACKET_ID -> BlockUpdatePackets.decodeSectionUpdate(data);
            case ContainerPackets.ContainerClosed.PACKET_ID -> ContainerPackets.ContainerClosed.decode(data);
            default -> new UnknownPacket(packetId, ByteBufUtil.getBytes(data));
        };
    }
}
