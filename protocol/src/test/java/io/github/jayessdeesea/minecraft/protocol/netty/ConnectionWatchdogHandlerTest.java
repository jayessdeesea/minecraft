package io.github.jayessdeesea.minecraft.protocol.netty;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionWatchdogHandlerTest {

    @Test
    void closesTheChannelOnReaderIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionWatchdogHandler());

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT);

        assertFalse(channel.isOpen(), "channel should be closed after a reader-idle event");
    }

    @Test
    void ignoresEventsThatAreNotReaderIdle() {
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionWatchdogHandler());

        channel.pipeline().fireUserEventTriggered(IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT);

        assertTrue(channel.isOpen(), "a writer-idle event should not close the channel");
    }

    @Test
    void leavesAHealthyChannelOpen() {
        EmbeddedChannel channel = new EmbeddedChannel(new ConnectionWatchdogHandler());

        assertTrue(channel.isOpen());
    }
}
