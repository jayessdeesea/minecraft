package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.FinishConfigurationPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.KnownPacksPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

/**
 * Configuration-state packet catalog (docs/protocol-spec.md § 3.4) - only the two packets the
 * join sequence needs to recognize (Select Known Packs, Finish Configuration) are typed; Plugin
 * Message, Feature Flags, Registry Data, and Update Tags are still deliberately unrecognized -
 * see § 3.4 on not guessing at formats we haven't verified against real traffic.
 */
public final class ConfigurationMarshaller implements PacketMarshaller {

    @Override
    public Packet decode(int packetId, ByteBuf data) {
        return switch (packetId) {
            case KnownPacksPacket.CLIENTBOUND_PACKET_ID -> KnownPacksPacket.decode(data);
            case FinishConfigurationPacket.PACKET_ID -> FinishConfigurationPacket.decode(data);
            default -> new UnknownPacket(packetId, ByteBufUtil.getBytes(data));
        };
    }
}
