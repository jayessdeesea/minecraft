package io.github.jayessdeesea.minecraft.protocol;

import java.util.UUID;

/**
 * One entity entering view, handed to whatever listener {@link MinecraftClient#join} was given -
 * the Netty/packet-free shape of an Add Entity packet (mirrors {@link ChunkUpdate}). {@code typeId}
 * is the raw entity-type registry ID; classifying it (hostile or not) is {@code bot}'s concern.
 * {@code uuid} identifies the entity across connections - for a player in offline mode, it's
 * derived from the player's name.
 * Later positions arrive as {@link EntityMove}s.
 */
public record EntityUpdate(int entityId, UUID uuid, int typeId, double x, double y, double z) {
}
