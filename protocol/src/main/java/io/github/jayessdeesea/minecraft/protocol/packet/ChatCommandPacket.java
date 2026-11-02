package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Serverbound Play-state packet, officially "Chat Command" (protocol 777), 0x07 - ID from
 * {@code GameProtocols}' serverbound registration order, consistent with Chunk Batch Received
 * (0x0B) and Client Command (0x0C) already confirmed. Body is the command line without its
 * leading slash, one {@code writeUtf} string ({@code ServerboundChatCommandPacket}).
 *
 * <p>Unsigned: the offline dev server never enforces secure profiles
 * ({@code DedicatedServer.enforceSecureProfile} requires online mode), so commands with message
 * arguments such as {@code me} are accepted without the signed variant.
 *
 * <p>The server kicks a player for any character vanilla chat disallows ({@code §}, control
 * characters including newlines), so the constructor refuses them rather than sending a line that
 * ends the connection.
 */
public record ChatCommandPacket(String command) implements Packet {

    public static final int PACKET_ID = 0x07;

    public ChatCommandPacket {
        refuseKickCharacters(command);
    }

    static void refuseKickCharacters(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '§' || c < ' ' || c == 127) {
                throw new IllegalArgumentException("character the server kicks for, at " + i + ": " + line);
            }
        }
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        ProtocolCodec.writeString(out, command);
    }
}
