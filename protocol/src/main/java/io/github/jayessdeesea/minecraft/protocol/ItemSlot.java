package io.github.jayessdeesea.minecraft.protocol;

/**
 * One non-empty inventory slot as the server describes it: which item (a {@code minecraft:item}
 * protocol ID), how many, and how worn ({@code damage}, -1 if undamaged or not damageable).
 * {@code partial} marks a stack carrying a component this client doesn't decode - the item and
 * count are right, the rest of its data wasn't read (docs/protocol-spec.md § 3.5, item stacks).
 */
public record ItemSlot(int itemId, int count, int damage, boolean partial) {
}
