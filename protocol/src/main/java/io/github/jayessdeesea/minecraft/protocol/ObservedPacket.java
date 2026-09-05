package io.github.jayessdeesea.minecraft.protocol;

/**
 * One packet seen from the server, recorded by {@link MinecraftClient#observeJoin} for states
 * (Configuration, early Play) whose packet catalog isn't fully implemented yet. A diagnostic
 * tool, not just scaffolding for one test: the honest way to pin down packet IDs is to read them
 * off a real, currently-running server rather than trust a possibly stale reference.
 */
public record ObservedPacket(String state, int packetId, int payloadLength) {
}
