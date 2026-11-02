package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * Clientbound Play-state packet, officially "Player Chat" (protocol 776), 0x41 - ID from
 * {@code GameProtocols}' clientbound registration order, layout from
 * {@code ClientboundPlayerChatPacket}'s wire constructor: VarInt global index, UUID sender, VarInt
 * index, optional signature (boolean, then 256 bytes - {@code MessageSignature.read}), then the
 * signed body ({@code SignedMessageBody.Packed}): the message as a string of at most 256
 * characters, timestamp, salt, last-seen list; then unsigned content, filter mask and chat type.
 *
 * <p>Only sender and message are read. Everything after the message is left unread: the frame is
 * already length-isolated, so stopping early is safe (same reasoning as {@code UnknownPacket}),
 * and the rest - an NBT text component, a registry-bound chat type - isn't needed to know who said
 * what. In offline mode a player's UUID is derived from its name, so the sender identifies the
 * player without decoding player info.
 */
public record PlayerChatPacket(UUID sender, String message) implements Packet {

    public static final int PACKET_ID = 0x41;

    /** {@code SignedMessageBody.Packed} reads the message with {@code readUtf(256)}. */
    static final int MAX_MESSAGE_LENGTH = 256;
    private static final int SIGNATURE_BYTES = 256;

    public static PlayerChatPacket decode(ByteBuf in) {
        ProtocolCodec.readVarInt(in); // global index
        UUID sender = ProtocolCodec.readUuid(in);
        ProtocolCodec.readVarInt(in); // index
        if (in.readBoolean()) {
            in.skipBytes(SIGNATURE_BYTES);
        }
        String message = ProtocolCodec.readString(in);
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalStateException("chat message of " + message.length() + " characters exceeds " + MAX_MESSAGE_LENGTH);
        }
        return new PlayerChatPacket(sender, message);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("PlayerChatPacket is clientbound-only and never sent by this client");
    }
}
