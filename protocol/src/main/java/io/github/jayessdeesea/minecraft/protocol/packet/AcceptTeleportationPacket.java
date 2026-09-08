package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state reply to {@link PlayerPositionPacket} - echoes its {@code teleportId}
 * back unchanged, confirming the client accepted the new position. Confirmed by decompiling the
 * real server jar: {@code ServerboundAcceptTeleportationPacket} is registered first (index 0) in
 * {@code GameProtocols}' serverbound list, so its packet ID is 0x00 - cross-checked against three
 * already-empirically-confirmed packets in the same list landing exactly where expected (Interact
 * at 0x1A, Keep Alive at 0x1C, Player Loaded at 0x2C).
 *
 * <p>Serverbound-only, like {@link KeepAliveResponsePacket} - see its javadoc for why
 * {@link #encode} is a real inverse of nothing and {@link #decode} doesn't exist here.
 */
public record AcceptTeleportationPacket(int teleportId) implements Packet {

    public static final int PACKET_ID = 0x00;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, teleportId);
    }
}
