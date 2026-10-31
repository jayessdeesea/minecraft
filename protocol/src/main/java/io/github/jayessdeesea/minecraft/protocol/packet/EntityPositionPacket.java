package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Entity Position Sync" (0x23) and "Teleport Entity" (0x7D), protocol 776 -
 * IDs from {@code GameProtocols}' clientbound registration order. Both are a VarInt entity ID
 * followed by {@code PositionMoveRotation} (double x/y/z, double velocity x/y/z, float yRot, float
 * xRot); Teleport then adds an int of {@code Relative} flags; both end with boolean onGround.
 *
 * <p>Position Sync is always absolute. A Teleport flag bit 0/1/2 set means x/y/z is an offset from
 * the entity's current position rather than absolute - see {@link #isRelative}.
 */
public record EntityPositionPacket(int packetId, int entityId, double x, double y, double z,
                                   int relativeFlags) implements Packet {

    public static final int SYNC_PACKET_ID = 0x23;
    public static final int TELEPORT_PACKET_ID = 0x7D;

    public static EntityPositionPacket decodeSync(ByteBuf in) {
        return decode(SYNC_PACKET_ID, in, false);
    }

    public static EntityPositionPacket decodeTeleport(ByteBuf in) {
        return decode(TELEPORT_PACKET_ID, in, true);
    }

    private static EntityPositionPacket decode(int packetId, ByteBuf in, boolean hasRelativeFlags) {
        int entityId = ProtocolCodec.readVarInt(in);
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        in.skipBytes(3 * Double.BYTES + 2 * Float.BYTES); // velocity, yRot, xRot
        int relativeFlags = hasRelativeFlags ? in.readInt() : 0;
        in.readBoolean(); // onGround
        if (in.isReadable()) {
            throw new IllegalStateException("Entity position packet 0x" + Integer.toHexString(packetId)
                    + " has " + in.readableBytes() + " unread trailing byte(s)");
        }
        return new EntityPositionPacket(packetId, entityId, x, y, z, relativeFlags);
    }

    /** Whether {@code axis} (0 = x, 1 = y, 2 = z) is an offset from the current position. */
    public boolean isRelative(int axis) {
        return (relativeFlags & (1 << axis)) != 0;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("EntityPositionPacket is clientbound-only and never sent by this client");
    }
}
