package io.github.jayessdeesea.minecraft.protocol;

import io.github.jayessdeesea.minecraft.protocol.netty.JoinClientHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.StatusClientHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.VarIntFrameDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.VarIntFrameEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
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
        return connect(host, port, result, channel -> channel.pipeline()
                .addLast(new VarIntFrameDecoder())
                .addLast(new VarIntFrameEncoder())
                .addLast(new StatusClientHandler(ProtocolVersion.CURRENT, host, port, result)));
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
        return connect(host, port, result, channel -> channel.pipeline()
                .addLast(new VarIntFrameDecoder())
                .addLast(new VarIntFrameEncoder())
                .addLast(new JoinClientHandler(ProtocolVersion.CURRENT, host, port, username, result)));
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
