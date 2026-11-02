package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.InventoryUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The three clientbound Play packets that keep this player's inventory current (protocol 776), IDs
 * from {@code GameProtocols}' registration order, layouts from their stream codecs:
 * <ul>
 *   <li>Container Set Content, 0x12: VarInt container ID, VarInt state ID, VarInt-counted list of
 *       item stacks, then the cursor's stack;</li>
 *   <li>Container Set Slot, 0x14: VarInt container ID, VarInt state ID, short slot, item stack;</li>
 *   <li>Set Held Slot, 0x69: VarInt hotbar slot.</li>
 * </ul>
 * Decoded straight into the {@link InventoryUpdate} a caller sees; the control handler reports
 * container 0, the player's own, as an inventory change and any other (a chest the player opened)
 * as a {@code ContainerUpdate}.
 */
public record InventoryPackets(int packetId, int containerId, int stateId, InventoryUpdate update) implements Packet {

    public static final int SET_CONTENT_PACKET_ID = 0x12;
    public static final int SET_SLOT_PACKET_ID = 0x14;
    public static final int SET_HELD_SLOT_PACKET_ID = 0x69;

    /** More than any container has (a double chest plus the player inventory is 90). */
    static final int MAX_SLOTS = 256;

    public static InventoryPackets decodeSetContent(ByteBuf in) {
        int containerId = ProtocolCodec.readVarInt(in);
        int stateId = ProtocolCodec.readVarInt(in);
        int count = ProtocolCodec.readVarIntCount(in, MAX_SLOTS);
        List<Optional<ItemSlot>> slots = new ArrayList<>(count);
        boolean complete = true;
        for (int i = 0; i < count; i++) {
            try {
                slots.add(ItemStacks.read(in));
            } catch (ItemStacks.Undecodable e) {
                slots.add(Optional.of(e.partial));
                complete = false;
                break;
            }
        }
        // The cursor's stack follows; a soldier never holds one, so it isn't read.
        return new InventoryPackets(SET_CONTENT_PACKET_ID, containerId, stateId, new InventoryUpdate.Contents(List.copyOf(slots), complete));
    }

    public static InventoryPackets decodeSetSlot(ByteBuf in) {
        int containerId = ProtocolCodec.readVarInt(in);
        int stateId = ProtocolCodec.readVarInt(in);
        int slot = in.readShort();
        Optional<ItemSlot> item;
        try {
            item = ItemStacks.read(in);
        } catch (ItemStacks.Undecodable e) {
            item = Optional.of(e.partial);
        }
        return new InventoryPackets(SET_SLOT_PACKET_ID, containerId, stateId, new InventoryUpdate.Slot(slot, item));
    }

    public static InventoryPackets decodeSetHeldSlot(ByteBuf in) {
        return new InventoryPackets(SET_HELD_SLOT_PACKET_ID, 0, 0, new InventoryUpdate.HeldSlot(ProtocolCodec.readVarInt(in)));
    }

    @Override
    public void encode(ByteBuf out) {
        throw new UnsupportedOperationException("inventory packets are clientbound-only and never sent by this client");
    }
}
