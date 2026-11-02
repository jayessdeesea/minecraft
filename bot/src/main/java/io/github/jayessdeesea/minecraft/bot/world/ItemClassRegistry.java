package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Which items a soldier's LACE report counts - weapons, armour, arrows - backed by a resource
 * generated from the pinned server jar's own {@code --reports} output
 * ({@code tools/item-class-generator}), same approach as {@link HostileMobRegistry}. Everything
 * else is just carried; an item missing from the list goes uncounted, a tracked gap (grow the
 * generator's list), not a violated invariant.
 *
 * <p>Pinned to protocol 777 by filename - regenerate and rename deliberately when the pin bumps.
 */
public final class ItemClassRegistry {

    /** What an item counts as. */
    public enum ItemClass { WEAPON, ARMOUR, ARROWS, LOG, PLANKS, STICK, CRAFTING_TABLE, COBBLESTONE, PICKAXE, FOOD }

    private static final String RESOURCE_NAME = "item-classes-777.txt";

    private record Entry(ItemClass itemClass, String name) {
    }

    private static final Map<Integer, Entry> ITEMS = load();

    private ItemClassRegistry() {
    }

    public static Optional<ItemClass> classOf(int itemId) {
        return Optional.ofNullable(ITEMS.get(itemId)).map(Entry::itemClass);
    }

    /** The item's registry name ("iron_sword"), for counted items. */
    public static Optional<String> nameOf(int itemId) {
        return Optional.ofNullable(ITEMS.get(itemId)).map(Entry::name);
    }

    /** The inventory armour slot an armour item goes in - 5 head, 6 chest, 7 legs, 8 feet - by its name. */
    public static OptionalInt armourSlot(int itemId) {
        Entry entry = ITEMS.get(itemId);
        if (entry == null || entry.itemClass() != ItemClass.ARMOUR) {
            return OptionalInt.empty();
        }
        String name = entry.name();
        if (name.endsWith("_helmet")) {
            return OptionalInt.of(5);
        } else if (name.endsWith("_chestplate")) {
            return OptionalInt.of(6);
        } else if (name.endsWith("_leggings")) {
            return OptionalInt.of(7);
        } else if (name.endsWith("_boots")) {
            return OptionalInt.of(8);
        }
        throw new IllegalStateException("armour item " + name + " fits no armour slot - extend armourSlot");
    }

    private static final List<String> MELEE_MATERIALS =
            List.of("wooden", "golden", "stone", "copper", "iron", "diamond", "netherite");

    /**
     * How good a weapon is for the soldier's arm's-length fight: swords beat axes beat spears,
     * then by material. Empty for anything a soldier can't melee with - bows, crossbows, the
     * trident, the mace - and for non-weapons.
     */
    public static OptionalInt meleeRank(int itemId) {
        Entry entry = ITEMS.get(itemId);
        if (entry == null || entry.itemClass() != ItemClass.WEAPON) {
            return OptionalInt.empty();
        }
        String[] parts = entry.name().split("_");
        if (parts.length != 2 || !MELEE_MATERIALS.contains(parts[0])) {
            return OptionalInt.empty();
        }
        int kind = switch (parts[1]) {
            case "sword" -> 3;
            case "axe" -> 2;
            case "spear" -> 1;
            default -> 0;
        };
        return kind == 0 ? OptionalInt.empty() : OptionalInt.of(kind * 10 + MELEE_MATERIALS.indexOf(parts[0]));
    }

    private static Map<Integer, Entry> load() {
        Map<Integer, Entry> items = new HashMap<>();
        try (InputStream in = ItemClassRegistry.class.getResourceAsStream(RESOURCE_NAME)) {
            if (in == null) {
                throw new IllegalStateException("Missing item class resource: " + RESOURCE_NAME);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split(" ");
                    ItemClass itemClass = switch (parts[1]) {
                        case "W" -> ItemClass.WEAPON;
                        case "A" -> ItemClass.ARMOUR;
                        case "R" -> ItemClass.ARROWS;
                        case "L" -> ItemClass.LOG;
                        case "P" -> ItemClass.PLANKS;
                        case "S" -> ItemClass.STICK;
                        case "C" -> ItemClass.CRAFTING_TABLE;
                        case "K" -> ItemClass.COBBLESTONE;
                        case "X" -> ItemClass.PICKAXE;
                        case "F" -> ItemClass.FOOD;
                        default -> throw new IllegalStateException("Unknown item class in " + RESOURCE_NAME + ": " + line);
                    };
                    items.put(Integer.parseInt(parts[0]), new Entry(itemClass, parts[2]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load item class resource: " + RESOURCE_NAME, e);
        }
        return Map.copyOf(items);
    }
}
