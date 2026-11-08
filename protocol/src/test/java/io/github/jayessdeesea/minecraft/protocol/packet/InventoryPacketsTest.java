package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.InventoryUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryPacketsTest {

    private static final int IRON_SWORD = 1045;
    private static final int ARROW = 1009;
    private static final int ENCHANTMENTS = 13;
    private static final int CUSTOM_DATA = 0; // an NBT compound - not decoded

    private static void stack(ByteBuf out, int count, int itemId, int... components) {
        ProtocolCodec.writeVarInt(out, count);
        if (count > 0) {
            ProtocolCodec.writeVarInt(out, itemId);
            ProtocolCodec.writeVarInt(out, components.length / 2);
            ProtocolCodec.writeVarInt(out, 0);
            for (int i = 0; i < components.length; i += 2) {
                ProtocolCodec.writeVarInt(out, components[i]);
                ProtocolCodec.writeVarInt(out, components[i + 1]);
            }
        }
    }

    @Test
    void setSlotDecodesAWornSword() {
        ByteBuf in = Unpooled.buffer();
        ProtocolCodec.writeVarInt(in, 0);  // container
        ProtocolCodec.writeVarInt(in, 7);  // state
        in.writeShort(36);                 // first hotbar slot
        stack(in, 1, IRON_SWORD, 3, 40);   // damage 40

        InventoryPackets packet = InventoryPackets.decodeSetSlot(in);

        assertEquals(new InventoryUpdate.Slot(36, Optional.of(new ItemSlot(IRON_SWORD, 1, 40, false))), packet.update());
        assertEquals(0, in.readableBytes());
    }

    @Test
    void setContentStopsAtAComponentItCannotDecodeAndSaysSo() {
        ByteBuf in = Unpooled.buffer();
        ProtocolCodec.writeVarInt(in, 0);
        ProtocolCodec.writeVarInt(in, 1);
        ProtocolCodec.writeVarInt(in, 3);  // three slots
        stack(in, 0, 0);                   // empty
        stack(in, 16, ARROW);              // 16 arrows
        ProtocolCodec.writeVarInt(in, 1);  // a sword with custom data: count 1,
        ProtocolCodec.writeVarInt(in, IRON_SWORD);
        ProtocolCodec.writeVarInt(in, 1);  // one added component,
        ProtocolCodec.writeVarInt(in, 0);
        ProtocolCodec.writeVarInt(in, CUSTOM_DATA); // of a type we don't decode
        in.writeBytes(new byte[]{1, 2, 3});

        InventoryUpdate.Contents contents = (InventoryUpdate.Contents) InventoryPackets.decodeSetContent(in).update();

        assertEquals(List.of(Optional.empty(), Optional.of(new ItemSlot(ARROW, 16, -1, false)),
                Optional.of(new ItemSlot(IRON_SWORD, 1, -1, true))), contents.slots());
        assertEquals(false, contents.complete());
    }

    @Test
    void decodesEnchantmentsAsRegistryIdsAndLevels() {
        ByteBuf in = Unpooled.buffer();
        ProtocolCodec.writeVarInt(in, 0);
        ProtocolCodec.writeVarInt(in, 3);
        in.writeShort(8);                  // boots slot
        ProtocolCodec.writeVarInt(in, 1);
        ProtocolCodec.writeVarInt(in, IRON_SWORD);
        ProtocolCodec.writeVarInt(in, 1);  // one added component:
        ProtocolCodec.writeVarInt(in, 0);
        ProtocolCodec.writeVarInt(in, ENCHANTMENTS);
        ProtocolCodec.writeVarInt(in, 2);  // two enchantments
        ProtocolCodec.writeVarInt(in, 9);  // registry ID 9 at level 4
        ProtocolCodec.writeVarInt(in, 4);
        ProtocolCodec.writeVarInt(in, 21);
        ProtocolCodec.writeVarInt(in, 1);

        InventoryUpdate.Slot slot = (InventoryUpdate.Slot) InventoryPackets.decodeSetSlot(in).update();

        assertEquals(java.util.Map.of(9, 4, 21, 1), slot.item().orElseThrow().enchantments());
        assertEquals(0, in.readableBytes());
    }

    /** Live: a banner a soldier picked up carries its item name, and the rest of its inventory was dropped. */
    @Test
    void aNamedItemsTextComponentsAreReadPast() {
        ByteBuf in = Unpooled.buffer();
        ProtocolCodec.writeVarInt(in, 0);
        ProtocolCodec.writeVarInt(in, 1);
        ProtocolCodec.writeVarInt(in, 2);  // two slots: a named banner, then arrows
        ProtocolCodec.writeVarInt(in, 1);
        ProtocolCodec.writeVarInt(in, 1417); // white_banner
        ProtocolCodec.writeVarInt(in, 2);  // item name and a line of lore
        ProtocolCodec.writeVarInt(in, 0);
        ProtocolCodec.writeVarInt(in, 9);  // item_name: a string tag
        in.writeByte(8);
        in.writeShort(6);
        in.writeBytes("Banner".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        ProtocolCodec.writeVarInt(in, 11); // lore: one line, an empty compound
        ProtocolCodec.writeVarInt(in, 1);
        in.writeByte(10);
        in.writeByte(0);
        stack(in, 16, ARROW);

        InventoryUpdate.Contents contents = (InventoryUpdate.Contents) InventoryPackets.decodeSetContent(in).update();

        assertEquals(true, contents.complete(), contents.toString());
        assertEquals(Optional.of(new ItemSlot(ARROW, 16, -1, false)), contents.slots().get(1));
    }

    @Test
    void setHeldSlot() {
        ByteBuf in = Unpooled.buffer();
        ProtocolCodec.writeVarInt(in, 4);

        assertEquals(new InventoryUpdate.HeldSlot(4), InventoryPackets.decodeSetHeldSlot(in).update());
    }
}
