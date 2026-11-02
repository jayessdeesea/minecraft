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
 * by the app module's {@code ArchitectureTest} confining {@code io.netty} to this module).
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

    /**
     * Runs a chat command as this player, without its leading slash (for example
     * {@code "me moving out"}). Which commands, and what they say, is the caller's business. The
     * server kicks for spam (roughly ten lines in quick succession), so callers send sparingly.
     */
    void sendChatCommand(String command);

    /** Sends a plain, unsigned chat line - other players receive it as Player Chat. */
    void sendChat(String message);

    /**
     * Uses the block at ({@code x}, {@code y}, {@code z}) with the main hand - for a chest, opens
     * it, and the server answers with a {@code ContainerUpdate.Opened} and the chest's contents.
     * The server ignores a block out of reach (about 4.5 blocks from the eyes).
     */
    void useBlock(int x, int y, int z);

    /**
     * Shift-clicks {@code slot} of an open container (or container 0, the player's inventory):
     * from a chest the stack moves into the inventory; from the inventory, armour moves onto the
     * body. {@code stateId} is the one the container's last update carried.
     */
    void quickMove(int containerId, int stateId, int slot);

    /** Swaps {@code slot} of a container with hotbar slot {@code hotbarSlot}, like a number key. */
    void swapWithHotbar(int containerId, int stateId, int slot, int hotbarSlot);

    /** Closes the container this player has open. */
    void closeContainer(int containerId);

    /** Selects hotbar slot 0-8 as the item in hand. The server doesn't echo it back. */
    void selectHotbarSlot(int hotbarSlot);

    /** Whether the underlying connection is still open. */
    boolean isOpen();

    /** Closes the connection. Safe to call more than once. */
    void close();
}
