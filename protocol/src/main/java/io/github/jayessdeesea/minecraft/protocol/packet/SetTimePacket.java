package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;
import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Clientbound Play-state packet, officially "Set Time" (protocol 777), 0x73 -
 * {@code ClientboundSetTimePacket}: long game time, then a map (VarInt count) of world clocks, each
 * a VarInt index into the synced {@code minecraft:world_clock} registry followed by its
 * {@code ClockNetworkState} - VarLong total ticks, float partial tick, float rate. The overworld's
 * time of day is its clock's total ticks; a rate of 0 means the clock is stopped.
 */
public record SetTimePacket(long gameTime, Map<Integer, TimeUpdate.Clock> clocks) implements Packet {

    public static final int PACKET_ID = 0x73;

    public static SetTimePacket decode(ByteBuf in) {
        long gameTime = in.readLong();
        int count = ProtocolCodec.readVarInt(in);
        Map<Integer, TimeUpdate.Clock> clocks = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int clock = ProtocolCodec.readVarInt(in);
            long totalTicks = ProtocolCodec.readVarLong(in);
            in.readFloat(); // partial tick: sub-tick smoothing for rendering
            float rate = in.readFloat();
            clocks.put(clock, new TimeUpdate.Clock(totalTicks, rate));
        }
        return new SetTimePacket(gameTime, Map.copyOf(clocks));
    }

    @Override
    public int packetId() {
        return PACKET_ID;
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("SetTimePacket is clientbound-only and never sent by this client");
    }
}
