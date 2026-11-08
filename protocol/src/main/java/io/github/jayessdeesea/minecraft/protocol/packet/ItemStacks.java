package io.github.jayessdeesea.minecraft.protocol.packet;

import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads an item stack as {@code ItemStack.OPTIONAL_STREAM_CODEC} writes it (protocol 777): VarInt
 * count; if positive, VarInt item ID ({@code Item.STREAM_CODEC}, a registry holder), then a
 * component patch ({@code DataComponentPatch}'s stream codec): VarInt added count, VarInt removed
 * count, each added component as VarInt type then its value, each removed one as VarInt type.
 *
 * <p>Each component value has its own codec and no length prefix, so a type this reader doesn't
 * know can't be skipped - and nothing after it in the packet can be read either. That's a tracked
 * gap, not a broken connection: the frame is already length-isolated (same reasoning as
 * {@code UnknownPacket}), so the caller keeps what it decoded and drops the rest, and the type is
 * logged loudly so the gap gets closed. Known: the plain-number components a soldier's gear
 * carries, and enchantments (codecs confirmed in {@code DataComponents}).
 */
final class ItemStacks {

    private static final Logger LOG = LogManager.getLogger(ItemStacks.class);

    // minecraft:data_component_type protocol IDs (registries.json) whose stream codec is VAR_INT.
    private static final int MAX_DAMAGE = 2;
    private static final int DAMAGE = 3;
    private static final int REPAIR_COST = 19;
    // ... and Unit (no bytes at all).
    private static final int UNBREAKABLE = 4;
    // ItemEnchantments.STREAM_CODEC: VarInt count, then per entry a VarInt enchantment registry ID
    // (holderRegistry - no inline form) and a VarInt level. Stored enchantments (books) share it.
    private static final int ENCHANTMENTS = 13;
    private static final int STORED_ENCHANTMENTS = 45;
    // ComponentSerialization.STREAM_CODEC: a text component as an unnamed NBT root tag - a name,
    // given or the item's own (a banner's) - and ItemLore's list of them, VarInt-counted to 256.
    private static final int CUSTOM_NAME = 6;
    private static final int ITEM_NAME = 9;
    private static final int LORE = 11;
    private static final int MAX_LORE_LINES = 256;
    /** More than any item carries: there are well under 64 enchantments. */
    private static final int MAX_ENCHANTMENTS = 64;

    /** More than there are component types (111) - a patch can name each at most once. */
    private static final int MAX_COMPONENTS = 256;

    /** Thrown when a stack carries a component this reader can't decode; holds what it could read. */
    static final class Undecodable extends RuntimeException {
        final ItemSlot partial;

        Undecodable(ItemSlot partial, int componentType) {
            super("item " + partial.itemId() + " carries undecoded component type " + componentType, null, false, false);
            this.partial = partial;
        }
    }

    private ItemStacks() {
    }

    static Optional<ItemSlot> read(ByteBuf in) {
        int count = ProtocolCodec.readVarInt(in);
        if (count <= 0) {
            return Optional.empty();
        }
        int itemId = ProtocolCodec.readVarInt(in);
        int added = ProtocolCodec.readVarIntCount(in, MAX_COMPONENTS);
        int removed = ProtocolCodec.readVarIntCount(in, MAX_COMPONENTS);
        int damage = -1;
        Map<Integer, Integer> enchantments = Map.of();
        for (int i = 0; i < added; i++) {
            int type = ProtocolCodec.readVarInt(in);
            switch (type) {
                case DAMAGE -> damage = ProtocolCodec.readVarInt(in);
                case MAX_DAMAGE, REPAIR_COST -> ProtocolCodec.readVarInt(in);
                case UNBREAKABLE -> {
                }
                case ENCHANTMENTS -> enchantments = readEnchantments(in);
                case STORED_ENCHANTMENTS -> readEnchantments(in); // a book's: not on the soldier
                case CUSTOM_NAME, ITEM_NAME -> Nbt.skipUnnamedRoot(in);
                case LORE -> {
                    int lines = ProtocolCodec.readVarIntCount(in, MAX_LORE_LINES);
                    for (int line = 0; line < lines; line++) {
                        Nbt.skipUnnamedRoot(in);
                    }
                }
                default -> {
                    LOG.warn("Item {} carries data component type {} - not decoded; rest of this packet dropped", itemId, type);
                    throw new Undecodable(new ItemSlot(itemId, count, damage, true), type);
                }
            }
        }
        for (int i = 0; i < removed; i++) {
            ProtocolCodec.readVarInt(in);
        }
        return Optional.of(new ItemSlot(itemId, count, damage, false, enchantments));
    }

    private static Map<Integer, Integer> readEnchantments(ByteBuf in) {
        int count = ProtocolCodec.readVarIntCount(in, MAX_ENCHANTMENTS);
        Map<Integer, Integer> enchantments = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            enchantments.put(ProtocolCodec.readVarInt(in), ProtocolCodec.readVarInt(in));
        }
        return enchantments;
    }
}
