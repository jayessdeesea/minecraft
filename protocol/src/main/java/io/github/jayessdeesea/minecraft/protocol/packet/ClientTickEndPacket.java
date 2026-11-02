package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state "Client Tick End" (protocol 777), 0x0D - empty body. A vanilla client
 * sends it at the end of every tick. The server allows one position report per client tick: a
 * second Move Player before the next Client Tick End disconnects the player as "Invalid move
 * player packet" ({@code ServerGamePacketListenerImpl.handleMovePlayer}'s
 * {@code receivedPositionThisTick}, cleared only by {@code handleClientTickEnd}).
 */
public record ClientTickEndPacket() implements Packet {

    public static final int PACKET_ID = 0x0D;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
    }
}
