package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state packet, officially "Chat" (protocol 777), 0x09 - ID from
 * {@code GameProtocols}' serverbound registration order, layout from
 * {@code ServerboundChatPacket.write}: message ({@code writeUtf}, at most 256 characters), long
 * timestamp (epoch millis), long salt, optional signature (boolean, then 256 bytes), then
 * {@code LastSeenMessages.Update}: VarInt offset, a 20-bit fixed bit set (3 bytes), byte checksum.
 *
 * <p>Unsigned, with nothing acknowledged, for the same reason as {@link ChatCommandPacket}: the
 * offline dev server doesn't enforce secure profiles. The server relays it to every player as
 * Player Chat ({@link PlayerChatPacket}) - unlike an unsigned chat <em>command</em> such as
 * {@code /me}, which arrives as Disguised Chat with no sender UUID.
 */
public record ChatPacket(String message, long timestampMillis, long salt) implements Packet {

    public static final int PACKET_ID = 0x09;

    public ChatPacket {
        if (message.length() > PlayerChatPacket.MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("chat message longer than " + PlayerChatPacket.MAX_MESSAGE_LENGTH + ": " + message);
        }
        ChatCommandPacket.refuseKickCharacters(message);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeString(out, message);
        out.writeLong(timestampMillis);
        out.writeLong(salt);
        out.writeBoolean(false); // no signature
        ProtocolCodec.writeVarInt(out, 0); // last seen: offset
        out.writeZero(3);                  // ... nothing acknowledged (20 bits)
        out.writeByte(0);                  // ... checksum
    }
}
