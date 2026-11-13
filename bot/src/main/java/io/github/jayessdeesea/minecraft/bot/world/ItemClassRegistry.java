package io.github.jayessdeesea.minecraft.bot.world;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
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
    public enum ItemClass { WEAPON, ARMOUR, ARROWS, LOG, PLANKS, STICK, CRAFTING_TABLE, COBBLESTONE, PICKAXE, FOOD, FURNACE, COAL, TORCH, RAW_IRON, IRON_INGOT,
        SHIELD, SEEDS, WHEAT, HOE, SAPLING, DIRT, STRING, FLINT, FEATHER, DIAMOND, RAW_COPPER, COPPER_INGOT,
        CHEST, LADDER, FENCE, FENCE_GATE, BUCKET, WATER_BUCKET, WOOL, BED, SHOVEL }

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

    /** The melee rank {@link #meleeRank} gives the named weapon - for comparing against a grade, not an item in hand. */
    public static int meleeRank(String weapon) {
        return ITEMS.entrySet().stream().filter(e -> e.getValue().name().equals(weapon)).findFirst()
                .map(e -> meleeRank(e.getKey())).filter(OptionalInt::isPresent).map(OptionalInt::getAsInt)
                .orElseThrow(() -> new IllegalArgumentException(weapon + " isn't a melee weapon"));
    }

    /**
     * What a pickaxe can mine, by its material's mining level: 0 wooden or golden (stone, coal),
     * 1 stone or copper (iron ore), 2 iron (diamond ore), 3 diamond, 4 netherite. Empty for anything
     * but a pickaxe.
     */
    public static OptionalInt miningTier(int itemId) {
        Entry entry = ITEMS.get(itemId);
        if (entry == null || entry.itemClass() != ItemClass.PICKAXE) {
            return OptionalInt.empty();
        }
        return switch (entry.name().replace("_pickaxe", "")) {
            case "wooden", "golden" -> OptionalInt.of(0);
            case "stone", "copper" -> OptionalInt.of(1);
            case "iron" -> OptionalInt.of(2);
            case "diamond" -> OptionalInt.of(3);
            case "netherite" -> OptionalInt.of(4);
            default -> throw new IllegalStateException("pickaxe " + entry.name() + " has no mining tier - extend miningTier");
        };
    }

    /**
     * How fast an axe chops, as its material's mining speed - a bare hand is 1: wooden 2, stone 4,
     * copper 5, iron 6, diamond 8, netherite 9, golden 12. Empty for anything but an axe.
     */
    public static OptionalDouble axeSpeed(int itemId) {
        Entry entry = ITEMS.get(itemId);
        if (entry == null || !entry.name().endsWith("_axe")) {
            return OptionalDouble.empty();
        }
        return switch (entry.name().replace("_axe", "")) {
            case "wooden" -> OptionalDouble.of(2);
            case "stone" -> OptionalDouble.of(4);
            case "copper" -> OptionalDouble.of(5);
            case "iron" -> OptionalDouble.of(6);
            case "diamond" -> OptionalDouble.of(8);
            case "netherite" -> OptionalDouble.of(9);
            case "golden" -> OptionalDouble.of(12);
            default -> throw new IllegalStateException("axe " + entry.name() + " has no speed - extend axeSpeed");
        };
    }

    /**
     * How fast a shovel digs soil and gravel, as its material's mining speed - a bare hand is 1, the
     * same speeds as an axe's (vanilla's tool tiers). Empty for anything but a shovel.
     */
    public static OptionalDouble shovelSpeed(int itemId) {
        Entry entry = ITEMS.get(itemId);
        if (entry == null || entry.itemClass() != ItemClass.SHOVEL) {
            return OptionalDouble.empty();
        }
        return switch (entry.name().replace("_shovel", "")) {
            case "wooden" -> OptionalDouble.of(2);
            case "stone" -> OptionalDouble.of(4);
            case "copper" -> OptionalDouble.of(5);
            case "iron" -> OptionalDouble.of(6);
            case "diamond" -> OptionalDouble.of(8);
            case "netherite" -> OptionalDouble.of(9);
            case "golden" -> OptionalDouble.of(12);
            default -> throw new IllegalStateException("shovel " + entry.name() + " has no speed - extend shovelSpeed");
        };
    }

    /** Armour materials, worst to best by the protection a full set gives; a turtle shell is an iron helmet's equal. */
    private static final List<String> ARMOUR_GRADES = List.of("leather", "copper", "golden", "chainmail", "iron", "diamond", "netherite");

    /** How good a piece of armour is, by its material - {@link #armourGrade(String)} of the material. Empty for anything else. */
    public static OptionalInt armourGrade(int itemId) {
        Entry entry = ITEMS.get(itemId);
        if (entry == null || entry.itemClass() != ItemClass.ARMOUR) {
            return OptionalInt.empty();
        }
        String material = entry.name().substring(0, entry.name().lastIndexOf('_'));
        return OptionalInt.of(armourGrade(material.equals("turtle") ? "iron" : material));
    }

    /** A material's place among armour grades, worst first. */
    public static int armourGrade(String material) {
        int grade = ARMOUR_GRADES.indexOf(material);
        if (grade < 0) {
            throw new IllegalStateException("armour material " + material + " has no grade - extend ARMOUR_GRADES");
        }
        return grade;
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
                        case "U" -> ItemClass.FURNACE;
                        case "O" -> ItemClass.COAL;
                        case "T" -> ItemClass.TORCH;
                        case "M" -> ItemClass.RAW_IRON;
                        case "N" -> ItemClass.IRON_INGOT;
                        case "H" -> ItemClass.SHIELD;
                        case "E" -> ItemClass.SEEDS;
                        case "Y" -> ItemClass.WHEAT;
                        case "Z" -> ItemClass.HOE;
                        case "B" -> ItemClass.SAPLING;
                        case "D" -> ItemClass.DIRT;
                        case "J" -> ItemClass.STRING;
                        case "Q" -> ItemClass.FLINT;
                        case "V" -> ItemClass.FEATHER;
                        case "I" -> ItemClass.DIAMOND;
                        case "G" -> ItemClass.RAW_COPPER;
                        case "c" -> ItemClass.COPPER_INGOT;
                        case "h" -> ItemClass.CHEST;
                        case "l" -> ItemClass.LADDER;
                        case "f" -> ItemClass.FENCE;
                        case "g" -> ItemClass.FENCE_GATE;
                        case "b" -> ItemClass.BUCKET;
                        case "w" -> ItemClass.WATER_BUCKET;
                        case "u" -> ItemClass.WOOL;
                        case "d" -> ItemClass.BED;
                        case "v" -> ItemClass.SHOVEL;
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
