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
     * Reports this client's current position and look direction to the server - yaw in degrees
     * (0 south, 90 west), pitch negative looking up. At most once per tick (see {@link #endTick});
     * the server tolerates redundant reports of an unchanged position.
     */
    void reportPosition(double x, double y, double z, float yaw, float pitch, boolean onGround);

    /**
     * Ends this client's tick, as a vanilla client does after every one. The server accepts at
     * most one {@link #reportPosition} per tick - a second before the next {@code endTick}
     * disconnects the player - so a caller reporting every tick must end every tick.
     */
    void endTick();

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
     * Uses the item in hand on {@code face} of the block at ({@code x}, {@code y}, {@code z}): with
     * a block in hand, places it in the cell on that side. The server refuses a cell a player
     * stands in, and one out of reach.
     */
    void useBlockFace(int x, int y, int z, BlockFace face);

    /**
     * Uses the item in hand at nothing in particular - for food, starts eating it. The server
     * finishes the meal on its own once the item's eating time has passed (1.6 seconds for most
     * food), as long as the same item stays in hand.
     */
    void useItem(float yaw, float pitch);

    /**
     * Tells the server this player is (or stops) sprinting. Movement speed is the client's own; the
     * flag is what makes the server charge sprinting's food cost, so a client that moves at sprint
     * speed must set it.
     */
    void setSprinting(boolean sprinting);

    /**
     * Shift-clicks {@code slot} of an open container (or container 0, the player's inventory):
     * from a chest the stack moves into the inventory; from the inventory, armour moves onto the
     * body. {@code stateId} is the one the container's last update carried.
     */
    void quickMove(int containerId, int stateId, int slot);

    /**
     * Clicks {@code slot} of a container as a mouse does: with the cursor empty, the left button
     * ({@code rightButton} false) picks up the whole stack and the right half of it; with a stack
     * on the cursor, the left button puts it all down and the right one item. How crafting grids
     * are filled.
     */
    void click(int containerId, int stateId, int slot, boolean rightButton);

    /** Swaps {@code slot} of a container with hotbar slot {@code hotbarSlot}, like a number key. */
    void swapWithHotbar(int containerId, int stateId, int slot, int hotbarSlot);

    /**
     * Throws one item from {@code slot} of a container, like the drop key over it: the server drops
     * it in front of the player, the way it faces, and any player can pick it up two seconds later.
     */
    void throwOne(int containerId, int stateId, int slot);

    /**
     * Starts digging the block at ({@code x}, {@code y}, {@code z}). It breaks only when
     * {@link #finishDigging} follows after the block's break time - bare-handed, a log takes 3
     * seconds; the server times it and ignores an early finish.
     */
    void startDigging(int x, int y, int z);

    /** Finishes digging the block {@link #startDigging} started: breaks it if enough time has passed. */
    void finishDigging(int x, int y, int z);

    /** Closes the container this player has open. */
    void closeContainer(int containerId);

    /** Selects hotbar slot 0-8 as the item in hand. The server doesn't echo it back. */
    void selectHotbarSlot(int hotbarSlot);

    /** Whether the underlying connection is still open. */
    boolean isOpen();

    /** Closes the connection. Safe to call more than once. */
    void close();
}
