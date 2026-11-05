package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Player Action" (protocol 777), 0x29 ({@code ServerboundPlayerActionPacket}):
 * VarInt action, packed block position, byte face, VarInt sequence. Used here to dig: start
 * destroying a block, then - once the block's break time has passed - stop, which breaks it. The
 * server times the dig itself and refuses a stop that comes too early for the block and the tool.
 * And to stop using an item - lowering a raised shield - which, as the vanilla client sends it,
 * carries no block: position zero, face down, sequence 0.
 */
public record PlayerActionPacket(int action, int x, int y, int z, int face, int sequence) implements Packet {

    public static final int PACKET_ID = 0x29;
    // The Action enum's ordinals: START_DESTROY_BLOCK, CHANGE_DESTROY_DIRECTION (new in 26.3),
    // ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK, DROP_ALL_ITEMS, DROP_ITEM, RELEASE_USE_ITEM, ...
    public static final int START_DESTROY_BLOCK = 0;
    public static final int STOP_DESTROY_BLOCK = 3;
    public static final int RELEASE_USE_ITEM = 6;
    private static final int FACE_DOWN = 0;
    private static final int FACE_UP = 1;

    /** A dig action on the block at (x, y, z), from above. */
    public static PlayerActionPacket dig(int action, int x, int y, int z, int sequence) {
        return new PlayerActionPacket(action, x, y, z, FACE_UP, sequence);
    }

    /** Stop using the item in hand. */
    public static PlayerActionPacket releaseUseItem() {
        return new PlayerActionPacket(RELEASE_USE_ITEM, 0, 0, 0, FACE_DOWN, 0);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, action);
        out.writeLong(((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF));
        out.writeByte(face);
        ProtocolCodec.writeVarInt(out, sequence);
    }
}
