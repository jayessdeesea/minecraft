package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Common shape for every packet type: an ID (docs/protocol-spec.md's per-state packet tables)
 * and the ability to write its own body. Lets the marshalling layer in {@code protocol.netty}
 * decode inbound bytes into a common supertype and encode any outbound instance the same way,
 * regardless of which specific packet type it is.
 */
public interface Packet {

    int packetId();

    void encode(ByteBuf out);
}
