package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Chunk Batch Received" (protocol 777), 0x0B - the acknowledgement for
 * {@link ChunkBatchFinishedPacket}, carrying the chunks-per-tick rate the client wants (a float the
 * server clamps). Vanilla derives it from measured processing time; this client decodes chunks
 * cheaply enough to ask for a generous fixed rate.
 */
public record ChunkBatchReceivedPacket(float desiredChunksPerTick) implements Packet {

    public static final int PACKET_ID = 0x0B;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeFloat(desiredChunksPerTick);
    }
}
