package io.github.jayessdeesea.minecraft.protocol;

/**
 * One entity entering view, handed to whatever listener {@link MinecraftClient#join} was given -
 * the Netty/packet-free shape of an Add Entity packet (mirrors {@link ChunkUpdate}). {@code typeId}
 * is the raw entity-type registry ID; classifying it (hostile or not) is {@code bot}'s concern.
 * Later positions arrive as {@link EntityMove}s.
 */
public record EntityUpdate(int entityId, int typeId, double x, double y, double z) {
}
