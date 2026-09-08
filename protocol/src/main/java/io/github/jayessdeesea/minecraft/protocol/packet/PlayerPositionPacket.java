package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state packet, officially "Player Position" (protocol 776), 0x48 - confirmed by
 * decompiling the real server jar (see DEVELOPMENT.md's "Confirming wire formats against the real
 * server"): {@code net.minecraft.network.protocol.game.GameProtocols}' clientbound registration
 * order puts it at index 72 (0x48), cross-checked against two already-empirically-confirmed
 * packets in the same list (Keep Alive at index 44/0x2C, Chunk Data at index 45/0x2D both landed
 * exactly where expected). Tells the client its actual position - the first one arrives right
 * after entering Play, and it's the only source of real spawn coordinates: {@code PlayLoginPacket}
 * carries none.
 *
 * <p>{@code teleportId} must be echoed back via {@code AcceptTeleportationPacket} - see
 * {@link AcceptTeleportationPacket}. {@code relativeFlags} (which of x/y/z/yRot/xRot/velocity are
 * relative to the client's <em>current</em> value rather than absolute) is decoded but not
 * resolved - this codebase doesn't track a running position to resolve a relative update against
 * yet, so {@link #x()}/{@link #y()}/{@link #z()} are only correct when the server sends an
 * absolute update, which is what happens on initial join (the only case exercised so far). Revisit
 * if a live run ever shows a relative flag set and a resulting position that's obviously wrong.
 */
public record PlayerPositionPacket(int teleportId, double x, double y, double z,
                                    float yRot, float xRot, int relativeFlags) implements Packet {

    public static final int PACKET_ID = 0x48;

    public static PlayerPositionPacket decode(ByteBuf in) {
        int teleportId = ProtocolCodec.readVarInt(in);
        double x = in.readDouble();
        double y = in.readDouble();
        double z = in.readDouble();
        // Delta movement (velocity) - not needed here, but must still be read to stay aligned.
        in.readDouble();
        in.readDouble();
        in.readDouble();
        float yRot = in.readFloat();
        float xRot = in.readFloat();
        int relativeFlags = in.readInt();
        return new PlayerPositionPacket(teleportId, x, y, z, yRot, xRot, relativeFlags);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("PlayerPositionPacket is clientbound-only and never sent by this client");
    }
}
