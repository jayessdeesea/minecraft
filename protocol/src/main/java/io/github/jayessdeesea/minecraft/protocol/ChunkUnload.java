package io.github.jayessdeesea.minecraft.protocol;

/** A chunk left this connection's view (Forget Level Chunk) - its terrain is no longer kept current. */
public record ChunkUnload(int chunkX, int chunkZ) {
}
