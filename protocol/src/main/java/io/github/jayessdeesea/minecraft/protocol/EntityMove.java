package io.github.jayessdeesea.minecraft.protocol;

/**
 * An entity's new absolute position, after {@code protocol} has resolved the server's relative
 * Move Entity changes (or applied a Position Sync / Teleport) against this connection's own record
 * of where that entity was. Always absolute, so a listener shared by several connections can apply
 * it from any of them without double-counting.
 */
public record EntityMove(int entityId, double x, double y, double z) {
}
