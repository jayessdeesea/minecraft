package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Player Action" (protocol 777), 0x29 ({@code ServerboundPlayerActionPacket}):
 * VarInt action, packed block position, byte face, VarInt sequence. Used here to dig: start
 * destroying a block, then - once the block's break time has passed - stop, which breaks it. The
 * server times the dig itself and refuses a stop that comes too early for the block and the tool.
 */
public record PlayerActionPacket(int action, int x, int y, int z, int sequence) implements Packet {

    public static final int PACKET_ID = 0x29;
    public static final int START_DESTROY_BLOCK = 0;
    public static final int STOP_DESTROY_BLOCK = 2;
    private static final int FACE_UP = 1;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, action);
        out.writeLong(((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF));
        out.writeByte(FACE_UP);
        ProtocolCodec.writeVarInt(out, sequence);
    }
}
