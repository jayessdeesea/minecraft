package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state packet, officially "Add Entity" (protocol 776), 0x01 - ID and layout
 * confirmed with {@code javap} against the real server jar: {@code GameProtocols}' clientbound
 * registration order puts it at index 1 (the same list whose indices 0x2C/0x2D/0x31/0x48 match
 * this codebase's already-confirmed Keep Alive/Chunk Data/Login/Player Position), and
 * {@code ClientboundAddEntityPacket}'s wire constructor reads: VarInt entity ID, UUID, VarInt
 * entity-type registry ID, double x/y/z, velocity ({@code LpVec3} - see {@link #skipLpVec3}),
 * byte xRot/yRot/yHeadRot, VarInt data.
 *
 * <p>Sent for every non-player-self entity entering view - mobs, items, other players, projectiles.
 * Vanilla brackets these in bundle-delimiter packets (0x00), but still sends each one as its own
 * frame, so no bundle handling is needed to decode them.
 */
public record AddEntityPacket(int entityId, int typeId, double x, double y, double z) implements Packet {

    public static final int PACKET_ID = 0x01;

    /** Bit in {@code LpVec3}'s first byte meaning a VarInt scale extension follows. */
    private static final int LP_VEC3_CONTINUATION_FLAG = 4;

    public static AddEntityPacket decode(ByteBuf in) {
        int entityId = ProtocolCodec.readVarInt(in);
        ProtocolCodec.readUuid(in);
        int typeId = ProtocolCodec.readVarInt(in);
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        skipLpVec3(in);
        in.skipBytes(3); // xRot, yRot, yHeadRot
        ProtocolCodec.readVarInt(in); // type-specific data
        // The velocity field is variable-length; a mis-sized skip would leave bytes behind here.
        if (in.isReadable()) {
            throw new IllegalStateException("Add Entity has " + in.readableBytes() + " unread trailing byte(s)");
        }
        return new AddEntityPacket(entityId, typeId, x, y, z);
    }

    /**
     * Skips a {@code net.minecraft.network.LpVec3}-encoded vector without decoding it. Variable
     * length: a zero first byte is the whole encoding (zero vector); otherwise a second byte and a
     * 4-byte unsigned int follow, plus a VarInt if the first byte carries the continuation flag.
     */
    static void skipLpVec3(ByteBuf in) {
        int first = in.readUnsignedByte();
        if (first == 0) {
            return;
        }
        in.skipBytes(1 + 4);
        if ((first & LP_VEC3_CONTINUATION_FLAG) != 0) {
            ProtocolCodec.readVarInt(in);
        }
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("AddEntityPacket is clientbound-only and never sent by this client");
    }
}
