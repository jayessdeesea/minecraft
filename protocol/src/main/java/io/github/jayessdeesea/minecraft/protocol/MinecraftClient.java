package io.github.jayessdeesea.minecraft.protocol;

import io.github.jayessdeesea.minecraft.protocol.netty.CompressionDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.CompressionEncoder;
import io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.FrameEncoder;
import io.github.jayessdeesea.minecraft.protocol.netty.JoinClientHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.MarshallingDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.MarshallingEncoder;
import io.github.jayessdeesea.minecraft.protocol.netty.SessionStateManager;
import io.github.jayessdeesea.minecraft.protocol.netty.StatusClientHandler;
import io.github.jayessdeesea.minecraft.protocol.packet.Packet;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Public entry point for the {@code protocol} module. Consumers depend on this class and its
 * result types ({@link StatusResult}, {@link ObservedPacket}, {@link ProtocolVersion}) only -
 * everything under {@code .netty} and {@code .packet} is an implementation detail (enforced by
 * the ArchUnit rules in the app module).
 */
public final class MinecraftClient {

    private MinecraftClient() {
    }

    /**
     * Performs a Status-state server-list ping: connects, runs Handshake -&gt; Status Request
     * -&gt; Status Response -&gt; Ping Request -&gt; Pong Response, then disconnects.
     */
    public static CompletableFuture<StatusResult> pingStatus(String host, int port) {
        CompletableFuture<StatusResult> result = new CompletableFuture<>();
        return connect(host, port, result, channel -> addPipeline(channel,
                new StatusClientHandler(ProtocolVersion.CURRENT, host, port, result)));
    }

    /**
     * Logs in, then <em>observes</em> (rather than drives to completion) the Configuration state
     * that follows - see {@link JoinClientHandler}'s class-level note for why. Completes after a
     * few seconds with every Configuration packet ID/length seen, then disconnects. This is a
     * diagnostic step toward implementing Configuration for real, not the shape a Soldier's
     * persistent connection will end up using.
     */
    public static CompletableFuture<List<ObservedPacket>> observeJoin(String host, int port, String username) {
        CompletableFuture<List<ObservedPacket>> result = new CompletableFuture<>();
        return connect(host, port, result, channel -> addPipeline(channel,
                new JoinClientHandler(ProtocolVersion.CURRENT, host, port, username, result)));
    }

    /**
     * Wires the shared wire-mechanics chain (docs/protocol-spec.md § 2/§ 3) ahead of whichever
     * control handler owns the actual connection: framing, then compression (initially a
     * passthrough - see {@link CompressionDecoder}), then marshalling (initially
     * {@code HandshakeMarshaller} - see {@link MarshallingDecoder}), then {@link SessionStateManager}
     * (owns the FSM, observes everything, sends nothing), then the control handler (sends every
     * reply, holds no state of its own). Registration order here is what makes the outbound
     * direction come out correct too: Netty runs outbound handlers in the reverse of their
     * registration order, so a packet the control handler writes flows marshaller -&gt; compressor
     * -&gt; framer -&gt; socket, the mirror of how inbound bytes arrive.
     */
    private static void addPipeline(SocketChannel channel, SimpleChannelInboundHandler<Packet> controlHandler) {
        channel.pipeline()
                .addLast(new FrameDecoder())
                .addLast(new FrameEncoder())
                .addLast(new CompressionDecoder())
                .addLast(new CompressionEncoder())
                .addLast(new MarshallingDecoder())
                .addLast(new MarshallingEncoder())
                .addLast(new SessionStateManager())
                .addLast(controlHandler);
    }

    /**
     * Shared connect/teardown: spins up a single-threaded event loop for this one connection and
     * shuts it down once {@code result} completes either way. Fine for occasional, short-lived
     * connections like these two; a Soldier's long-lived Play-state connection will eventually
     * want a shared event loop instead of one per connection.
     */
    private static <T> CompletableFuture<T> connect(String host, int port, CompletableFuture<T> result,
                                                      Consumer<SocketChannel> pipelineInitializer) {
        EventLoopGroup group = new NioEventLoopGroup(1);

        new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        pipelineInitializer.accept(channel);
                    }
                })
                .connect(host, port)
                .addListener(connectFuture -> {
                    if (!connectFuture.isSuccess()) {
                        result.completeExceptionally(connectFuture.cause());
                    }
                });

        result.whenComplete((value, error) -> group.shutdownGracefully(0, 1, TimeUnit.SECONDS));
        return result;
    }
}
