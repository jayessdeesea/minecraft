package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration-state Known Packs exchange (docs/protocol-spec.md § 3.4): same wire shape - a
 * VarInt count followed by that many {@link KnownPack} entries - in both directions, like
 * {@link PingPongPacket}, so one type covers both.
 *
 * <p>The server sends its known packs (clientbound "Select Known Packs") and waits for the
 * client to reply in kind before continuing the Configuration sequence - confirmed empirically
 * against the local dev server: {@link #CLIENTBOUND_PACKET_ID} content decodes cleanly as one
 * {@code (minecraft, core, 26.2)} entry, and it was the last packet before the server went quiet.
 *
 * <p>{@link #SERVERBOUND_PACKET_ID} was the commonly documented value for this exchange, unconfirmed
 * by content since our own reply doesn't echo back - confirmed instead by behavior: sending it is
 * what unblocked Registry Data/Update Tags/Finish Configuration against the live dev server.
 */
public record KnownPacksPacket(List<KnownPack> packs) implements Packet {

    public static final int CLIENTBOUND_PACKET_ID = 0x0E;
    public static final int SERVERBOUND_PACKET_ID = 0x07;

    /** Generous - we've only ever observed 1 in practice - but bounded against a hostile/buggy count. */
    private static final int MAX_PACKS = 1024;

    public static KnownPacksPacket decode(ByteBuf in) {
        int count = ProtocolCodec.readVarIntCount(in, MAX_PACKS);
        List<KnownPack> packs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            packs.add(KnownPack.decode(in));
        }
        return new KnownPacksPacket(List.copyOf(packs));
    }

    /** Always {@link #SERVERBOUND_PACKET_ID} - we only ever construct one of these to send our reply. */
    @Override
    public int packetId() {
        return SERVERBOUND_PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, packs.size());
        for (KnownPack pack : packs) {
            pack.encode(out);
        }
    }
}
