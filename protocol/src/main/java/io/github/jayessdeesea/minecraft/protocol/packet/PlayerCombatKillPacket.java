package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

/**
 * Clientbound Play-state "Player Combat Kill", 0x45 ({@code ClientboundPlayerCombatKillPacket}):
 * sent to a player as it dies - its entity id (VarInt), then the death message as a text component
 * (NBT). The message is translatable ({@code death.attack.arrow} and so on, with the dead player's
 * and the killer's names as its arguments), read as its key and arguments (see {@link Nbt#readText}).
 */
public record PlayerCombatKillPacket(int playerId, String message) implements Packet {

    public static final int PACKET_ID = 0x45;

    public static PlayerCombatKillPacket decode(ByteBuf in) {
        int playerId = ProtocolCodec.readVarInt(in);
        return new PlayerCombatKillPacket(playerId, Nbt.readText(in));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("PlayerCombatKillPacket is clientbound-only and never sent by this client");
    }
}
