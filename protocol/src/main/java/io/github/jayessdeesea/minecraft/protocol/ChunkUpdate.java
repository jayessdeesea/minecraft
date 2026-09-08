package io.github.jayessdeesea.minecraft.protocol;

import java.util.List;

/**
 * One decoded Chunk Data packet, handed to whatever listener {@link MinecraftClient#join} was
 * given - the Netty/packet-free shape a caller outside {@code protocol} is allowed to depend on
 * (mirrors {@link PlayState}/{@link ObservedPacket}, not the internal {@code ChunkDataPacket}).
 *
 * <p>{@code sectionBlockStates} is one {@code int[4096]} of global block-state IDs per vertical
 * section, bottom section first - exactly the shape {@code bot.world.ChunkCache} needs. Delivered
 * on the connection's own Netty event loop thread; a listener that calls into a shared structure
 * (like {@code ChunkCache}) needs that structure to already be safe for concurrent callers from
 * multiple soldiers' event loops - {@code ChunkCache}'s {@code ConcurrentHashMap.compute()} basis
 * already is, so no additional synchronization belongs here.
 */
public record ChunkUpdate(int chunkX, int chunkZ, List<int[]> sectionBlockStates) {
}
