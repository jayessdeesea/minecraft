package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state reply to {@link KeepAliveRequestPacket} - echoes the same opaque Long
 * back unchanged. {@code PACKET_ID} was hard to derive by our usual by-content method: unlike a
 * clientbound packet, there's no way to confirm a serverbound ID by passively observing traffic,
 * since we're the one who has to originate it.
 *
 * <p>Three candidates were tried by active probing against the live dev server (send immediately
 * on entering Play, see what the server does). Two were cleanly ruled out - the server's own
 * decoder exceptions named the real packet at each ID: 0x2C is {@code ServerboundPlayerLoadedPacket}
 * (empty body), 0x1A is {@code ServerboundInteractPacket} (4-byte body). The third, 0x1C, produced
 * an ambiguous result at the time - an immediate "Timed out" disconnect plus a
 * {@code handleDisconnection() called twice} warning server-side - that looked like desync rather
 * than acceptance, so it was left unconfirmed pending a real client capture.
 *
 * <p>Cross-checked afterward against minecraft.wiki's Java Edition protocol page for protocol 776
 * (our pinned version, AGENTS.md § Target environment): it lists serverbound Keep Alive at 0x1C -
 * the same candidate flagged ambiguous, not one of the two cleanly ruled out. That reframes the
 * earlier result: a wrong packet ID produces a decoder exception (as it did for the other two
 * candidates), so 0x1C never being rejected as unrecognized is consistent with it being correct -
 * the "Timed out" was very likely this diagnostic tool's own fixed observation window closing the
 * connection out from under the reply, not the server rejecting the packet. Confirmed live against
 * the dev server with a real reply wired into {@code JoinClientHandler} (Keep Alive round-trips
 * without disconnecting) - see JoinObservationLiveIT run notes.
 */
public record KeepAliveResponsePacket(long id) implements Packet {

    /** Confirmed via minecraft.wiki (protocol 776) and live round-trip against the dev server. */
    public static final int PACKET_ID = 0x1C;

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeLong(id);
    }
}
