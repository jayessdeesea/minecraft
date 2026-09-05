package io.github.jayessdeesea.minecraft.protocol.packet;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Clientbound Play-state packet, officially "Login (play)" - the first packet the server sends on
 * entering Play (docs/protocol-spec.md § 3.5). Field decoding confirmed against the live dev
 * server by matching known ground truth: {@code entityId} against the server console's "logged in
 * with entity id N", and {@code maxPlayers}/{@code viewDistance}/{@code simulationDistance}/
 * {@code gameMode} against server.properties' {@code max-players}/{@code view-distance}/
 * {@code simulation-distance}/{@code gamemode}.
 *
 * <p>Only the fields confirmed this way are decoded. Several trailing fields (portal cooldown and
 * later) aren't yet - left unread, same as {@link LoginSuccessPacket}'s trailing profile
 * properties, since the frame is already length-isolated. {@code hasDeathLocation}, if true, would
 * be followed by a death dimension name and location - not decoded either, since a fresh join
 * never has one to exercise.
 */
public record PlayLoginPacket(
        int entityId,
        boolean hardcore,
        List<String> dimensionNames,
        int maxPlayers,
        int viewDistance,
        int simulationDistance,
        boolean reducedDebugInfo,
        boolean enableRespawnScreen,
        boolean doLimitedCrafting,
        int dimensionType,
        String dimensionName,
        long hashedSeed,
        int gameMode,
        int previousGameMode,
        boolean debug,
        boolean flat,
        boolean hasDeathLocation) implements Packet {

    public static final int PACKET_ID = 0x31;

    /** Generous cap on the dimension list - vanilla always sends exactly 3 (overworld/end/nether). */
    private static final int MAX_DIMENSIONS = 64;

    public static PlayLoginPacket decode(ByteBuf in) {
        int entityId = in.readInt();
        boolean hardcore = in.readBoolean();

        int dimensionCount = ProtocolCodec.readVarIntCount(in, MAX_DIMENSIONS);
        List<String> dimensionNames = new ArrayList<>(dimensionCount);
        for (int i = 0; i < dimensionCount; i++) {
            dimensionNames.add(ProtocolCodec.readString(in));
        }

        int maxPlayers = ProtocolCodec.readVarInt(in);
        int viewDistance = ProtocolCodec.readVarInt(in);
        int simulationDistance = ProtocolCodec.readVarInt(in);
        boolean reducedDebugInfo = in.readBoolean();
        boolean enableRespawnScreen = in.readBoolean();
        boolean doLimitedCrafting = in.readBoolean();
        int dimensionType = ProtocolCodec.readVarInt(in);
        String dimensionName = ProtocolCodec.readString(in);
        long hashedSeed = in.readLong();
        int gameMode = in.readUnsignedByte();
        int previousGameMode = in.readByte();
        boolean debug = in.readBoolean();
        boolean flat = in.readBoolean();
        boolean hasDeathLocation = in.readBoolean();

        return new PlayLoginPacket(entityId, hardcore, List.copyOf(dimensionNames), maxPlayers,
                viewDistance, simulationDistance, reducedDebugInfo, enableRespawnScreen,
                doLimitedCrafting, dimensionType, dimensionName, hashedSeed, gameMode,
                previousGameMode, debug, flat, hasDeathLocation);
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeInt(entityId);
        out.writeBoolean(hardcore);
        ProtocolCodec.writeVarInt(out, dimensionNames.size());
        for (String name : dimensionNames) {
            ProtocolCodec.writeString(out, name);
        }
        ProtocolCodec.writeVarInt(out, maxPlayers);
        ProtocolCodec.writeVarInt(out, viewDistance);
        ProtocolCodec.writeVarInt(out, simulationDistance);
        out.writeBoolean(reducedDebugInfo);
        out.writeBoolean(enableRespawnScreen);
        out.writeBoolean(doLimitedCrafting);
        ProtocolCodec.writeVarInt(out, dimensionType);
        ProtocolCodec.writeString(out, dimensionName);
        out.writeLong(hashedSeed);
        out.writeByte(gameMode);
        out.writeByte(previousGameMode);
        out.writeBoolean(debug);
        out.writeBoolean(flat);
        out.writeBoolean(hasDeathLocation);
    }
}
