package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state packet, officially "Move Player Position" (protocol 777) - reports the
 * client's current position with no rotation, the position-only variant of a family of four
 * ({@code move_player_pos}/{@code _pos_rot}/{@code _rot}/{@code _status_only}). This codebase
 * doesn't track look direction, so this is the one that matches what it actually has - sending a
 * fabricated yaw/pitch would be worse than simply not claiming to have one.
 *
 * <p>Confirmed by decompiling the real server jar: {@code ServerboundMovePlayerPacket.Pos} is
 * registered at index 30 (0x1E) in {@code GameProtocols}' serverbound list - same cross-checked
 * list as {@link AcceptTeleportationPacket}. {@code horizontalCollision} is always sent as
 * {@code false}: nothing here detects it, and it's an optional detail the server tolerates being
 * wrong about (unlike position itself).
 */
public record MovePlayerPosPacket(double x, double y, double z, boolean onGround) implements Packet {

    public static final int PACKET_ID = 0x1E;

    private static final int FLAG_ON_GROUND = 1;

    /** The server disconnects a client that reports a non-finite position, so it never goes out. */
    public MovePlayerPosPacket {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("non-finite position (" + x + ", " + y + ", " + z + ")");
        }
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeByte(onGround ? FLAG_ON_GROUND : 0);
    }
}
