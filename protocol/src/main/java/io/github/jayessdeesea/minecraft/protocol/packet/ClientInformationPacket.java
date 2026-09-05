package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Configuration-state packet, ID 0x00 - <strong>unconfirmed</strong>, inferred from
 * the same numbering source that correctly predicted {@link KnownPacksPacket}'s serverbound ID
 * 0x07 (see docs/protocol-spec.md § 3.4). Working theory: the server won't send Finish
 * Configuration until it has this, since real clients send it early in Configuration and our
 * client didn't.
 */
public record ClientInformationPacket(
        String locale,
        byte viewDistance,
        int chatMode,
        boolean chatColors,
        int displayedSkinParts,
        int mainHand,
        boolean enableTextFiltering,
        boolean allowServerListings,
        int particleStatus) implements Packet {

    public static final int PACKET_ID = 0x00;

    public static ClientInformationPacket defaults() {
        return new ClientInformationPacket("en_us", (byte) 10, 0, true, 0x7F, 1, false, true, 0);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeString(out, locale);
        out.writeByte(viewDistance);
        ProtocolCodec.writeVarInt(out, chatMode);
        out.writeBoolean(chatColors);
        out.writeByte(displayedSkinParts);
        ProtocolCodec.writeVarInt(out, mainHand);
        out.writeBoolean(enableTextFiltering);
        out.writeBoolean(allowServerListings);
        ProtocolCodec.writeVarInt(out, particleStatus);
    }
}
