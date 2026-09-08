package io.github.jayessdeesea.minecraft.protocol;

/**
 * A confirmed absolute position, handed to whatever listener {@link MinecraftClient#join} was
 * given - the Netty/packet-free shape a caller outside {@code protocol} is allowed to depend on
 * (mirrors {@link PlayState}/{@link ChunkUpdate}, not the internal {@code PlayerPositionPacket}).
 *
 * <p>Deliberately its own tiny type here rather than {@code command.Position} - {@code protocol}
 * has no dependency on {@code command} (see AGENTS.md's module layout: it's the other way around),
 * so it can't reuse that type any more than {@code command.Position}'s own javadoc says it could
 * reuse one from here.
 */
public record PlayerPosition(double x, double y, double z) {
}
