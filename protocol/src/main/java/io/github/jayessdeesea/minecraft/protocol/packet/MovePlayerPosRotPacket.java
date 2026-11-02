package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Move Player Pos Rot" (protocol 777), 0x1F - the client's position and
 * look direction ({@code ServerboundMovePlayerPacket.PosRot}): double x/y/z, float yRot, float
 * xRot, byte flags (bit 0 on ground, bit 1 horizontal collision). Other players see the body and
 * head turn to the reported rotation. {@code horizontalCollision} is always sent as {@code false}:
 * nothing here detects it, and the server tolerates it being wrong (unlike position itself).
 *
 * <p>yRot is yaw in degrees, 0 facing south (+z) and 90 facing west (-x); xRot is pitch, negative
 * looking up.
 */
public record MovePlayerPosRotPacket(double x, double y, double z, float yRot, float xRot, boolean onGround) implements Packet {

    public static final int PACKET_ID = 0x1F;

    private static final int FLAG_ON_GROUND = 1;

    /** The server disconnects a client that reports a non-finite position or rotation, so it never goes out. */
    public MovePlayerPosRotPacket {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yRot) || !Float.isFinite(xRot)) {
            throw new IllegalArgumentException("non-finite move (" + x + ", " + y + ", " + z + ", " + yRot + ", " + xRot + ")");
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
        out.writeFloat(yRot);
        out.writeFloat(xRot);
        out.writeByte(onGround ? FLAG_ON_GROUND : 0);
    }
}
