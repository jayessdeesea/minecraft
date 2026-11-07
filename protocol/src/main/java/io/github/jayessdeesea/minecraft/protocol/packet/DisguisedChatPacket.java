package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Disguised Chat", 0x21 ({@code ClientboundDisguisedChatPacket}): the
 * message as a text component (NBT), then {@code ChatType.Bound} - the chat type as a registry
 * holder (VarInt id + 1, 0 for one sent inline), the sender's name as a text component, and an
 * optional target name. What a player's unsigned chat command - {@code /msg}, {@code /me} -
 * reaches its hearers as: the server resolves the command's message as a system message, which
 * carries no sender UUID, only the sender's name.
 *
 * <p>Message and sender name are read as plain text; the target name is left unread. A chat type
 * sent inline is a violated invariant - the pinned server only sends registered ones - and fails
 * the decode, since its own layout comes before the sender's name.
 */
public record DisguisedChatPacket(String message, String senderName) implements Packet {

    public static final int PACKET_ID = 0x21;

    public static DisguisedChatPacket decode(ByteBuf in) {
        String message = Nbt.readText(in);
        if (ProtocolCodec.readVarInt(in) == 0) {
            throw new IllegalStateException("Disguised Chat with a chat type sent inline: the pinned server sends only registered ones");
        }
        String sender = Nbt.readText(in);
        return new DisguisedChatPacket(message, sender);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("DisguisedChatPacket is clientbound-only and never sent by this client");
    }
}
