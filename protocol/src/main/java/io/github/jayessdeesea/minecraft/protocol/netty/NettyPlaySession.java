package io.github.jayessdeesea.minecraft.protocol.netty;

import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.packet.UseItemPacket;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlayState;
import io.github.jayessdeesea.minecraft.protocol.packet.AttackPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ChatCommandPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ChatPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ClientCommandPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ClientTickEndPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.ContainerPackets;
import io.github.jayessdeesea.minecraft.protocol.packet.PunchPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.MovePlayerPosRotPacket;
import io.github.jayessdeesea.minecraft.protocol.packet.PlayerActionPacket;
import io.netty.channel.Channel;

/**
 * The only {@link PlaySession} implementation - package-private because holding a {@link Channel}
 * is exactly the Netty detail {@link PlaySession}'s callers (outside {@code protocol}) must not
 * see or depend on.
 */
final class NettyPlaySession implements PlaySession {

    private final Channel channel;
    private final PlayState playState;
    /** Block-interaction sequence; the server acknowledges each. */
    private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();

    NettyPlaySession(Channel channel, PlayState playState) {
        this.channel = channel;
        this.playState = playState;
    }

    @Override
    public PlayState playState() {
        return playState;
    }

    @Override
    public void reportPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        channel.writeAndFlush(new MovePlayerPosRotPacket(x, y, z, yaw, pitch, onGround));
    }

    @Override
    public void endTick() {
        channel.writeAndFlush(new ClientTickEndPacket());
    }

    @Override
    public void respawn() {
        channel.writeAndFlush(new ClientCommandPacket(ClientCommandPacket.PERFORM_RESPAWN));
    }

    @Override
    public void attack(int entityId) {
        channel.write(new AttackPacket(entityId));
        channel.writeAndFlush(new PunchPacket());
    }

    @Override
    public void sendChatCommand(String command) {
        channel.writeAndFlush(new ChatCommandPacket(command));
    }

    @Override
    public void sendChat(String message) {
        channel.writeAndFlush(new ChatPacket(message, System.currentTimeMillis(), 0));
    }

    @Override
    public void useBlock(int x, int y, int z) {
        channel.writeAndFlush(new ContainerPackets.UseItemOn(x, y, z, BlockFace.UP, sequence.incrementAndGet()));
    }

    @Override
    public void useItem(float yaw, float pitch) {
        channel.writeAndFlush(new UseItemPacket(sequence.incrementAndGet(), yaw, pitch));
    }

    @Override
    public void useBlockFace(int x, int y, int z, BlockFace face) {
        channel.writeAndFlush(new ContainerPackets.UseItemOn(x, y, z, face, sequence.incrementAndGet()));
    }

    @Override
    public void quickMove(int containerId, int stateId, int slot) {
        channel.writeAndFlush(new ContainerPackets.ContainerClick(containerId, stateId, slot, 0, ContainerPackets.ContainerClick.QUICK_MOVE));
    }

    @Override
    public void click(int containerId, int stateId, int slot, boolean rightButton) {
        channel.writeAndFlush(new ContainerPackets.ContainerClick(containerId, stateId, slot, rightButton ? 1 : 0, ContainerPackets.ContainerClick.PICKUP));
    }

    @Override
    public void swapWithHotbar(int containerId, int stateId, int slot, int hotbarSlot) {
        channel.writeAndFlush(new ContainerPackets.ContainerClick(containerId, stateId, slot, hotbarSlot, ContainerPackets.ContainerClick.SWAP));
    }

    @Override
    public void throwOne(int containerId, int stateId, int slot) {
        channel.writeAndFlush(new ContainerPackets.ContainerClick(containerId, stateId, slot, 0, ContainerPackets.ContainerClick.THROW));
    }

    @Override
    public void startDigging(int x, int y, int z) {
        channel.writeAndFlush(new PlayerActionPacket(PlayerActionPacket.START_DESTROY_BLOCK, x, y, z, sequence.incrementAndGet()));
    }

    @Override
    public void finishDigging(int x, int y, int z) {
        channel.writeAndFlush(new PlayerActionPacket(PlayerActionPacket.STOP_DESTROY_BLOCK, x, y, z, sequence.incrementAndGet()));
    }

    @Override
    public void closeContainer(int containerId) {
        channel.writeAndFlush(new ContainerPackets.CloseContainer(containerId));
    }

    @Override
    public void selectHotbarSlot(int hotbarSlot) {
        channel.writeAndFlush(new ContainerPackets.SetCarriedItem(hotbarSlot));
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
