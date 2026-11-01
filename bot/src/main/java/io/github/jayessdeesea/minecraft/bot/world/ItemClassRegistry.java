package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Which items a soldier's LACE report counts - weapons, armour, arrows - backed by a resource
 * generated from the pinned server jar's own {@code --reports} output
 * ({@code tools/item-class-generator}), same approach as {@link HostileMobRegistry}. Everything
 * else is just carried; an item missing from the list goes uncounted, a tracked gap (grow the
 * generator's list), not a violated invariant.
 *
 * <p>Pinned to protocol 776 by filename - regenerate and rename deliberately when the pin bumps.
 */
public final class ItemClassRegistry {

    /** What an item counts as. */
    public enum ItemClass { WEAPON, ARMOUR, ARROWS }

    private static final String RESOURCE_NAME = "item-classes-776.txt";

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
