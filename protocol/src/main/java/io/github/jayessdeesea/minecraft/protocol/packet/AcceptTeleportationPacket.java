package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state reply to {@link PlayerPositionPacket} - echoes its {@code teleportId}
 * back unchanged, confirming the client accepted the new position, followed by the position and
 * rotation the client now has (protocol 777: VarInt ID, three doubles, two floats). The server
 * snaps to its own target and rejects non-finite values. Confirmed by decompiling the
 * real server jar: {@code ServerboundAcceptTeleportationPacket} is registered first (index 0) in
 * {@code GameProtocols}' serverbound list, so its packet ID is 0x00 - cross-checked against three
 * already-empirically-confirmed packets in the same list landing exactly where expected (Interact
 * at 0x1A, Keep Alive at 0x1C, Player Loaded at 0x2C).
 *
 * <p>Serverbound-only, like {@link KeepAliveResponsePacket} - see its javadoc for why
 * {@link #encode} is a real inverse of nothing and {@link #decode} doesn't exist here.
 */
public record AcceptTeleportationPacket(int teleportId, double x, double y, double z, float yRot, float xRot) implements Packet {

    public static final int PACKET_ID = 0x00;

    /** The server disconnects a client whose reported position or rotation isn't finite. */
    public AcceptTeleportationPacket {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yRot) || !Float.isFinite(xRot)) {
            throw new IllegalArgumentException("non-finite teleport echo (" + x + ", " + y + ", " + z + ", " + yRot + ", " + xRot + ")");
        }
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, teleportId);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeFloat(yRot);
        out.writeFloat(xRot);
    }
}
