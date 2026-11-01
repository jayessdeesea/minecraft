package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayLoginPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers {@link PlayClientHandler#channelInactive}'s {@code onDisconnect} signal without a live
 * server: {@code SessionStateManager} forwards every packet regardless of current state (it only
 * reacts to its own three state-implying packet types, per its own javadoc), so feeding a bare
 * {@link PlayLoginPacket} straight in is enough to reach "Play reached" without replaying the full
 * Login/Configuration handshake first. {@link MarshallingDecoder} is only in the test pipeline
 * because {@code channelActive}'s {@code transitionTo(LOGIN)} call reaches for it - it never
 * actually decodes anything here, since every packet in this test is already a decoded object.
 */
class PlayClientHandlerTest {

    private static final PlayLoginPacket PLAY_LOGIN = new PlayLoginPacket(
            1, false, List.of("minecraft:overworld"), 20, 10, 10,
            false, true, false, 0, "minecraft:overworld", 0L, 0, -1, false, false, false);

    @Test
    void firesOnDisconnectWhenTheChannelClosesUnexpectedlyAfterPlay() {
        AtomicReference<Throwable> disconnectCause = new AtomicReference<>();
        EmbeddedChannel channel = newChannel(disconnectCause);

        channel.writeInbound(PLAY_LOGIN);
        channel.close();

        assertNotNull(disconnectCause.get(), "onDisconnect should fire for an unannounced channel close");
    }

    @Test
    void doesNotFireOnDisconnectForADeliberateClose() {
        AtomicReference<Throwable> disconnectCause = new AtomicReference<>();
        CompletableFuture<PlaySession> result = new CompletableFuture<>();
        EmbeddedChannel channel = newChannel(disconnectCause, result);

        channel.writeInbound(PLAY_LOGIN);
        result.getNow(null).close();

        assertNull(disconnectCause.get(), "onDisconnect should not fire for PlaySession.close()");
    }

    @Test
    void doesNotFireOnDisconnectBeforePlayIsReached() {
        AtomicReference<Throwable> disconnectCause = new AtomicReference<>();
        EmbeddedChannel channel = newChannel(disconnectCause);

        channel.close();

        assertNull(disconnectCause.get(), "onDisconnect should not fire if Play was never reached");
    }

    private static EmbeddedChannel newChannel(AtomicReference<Throwable> disconnectCause) {
        return newChannel(disconnectCause, new CompletableFuture<>());
    }

    private static EmbeddedChannel newChannel(AtomicReference<Throwable> disconnectCause, CompletableFuture<PlaySession> result) {
        PlaySessionListeners listeners = new PlaySessionListeners(update -> { }, unload -> { }, position -> { }, entity -> { }, move -> { }, ids -> { }, health -> { }, inventory -> { }, disconnectCause::set);
        return new EmbeddedChannel(
                new MarshallingDecoder(),
                new SessionStateManager(),
                new PlayClientHandler(776, "localhost", 25565, "TestBot", result, listeners));
    }
}
