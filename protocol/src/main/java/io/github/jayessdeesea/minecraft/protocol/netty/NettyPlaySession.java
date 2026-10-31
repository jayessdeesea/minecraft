package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlayState;
import io.github.jayessdeesea.minecraft.protocol.packet.AttackPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ClientCommandPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.SwingPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.MovePlayerPosPacket;
import io.netty.channel.Channel;

/**
 * The only {@link PlaySession} implementation - package-private because holding a {@link Channel}
 * is exactly the Netty detail {@link PlaySession}'s callers (outside {@code protocol}) must not
 * see or depend on.
 */
final class NettyPlaySession implements PlaySession {

    private final Channel channel;
    private final PlayState playState;

    NettyPlaySession(Channel channel, PlayState playState) {
        this.channel = channel;
        this.playState = playState;
    }

    @Override
    public PlayState playState() {
        return playState;
    }

    @Override
    public void reportPosition(double x, double y, double z, boolean onGround) {
        channel.writeAndFlush(new MovePlayerPosPacket(x, y, z, onGround));
    }

    @Override
    public void respawn() {
        channel.writeAndFlush(new ClientCommandPacket(ClientCommandPacket.PERFORM_RESPAWN));
    }

    @Override
    public void attack(int entityId) {
        channel.write(new AttackPacket(entityId));
        channel.writeAndFlush(new SwingPacket());
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() {
        // Marked before closing so PlayClientHandler.channelInactive can tell this deliberate
        // close apart from an unexpected one (watchdog timeout, exception, server-initiated close)
        // and skip firing PlaySessionListeners.onDisconnect() for it - see that field's javadoc.
        channel.attr(PlayClientHandler.INTENTIONAL_CLOSE).set(true);
        channel.close();
    }
}
