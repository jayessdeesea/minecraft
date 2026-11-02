package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Move Entity Pos" (0x36) and "Move Entity Pos Rot" (0x37), protocol 777 -
 * IDs from {@code GameProtocols}' clientbound registration order, layouts from
 * {@code ClientboundMoveEntityPacket$Pos/$PosRot.read}: VarInt entity ID; VarInt properties (bit 0
 * on ground, the rest a step count); the position change ({@code VecDelta.read}) - with no steps,
 * three shorts; otherwise per step a VarInt tick count and three shorts, each step relative to the
 * one before; then (0x37 only) byte yRot, byte xRot. The steps are a path the client may animate
 * along; this client keeps only where it ends, the sum of the steps.
 *
 * <p>The shorts are fixed-point in units of 1/{@value #UNITS_PER_BLOCK} block, relative to the last
 * position the server sent for that entity - by any of these packets, {@link EntityPositionPacket},
 * or {@link AddEntityPacket} ({@code ServerEntity.sendChanges} resets its {@code VecDeltaCodec}
 * base after every send). Resolving them needs that per-connection history; see
 * {@code PlayClientHandler}.
 */
public record MoveEntityPacket(int packetId, int entityId, int deltaX, int deltaY, int deltaZ) implements Packet {

    public static final int POS_PACKET_ID = 0x36;
    public static final int POS_ROT_PACKET_ID = 0x37;

    public static final double UNITS_PER_BLOCK = 4096.0;
    private static final int STEP_MIN_BYTES = 7;

    public static MoveEntityPacket decodePos(ByteBuf in) {
        return decode(POS_PACKET_ID, in, false);
    }

    public static MoveEntityPacket decodePosRot(ByteBuf in) {
        return decode(POS_ROT_PACKET_ID, in, true);
    }

    private static MoveEntityPacket decode(int packetId, ByteBuf in, boolean hasRotation) {
        int entityId = ProtocolCodec.readVarInt(in);
        int steps = ProtocolCodec.readVarInt(in) >>> 1; // bit 0 is onGround
        // VecDelta.read's own guard: a step is at least 7 bytes.
        if (steps > in.readableBytes() / STEP_MIN_BYTES) {
            throw new IllegalStateException("Move Entity claims " + steps + " steps in " + in.readableBytes() + " bytes");
        }
        int dx = 0;
        int dy = 0;
        int dz = 0;
        for (int step = 0; step < Math.max(steps, 1); step++) {
            if (steps > 0) {
                ProtocolCodec.readVarInt(in); // ticks this step takes
            }
            dx += in.readShort();
            dy += in.readShort();
            dz += in.readShort();
        }
        if (hasRotation) {
            in.skipBytes(2);
        }
        if (in.isReadable()) {
            throw new IllegalStateException("Move Entity 0x" + Integer.toHexString(packetId)
                    + " has " + in.readableBytes() + " unread trailing byte(s)");
        }
        return new MoveEntityPacket(packetId, entityId, dx, dy, dz);
    }

    /** Applies this packet's change to {@code base}, matching vanilla's {@code VecDeltaCodec.decode}. */
    public static double apply(double base, int delta) {
        return (Math.round(base * UNITS_PER_BLOCK) + delta) / UNITS_PER_BLOCK;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("MoveEntityPacket is clientbound-only and never sent by this client");
    }
}
