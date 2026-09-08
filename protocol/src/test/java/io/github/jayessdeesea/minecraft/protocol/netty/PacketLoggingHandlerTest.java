package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.packet.PingPongPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.UnknownPacket;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketLoggingHandlerTest {

    @Test
    void passesInboundPacketsThroughUnchanged() {
        EmbeddedChannel channel = new EmbeddedChannel(new PacketLoggingHandler());
        PingPongPacket packet = new PingPongPacket(42L);

        channel.writeInbound(packet);

        assertEquals(packet, channel.readInbound());
    }

    @Test
    void passesOutboundPacketsThroughUnchanged() {
        EmbeddedChannel channel = new EmbeddedChannel(new PacketLoggingHandler());
        PingPongPacket packet = new PingPongPacket(7L);

        channel.writeOutbound(packet);

        assertEquals(packet, channel.readOutbound());
    }

    @Test
    void passesUnknownPacketsThroughWithoutLoggingThem() {
        EmbeddedChannel channel = new EmbeddedChannel(new PacketLoggingHandler());
        UnknownPacket packet = new UnknownPacket(0x7F, new byte[]{0x01});

        channel.writeInbound(packet);

        assertEquals(packet, channel.readInbound());
    }
}
