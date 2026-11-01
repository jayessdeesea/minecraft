package io.github.jayessdeesea.minecraft.protocol;

import java.util.List;
import java.util.function.Consumer;

/**
 * The things a caller of {@link MinecraftClient#join} can be notified about, bundled into one
 * value type instead of growing {@code join}'s parameter list further - its javadoc flagged a
 * third {@code Consumer} parameter as the trigger for this refactor, and {@code onDisconnect}
 * below is that third one.
 *
 * <p>{@code onDisconnect} fires at most once, on the connection's own Netty event loop thread,
 * when the channel closes for any reason other than the caller's own {@link PlaySession#close()} -
 * a watchdog-triggered timeout (no inbound traffic within the configured idle window - see
 * {@code ConnectionWatchdogHandler}), a decode/protocol exception, or the server closing the socket
 * on its own. It never fires for a deliberate {@code close()}.
 *
 * <p>This is the signal a caller needs in order to reconnect - {@code protocol} deliberately does
 * not attempt reconnection itself. A fresh connection needs a fresh join sequence and, for a
 * Soldier, re-seeding its position and order from the new session's own spawn - both are
 * {@code app}/{@code bot} concerns, not {@code protocol}'s.
 */
public record PlaySessionListeners(Consumer<ChunkUpdate> onChunkUpdate,
                                    Consumer<ChunkUnload> onChunkUnload,
                                    Consumer<PlayerPosition> onPlayerPosition,
                                    Consumer<EntityUpdate> onEntitySpawn,
                                    Consumer<EntityMove> onEntityMove,
                                    Consumer<List<Integer>> onEntitiesRemoved,
                                    Consumer<HealthUpdate> onHealth,
                                    Consumer<Throwable> onDisconnect) {

    public static PlaySessionListeners none() {
        return new PlaySessionListeners(update -> { }, unload -> { }, position -> { }, entity -> { }, move -> { }, ids -> { }, health -> { }, cause -> { });
    }
}
