package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Kit;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * One soldier's own inventory window, kept current from the server's updates, reduced to the
 * {@link Kit} its LACE report carries. Slots are numbered the vanilla way (5-8 armour, 36-44
 * hotbar). Synchronized: its connection's thread applies the server's updates while the soldier's
 * tick thread reads it to resupply ({@code bot.supply}). {@link #kit} is an immutable value safe to
 * hand to any other thread.
 *
 * <p>Takes plain item IDs rather than {@code protocol} types so {@code bot} keeps no dependency on
 * how the wire describes a stack; the composition root translates.
 */
public final class Inventory {

    public static final int SLOTS = 46;
    public static final int ARMOUR_FIRST = 5;
    public static final int ARMOUR_LAST = 8;
    public static final int MAIN_FIRST = 9;
    public static final int HOTBAR_FIRST = 36;
    public static final int HOTBAR_LAST = 44;
    public static final int OFF_HAND = 45;

    private final int[] itemIds = new int[SLOTS];
    private final int[] counts = new int[SLOTS];
    @SuppressWarnings("unchecked")
    private final Map<String, Integer>[] enchantments = new Map[SLOTS];
    private int heldHotbarSlot;

    public Inventory() {
        Arrays.fill(itemIds, -1);
        Arrays.fill(enchantments, Map.of());
    }

    /**
     * One slot with its enchantments by name ("feather_falling" -> 4); {@code item} is {itemId,
     * count}, or null for empty.
     */
    public synchronized Kit setSlot(int slot, int[] item, Map<String, Integer> enchanted) {
        setSlot(slot, item);
        if (slot >= 0 && slot < SLOTS) {
            enchantments[slot] = item == null ? Map.of() : Map.copyOf(enchanted);
        }
        return kit();
    }

    /** Every slot at once; slots past the end of {@code items} (an incomplete update) are left as they were. */
    public synchronized Kit replaceAll(List<int[]> items) {
        for (int slot = 0; slot < Math.min(items.size(), SLOTS); slot++) {
            set(slot, items.get(slot));
        }
        return kit();
    }

    /** One slot: {@code item} is {itemId, count}, or null for empty. */
    public synchronized Kit setSlot(int slot, int[] item) {
        if (slot >= 0 && slot < SLOTS) {
            set(slot, item);
        }
        return kit();
    }

    /**
     * A slot of an open container's window that shows this inventory: after the container's own
     * {@code containerSize} slots come the main inventory (9-35 here) and then the hotbar (36-44).
     * The server doesn't repeat these changes for this window when the container closes, so they
     * must be applied as they arrive. The container's own slots are ignored.
     */
    public synchronized Kit setWindowSlot(int windowSlot, int containerSize, int[] item) {
        if (windowSlot >= containerSize) {
            setSlot(MAIN_FIRST + windowSlot - containerSize, item);
        }
        return kit();
    }

    /** The first main-inventory or hotbar slot (9-44) holding an item of {@code itemClass}, or -1. */
    public synchronized int slotOf(ItemClassRegistry.ItemClass itemClass) {
        for (int slot = MAIN_FIRST; slot <= HOTBAR_LAST; slot++) {
            if (ItemClassRegistry.classOf(itemIds[slot]).equals(Optional.of(itemClass))) {
                return slot;
            }
        }
        return -1;
    }

    /** How many melee weapons - swords, axes, spears - the main inventory and hotbar hold. */
    public synchronized int meleeWeapons() {
        int total = 0;
        for (int slot = MAIN_FIRST; slot <= HOTBAR_LAST; slot++) {
            if (ItemClassRegistry.meleeRank(itemIds[slot]).isPresent()) {
                total++;
            }
        }
        return total;
    }

    /**
     * The slot of a melee weapon beyond the best one carried - the worst of the rest, the one to give
     * away - or -1 with one or none.
     */
    public synchronized int spareWeaponSlot() {
        int best = -1;
        int worst = -1;
        for (int slot = MAIN_FIRST; slot <= HOTBAR_LAST; slot++) {
            OptionalInt rank = ItemClassRegistry.meleeRank(itemIds[slot]);
            if (rank.isEmpty()) {
                continue;
            }
            if (best < 0 || rank.getAsInt() > ItemClassRegistry.meleeRank(itemIds[best]).getAsInt()) {
                best = slot;
            }
        }
        for (int slot = MAIN_FIRST; slot <= HOTBAR_LAST; slot++) {
            OptionalInt rank = ItemClassRegistry.meleeRank(itemIds[slot]);
            if (rank.isPresent() && slot != best
                    && (worst < 0 || rank.getAsInt() < ItemClassRegistry.meleeRank(itemIds[worst]).getAsInt())) {
                worst = slot;
            }
        }
        return worst;
    }

    /** How many items of {@code itemClass} the main inventory and hotbar hold. */
    public synchronized int count(ItemClassRegistry.ItemClass itemClass) {
        int total = 0;
        for (int slot = MAIN_FIRST; slot <= HOTBAR_LAST; slot++) {
            if (ItemClassRegistry.classOf(itemIds[slot]).equals(Optional.of(itemClass))) {
                total += counts[slot];
            }
        }
        return total;
    }

    /** What's carried, by counted class - "LOG x2, PLANKS x4" - for logs explaining a failure. */
    public synchronized String summary() {
        Map<ItemClassRegistry.ItemClass, Integer> totals = new EnumMap<>(ItemClassRegistry.ItemClass.class);
        for (int slot = 0; slot < SLOTS; slot++) {
            int s = slot;
            ItemClassRegistry.classOf(itemIds[slot]).ifPresent(c -> totals.merge(c, counts[s], Integer::sum));
        }
        return totals.isEmpty() ? "nothing counted" : totals.entrySet().stream()
                .map(e -> e.getKey() + " x" + e.getValue()).collect(Collectors.joining(", "));
    }

    /** The item ID in {@code slot}, or -1 if it's empty. */
    public synchronized int itemAt(int slot) {
        return itemIds[slot];
    }

    /** How many items {@code slot} holds, 0 if it's empty. */
    public synchronized int countAt(int slot) {
        return counts[slot];
    }

    public synchronized int heldHotbarSlot() {
        return heldHotbarSlot;
    }

    public synchronized Kit selectHotbar(int hotbarSlot) {
        heldHotbarSlot = hotbarSlot;
        return kit();
    }

    private void set(int slot, int[] item) {
        itemIds[slot] = item == null ? -1 : item[0];
        counts[slot] = item == null ? 0 : item[1];
        enchantments[slot] = Map.of();
    }

    /** The first slot holding the item named {@code name} ("bow"), or -1. */
    public synchronized int slotOfNamed(String name) {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (ItemClassRegistry.nameOf(itemIds[slot]).filter(name::equals).isPresent()) {
                return slot;
            }
        }
        return -1;
    }

    public synchronized Kit kit() {
        // Armed means a weapon at hand: the one held, or else one on the hotbar - a soldier that put
        // its sword away to chop wood draws it in a moment. One in the main inventory doesn't count.
        int held = itemIds[HOTBAR_FIRST + heldHotbarSlot];
        Optional<String> weapon = ItemClassRegistry.classOf(held).filter(c -> c == ItemClassRegistry.ItemClass.WEAPON)
                .flatMap(c -> ItemClassRegistry.nameOf(held));
        for (int slot = HOTBAR_FIRST; weapon.isEmpty() && slot <= HOTBAR_LAST; slot++) {
            int item = itemIds[slot];
            weapon = ItemClassRegistry.classOf(item).filter(c -> c == ItemClassRegistry.ItemClass.WEAPON)
                    .flatMap(c -> ItemClassRegistry.nameOf(item));
        }
        int arrows = 0;
        int armour = 0;
        int fallProtection = 0;
        int logs = 0;
        int stone = 0;
        int dirt = 0;
        int rations = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            Optional<ItemClassRegistry.ItemClass> itemClass = ItemClassRegistry.classOf(itemIds[slot]);
            if (itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.ARROWS))) {
                arrows += counts[slot];
            } else if (itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.LOG))) {
                logs += counts[slot];
            } else if (itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.COBBLESTONE))) {
                stone += counts[slot];
            } else if (itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.DIRT))) {
                dirt += counts[slot];
            } else if (itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.FOOD))) {
                rations += counts[slot];
            } else if (slot >= ARMOUR_FIRST && slot <= ARMOUR_LAST && itemClass.equals(Optional.of(ItemClassRegistry.ItemClass.ARMOUR))) {
                armour++;
                fallProtection += 3 * enchantments[slot].getOrDefault("minecraft:feather_falling", 0)
                        + enchantments[slot].getOrDefault("minecraft:protection", 0);
            }
        }
        boolean shield = ItemClassRegistry.classOf(itemIds[OFF_HAND]).equals(Optional.of(ItemClassRegistry.ItemClass.SHIELD));
        return new Kit(weapon, arrows, armour, Math.min(fallProtection, Kit.MAX_PROTECTION), logs, stone, rations, shield,
                slotOf(ItemClassRegistry.ItemClass.PICKAXE) >= 0, dirt, slotOfNamed("bow") >= 0, Math.max(0, meleeWeapons() - 1));
    }
}
