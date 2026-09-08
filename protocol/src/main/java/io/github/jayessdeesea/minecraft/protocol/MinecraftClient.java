package io.github.jayessdeesea.minecraft.protocol;

import io.github.jayessdeesea.minecraft.protocol.netty.CompressionDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.CompressionEncoder;
import io.github.jayessdeesea.minecraft.protocol.netty.ConnectionWatchdogHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.FrameDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.FrameEncoder;
import io.github.jayessdeesea.minecraft.protocol.netty.FrameStatsHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.JoinClientHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.MarshallingDecoder;
import io.github.jayessdeesea.minecraft.protocol.netty.MarshallingEncoder;
import io.github.jayessdeesea.minecraft.protocol.netty.PacketLoggingHandler;
import io.github.jayessdeesea.minecraft.protocol.netty.PlayClientHandler;
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
import io.netty.handler.timeout.IdleStateHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Public entry point for the {@code protocol} module. Consumers depend on this class and its
 * result/parameter types ({@link StatusResult}, {@link ObservedPacket}, {@link ProtocolVersion},
 * {@link PlaySession}, {@link PlaySessionListeners}, {@link PlayState}, {@link ChunkUpdate},
 * {@link PlayerPosition}) only - everything under {@code .netty} and {@code .packet} is an
 * implementation detail (enforced by the ArchUnit rules in the app module).
 */
public final class MinecraftClient {

    // Vanilla's Keep Alive cadence is ~15s (KeepAliveRequestPacket) - double it as margin against
    // any single missed/delayed Keep Alive without flagging a genuinely healthy connection as dead.
    private static final int READER_IDLE_TIMEOUT_SECONDS = 30;

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
     * Equivalent to {@link #join(String, int, String, PlaySessionListeners)} with
     * {@link PlaySessionListeners#none()} - for callers (diagnostics, tests) that only care about
     * the connection staying open, not the world data flowing through it or being told when it
     * unexpectedly dies.
     */
    public static CompletableFuture<PlaySession> join(String host, int port, String username) {
        return join(host, port, username, PlaySessionListeners.none());
    }

    /**
     * Logs in and drives Handshake -&gt; Login -&gt; Configuration -&gt; Play the same way
     * {@link #observeJoin} does, but stays connected: completes with a {@link PlaySession} the
     * moment Play is reached (instead of observing it for a fixed window and disconnecting) and
     * keeps replying to Keep Alive indefinitely. This is the entry point a persistent Soldier
     * connection uses - {@link #observeJoin} remains the diagnostic tool for cataloging packets,
     * this is for actually staying in the game.
     *
     * <p>{@code listeners}' callbacks are each called on this connection's own event loop thread -
     * see {@link PlayClientHandler}'s javadoc for why that's fine to call straight into a shared
     * structure like {@code bot.world.ChunkCache} with no extra synchronization. Registering them
     * here, at connect time, rather than on the returned {@link PlaySession} later, is deliberate:
     * it means there is no window between "Play reached" and "listener attached" for an early
     * chunk or position confirmation to be missed.
     *
     * <p>The connection is also watched by an idle-read guard (see {@code addPipeline}) - if the
     * server goes silent for longer than a Keep Alive interval should allow, the connection is
     * closed and {@code listeners.onDisconnect()} fires, so a caller doesn't have to poll
     * {@link PlaySession#isOpen()} to notice a silently-dead connection.
     */
    public static CompletableFuture<PlaySession> join(String host, int port, String username,
                                                        PlaySessionListeners listeners) {
        CompletableFuture<PlaySession> result = new CompletableFuture<>();
        return connect(host, port, result, channel -> addPipeline(channel,
                new PlayClientHandler(ProtocolVersion.CURRENT, host, port, username, result, listeners)));
    }

    /**
     * Wires the shared wire-mechanics chain (docs/protocol-spec.md § 2/§ 3; full tier table in
     * docs/protocol-pipeline.md) ahead of whichever control handler owns the actual connection:
     * framing, then an idle-read guard ({@link IdleStateHandler} + {@link
     * ConnectionWatchdogHandler} - registered here for the same reason as {@link
     * FrameStatsHandler} one line below: it needs to see already-delimited frames, not raw stream
     * bytes, so it resets on "the peer said something," not on partial TCP reads), then {@link
     * FrameStatsHandler} (frame-level counts), then compression (initially a passthrough - see
     * {@link CompressionDecoder}), then marshalling (initially {@code HandshakeMarshaller} - see
     * {@link MarshallingDecoder}), then {@link PacketLoggingHandler} (typed-packet-level logging,
     * mirroring {@link FrameStatsHandler}'s frame-level logging one tier down), then
     * {@link SessionStateManager} (owns the FSM, observes everything, sends nothing), then the
     * control handler (sends every reply, holds no state of its own). Registration order here is
     * what makes the outbound direction come out correct too: Netty runs outbound handlers in the
     * reverse of their registration order, so a packet the control handler writes flows
     * packet-logger -&gt; marshaller -&gt; compressor -&gt; frame-stats -&gt; framer -&gt; socket,
     * the mirror of how inbound bytes arrive.
     */
    private static void addPipeline(SocketChannel channel, SimpleChannelInboundHandler<Packet> controlHandler) {
        channel.pipeline()
                .addLast(new FrameDecoder())
                .addLast(new FrameEncoder())
                .addLast(new IdleStateHandler(READER_IDLE_TIMEOUT_SECONDS, 0, 0))
                .addLast(new ConnectionWatchdogHandler())
                .addLast(new FrameStatsHandler())
                .addLast(new CompressionDecoder())
                .addLast(new CompressionEncoder())
                .addLast(new MarshallingDecoder())
                .addLast(new MarshallingEncoder())
                .addLast(new PacketLoggingHandler())
                .addLast(new SessionStateManager())
                .addLast(controlHandler);
    }

    /**
     * Shared connect/teardown: spins up a single-threaded event loop for this one connection and
     * shuts it down once the <em>channel</em> closes - not once {@code result} completes, since
     * {@link #join} deliberately completes its result well before the connection ends (the whole
     * point of a persistent session). {@link #pingStatus} and {@link #observeJoin} both close
     * their own channel in the same call that completes their result, so this is equivalent to the
     * old completes-{@code result}-triggered teardown for them, and additionally correct for
     * {@link #join}. Fine for occasional/long-lived connections alike at today's one-bot scale; a
     * fleet of Soldiers will eventually want a shared event loop instead of one per connection.
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
                        channel.closeFuture().addListener(f -> group.shutdownGracefully(0, 1, TimeUnit.SECONDS));
                    }
                })
                .connect(host, port)
                .addListener(connectFuture -> {
                    if (!connectFuture.isSuccess()) {
                        result.completeExceptionally(connectFuture.cause());
                        group.shutdownGracefully(0, 1, TimeUnit.SECONDS);
                    }
                });

        return result;
    }
}
