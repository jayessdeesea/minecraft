package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Move Entity Pos" (0x35) and "Move Entity Pos Rot" (0x36), protocol 776 -
 * IDs from {@code GameProtocols}' clientbound registration order, layouts from
 * {@code ClientboundMoveEntityPacket$Pos/$PosRot.read}: VarInt entity ID, three shorts of position
 * change, (0x36 only: byte yRot, byte xRot), boolean onGround.
 *
 * <p>The shorts are fixed-point in units of 1/{@value #UNITS_PER_BLOCK} block, relative to the last
 * position the server sent for that entity - by any of these packets, {@link EntityPositionPacket},
 * or {@link AddEntityPacket} ({@code ServerEntity.sendChanges} resets its {@code VecDeltaCodec}
 * base after every send). Resolving them needs that per-connection history; see
 * {@code PlayClientHandler}.
 */
public record MoveEntityPacket(int packetId, int entityId, short deltaX, short deltaY, short deltaZ) implements Packet {

    public static final int POS_PACKET_ID = 0x35;
    public static final int POS_ROT_PACKET_ID = 0x36;

    public static final double UNITS_PER_BLOCK = 4096.0;

    public static MoveEntityPacket decodePos(ByteBuf in) {
        return decode(POS_PACKET_ID, in, false);
    }

    public static MoveEntityPacket decodePosRot(ByteBuf in) {
        return decode(POS_ROT_PACKET_ID, in, true);
    }

    private static MoveEntityPacket decode(int packetId, ByteBuf in, boolean hasRotation) {
        int entityId = ProtocolCodec.readVarInt(in);
        short dx = in.readShort();
        short dy = in.readShort();
        short dz = in.readShort();
        if (hasRotation) {
            in.skipBytes(2);
        }
        in.readBoolean(); // onGround
        if (in.isReadable()) {
            throw new IllegalStateException("Move Entity 0x" + Integer.toHexString(packetId)
                    + " has " + in.readableBytes() + " unread trailing byte(s)");
        }
        return new MoveEntityPacket(packetId, entityId, dx, dy, dz);
    }

    /** Applies this packet's change to {@code base}, matching vanilla's {@code VecDeltaCodec.decode}. */
    public static double apply(double base, short delta) {
        return (Math.round(base * UNITS_PER_BLOCK) + delta) / UNITS_PER_BLOCK;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("MoveEntityPacket is clientbound-only and never sent by this client");
    }
}
