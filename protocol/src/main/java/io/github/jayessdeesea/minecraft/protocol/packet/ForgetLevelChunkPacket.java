package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Forget Level Chunk" (protocol 777), 0x26 - the chunk left this client's
 * view. Body is a packed long ({@code ChunkPos.unpack}): chunk x in the low 32 bits, z in the high 32.
 */
public record ForgetLevelChunkPacket(int chunkX, int chunkZ) implements Packet {

    public static final int PACKET_ID = 0x26;

    public static ForgetLevelChunkPacket decode(ByteBuf in) {
        long packed = in.readLong();
        return new ForgetLevelChunkPacket((int) packed, (int) (packed >> 32));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("ForgetLevelChunkPacket is clientbound-only and never sent by this client");
    }
}
