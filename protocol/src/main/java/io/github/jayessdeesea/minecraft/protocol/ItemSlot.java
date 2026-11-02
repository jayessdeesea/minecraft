package io.github.jayessdeesea.minecraft.protocol;

import java.util.Map;

/**
 * One non-empty inventory slot as the server describes it: which item (a {@code minecraft:item}
 * protocol ID), how many, and how worn ({@code damage}, -1 if undamaged or not damageable).
 * {@code partial} marks a stack carrying a component this client doesn't decode - the item and
 * count are right, the rest of its data wasn't read (docs/protocol-spec.md § 3.5, item stacks).
 *
 * <p>{@code enchantments} maps each enchantment's ID in this session's {@code minecraft:enchantment}
 * registry (synced during configuration - see {@code PlaySessionListeners.onRegistry}) to its level.
 */
public record ItemSlot(int itemId, int count, int damage, boolean partial, Map<Integer, Integer> enchantments) {

    public ItemSlot {
        enchantments = Map.copyOf(enchantments);
    }

    /** A stack with no enchantments. */
    public ItemSlot(int itemId, int count, int damage, boolean partial) {
        this(itemId, count, damage, partial, Map.of());
    }
}
