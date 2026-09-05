package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.netty.buffer.ByteBuf;

/**
 * Decodes one packet ID + body, for whichever {@link ProtocolState} the implementation is
 * catalogued for. The session-manager handler swaps which implementation
 * {@link MarshallingDecoder} uses on every state transition - see docs/protocol-spec.md § 3 for
 * why the same numeric ID means different things in different states.
 */
public interface PacketMarshaller {

    Packet decode(int packetId, ByteBuf data);
}
