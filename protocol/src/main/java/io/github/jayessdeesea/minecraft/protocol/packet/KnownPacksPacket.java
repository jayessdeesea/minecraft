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
 * <p>{@link #SERVERBOUND_PACKET_ID} is <strong>not yet confirmed</strong> - it's the commonly
 * documented value for this exchange, to be verified by whether the server actually continues
 * the Configuration sequence after we send it.
 */
public record KnownPacksPacket(List<KnownPack> packs) {

    public static final int CLIENTBOUND_PACKET_ID = 0x0E;
    public static final int SERVERBOUND_PACKET_ID = 0x07;

    public static KnownPacksPacket decode(ByteBuf in) {
        int count = ProtocolCodec.readVarInt(in);
        List<KnownPack> packs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            packs.add(KnownPack.decode(in));
        }
        return new KnownPacksPacket(List.copyOf(packs));
    }

    public void encode(ByteBuf out) {
        ProtocolCodec.writeVarInt(out, packs.size());
        for (KnownPack pack : packs) {
            pack.encode(out);
        }
    }
}
