package io.github.jayessdeesea.minecraft.protocol;

/**
 * One entity entering view, handed to whatever listener {@link MinecraftClient#join} was given -
 * the Netty/packet-free shape of an Add Entity packet (mirrors {@link ChunkUpdate}). {@code typeId}
 * is the raw entity-type registry ID; classifying it (hostile or not) is {@code bot}'s concern.
 *
 * <p>The position is where the entity was when it came into view. Nothing decodes entity movement
 * packets yet, so a caller holding onto this has a last-known position, not a live one.
 */
public record EntityUpdate(int entityId, int typeId, double x, double y, double z) {
}
