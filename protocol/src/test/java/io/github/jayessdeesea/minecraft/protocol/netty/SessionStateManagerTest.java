package io.github.jayessdeesea.minecraft.protocol.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The one way a handler changes the connection's state: {@link SessionStateManager#beginHandshake}, once, out of
 * HANDSHAKE. Every other transition is the manager's own, on the packet that implies it.
 */
class SessionStateManagerTest {

    private final SessionStateManager manager = new SessionStateManager();
    private final EmbeddedChannel channel = new EmbeddedChannel(new MarshallingDecoder(), manager);
    private final ChannelHandlerContext context = channel.pipeline().context(manager);

    @Test
    void theHandshakeTakesTheConnectionToTheStateItNamed() {
        manager.beginHandshake(context, ProtocolState.STATUS);

        assertEquals(ProtocolState.STATUS, manager.state());
    }

    @Test
    void aHandshakeIsBegunOnce() {
        manager.beginHandshake(context, ProtocolState.LOGIN);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> manager.beginHandshake(context, ProtocolState.LOGIN));
        assertEquals("A handshake was already begun: the connection is in LOGIN", refused.getMessage());
        assertEquals(ProtocolState.LOGIN, manager.state());
    }

    @Test
    void aHandshakeNamesLoginOrStatusOnly() {
        assertThrows(IllegalStateException.class, () -> manager.beginHandshake(context, ProtocolState.PLAY));
        assertEquals(ProtocolState.HANDSHAKE, manager.state());
    }
}
