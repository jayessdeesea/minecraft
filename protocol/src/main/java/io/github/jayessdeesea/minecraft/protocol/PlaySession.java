package io.github.jayessdeesea.minecraft.protocol;

/**
 * A live Play-state connection, handed back once {@link MinecraftClient#join} reaches Play.
 * Chunk data and confirmed position are observed via listeners passed into {@code join} itself
 * (see {@link ChunkUpdate}/{@link PlayerPosition}), not through this interface - this interface
 * grows a field or method only once something real needs it, the same way {@link PlayState}
 * itself grows one confirmed field at a time.
 *
 * <p>{@link #reportPosition} is the first thing this session can *originate* rather than just
 * react to or observe - docs/protocol-pipeline.md's control-plane tenet 4 called this moment out
 * specifically: once something outside {@code protocol} needs to send a real command, it goes
 * through an explicit method here, never through {@code bot} reaching into Netty directly (enforced
 * by the app module's {@code ArchitectureTest} confining {@code io.netty} to this module). No
 * {@code onTick}/chat-sending yet - those packets aren't decoded.
 *
 * <p>Netty-free by design, like {@link PlayState} - {@code bot} depends on this interface without
 * pulling in {@code io.netty}.
 */
public interface PlaySession {

    /** The session state captured when Play was reached - see {@link PlayState}. */
    PlayState playState();

    /**
     * Reports this client's current position to the server - no look direction, since nothing in
     * this codebase tracks one yet (see {@code MovePlayerPosPacket}'s javadoc). Safe to call
     * every tick; the server tolerates redundant reports of an unchanged position.
     */
    void reportPosition(double x, double y, double z, boolean onGround);

    /**
     * Asks the server to respawn this (dead) player. Whether and when to respawn is the caller's
     * decision, not {@code protocol}'s - see docs/protocol-pipeline.md on game decisions staying out
     * of the control plane. A dead player is sent no chunks or entities until it does.
     */
    void respawn();

    /**
     * Attacks the entity (with an arm swing, as a vanilla client does). The server silently ignores
     * an attack that's out of range, so whether to attack - and how often - is the caller's call.
     */
    void attack(int entityId);

    /** Whether the underlying connection is still open. */
    boolean isOpen();

    /** Closes the connection. Safe to call more than once. */
    void close();
}
