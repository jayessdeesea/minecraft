package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Chunk Batch Finished" (protocol 777), 0x0B - VarInt batch size. The
 * server streams chunks in batches and sends the next batch only after the client answers this with
 * {@link ChunkBatchReceivedPacket}; a client that never answers gets the first few batches and
 * then no more terrain at all, however far it walks.
 */
public record ChunkBatchFinishedPacket(int batchSize) implements Packet {

    public static final int PACKET_ID = 0x0B;

    public static ChunkBatchFinishedPacket decode(ByteBuf in) {
        return new ChunkBatchFinishedPacket(ProtocolCodec.readVarInt(in));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("ChunkBatchFinishedPacket is clientbound-only and never sent by this client");
    }
}
