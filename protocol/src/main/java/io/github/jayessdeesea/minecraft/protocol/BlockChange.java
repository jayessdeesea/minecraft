package io.github.jayessdeesea.minecraft.protocol;

/** A block in a loaded chunk that became a different block state (global block-state ID). */
public record BlockChange(int x, int y, int z, int stateId) {
}
