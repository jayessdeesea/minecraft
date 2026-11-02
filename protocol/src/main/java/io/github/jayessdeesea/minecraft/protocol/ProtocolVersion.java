package io.github.jayessdeesea.minecraft.protocol;

/**
 * The single pinned Minecraft protocol version this client speaks - see
 * AGENTS.md § Target environment for which game version this tracks, and why it's a hard pin
 * rather than a negotiated range.
 */
public final class ProtocolVersion {

    /** Protocol version for Minecraft Java Edition 26.3. */
    public static final int CURRENT = 777;

    private ProtocolVersion() {
    }
}
