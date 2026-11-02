package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Entity Position Sync" (0x23) and "Teleport Entity" (0x80), protocol 777 -
 * IDs from {@code GameProtocols}' clientbound registration order. Both start with a VarInt entity
 * ID and end with boolean onGround. Teleport carries {@code PositionMoveRotation} (double x/y/z,
 * double velocity x/y/z, float yRot, float xRot) and then an int of {@code Relative} flags. Position
 * Sync carries a {@code PositionPath} - VarInt type, then for 0 (linear) double x/y/z, for 1
 * (stepped) a VarInt-counted list of double x/y/z and VarInt ticks, the entity ending at the last -
 * and then float yRot, float xRot.
 *
 * <p>Position Sync is always absolute. A Teleport flag bit 0/1/2 set means x/y/z is an offset from
 * the entity's current position rather than absolute - see {@link #isRelative}.
 */
public record EntityPositionPacket(int packetId, int entityId, double x, double y, double z,
                                   int relativeFlags) implements Packet {

    public static final int SYNC_PACKET_ID = 0x23;
    public static final int TELEPORT_PACKET_ID = 0x80;

    public static EntityPositionPacket decodeSync(ByteBuf in) {
        return decode(SYNC_PACKET_ID, in, false);
    }

    public static EntityPositionPacket decodeTeleport(ByteBuf in) {
        return decode(TELEPORT_PACKET_ID, in, true);
    }

    /** A stepped path longer than this is a violated invariant: entities move a few steps per tick. */
    static final int MAX_PATH_STEPS = 64;
    private static final int LINEAR = 0;
    private static final int STEPPED = 1;

    private static EntityPositionPacket decode(int packetId, ByteBuf in, boolean teleport) {
        int entityId = ProtocolCodec.readVarInt(in);
        double x;
        double y;
        double z;
        int relativeFlags = 0;
        if (teleport) {
            x = in.readDouble();
            y = in.readDouble();
            z = in.readDouble();
            in.skipBytes(3 * Double.BYTES + 2 * Float.BYTES); // velocity, yRot, xRot
            relativeFlags = in.readInt();
        } else {
            int type = ProtocolCodec.readVarInt(in);
            int points = switch (type) {
                case LINEAR -> 1;
                case STEPPED -> ProtocolCodec.readVarIntCount(in, MAX_PATH_STEPS);
                default -> throw new IllegalStateException("Entity Position Sync path type " + type);
            };
            if (points == 0) {
                throw new IllegalStateException("Entity Position Sync with an empty path");
            }
            x = y = z = 0;
            for (int i = 0; i < points; i++) {
                x = in.readDouble();
                y = in.readDouble();
                z = in.readDouble();
                if (type == STEPPED) {
                    ProtocolCodec.readVarInt(in); // ticks
                }
            }
            in.skipBytes(2 * Float.BYTES); // yRot, xRot
        }
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
