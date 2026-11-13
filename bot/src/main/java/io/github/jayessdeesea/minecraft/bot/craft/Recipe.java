package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a soldier can make, and from what: a shaped pattern of item classes, the station it's made
 * at, and how many one craft yields. Vanilla 26.3's recipes for the items a squad uses (the
 * server's {@code data/minecraft/recipe/*.json}); a class stands for any item of it, which the
 * recipes allow - any planks for a table, coal or charcoal for a torch.
 *
 * <p>What's made is counted by its class, except where a class holds grades of one thing - a
 * stone sword and a wooden one are both {@link ItemClass#WEAPON} - where it names its {@code item},
 * and only that item counts as having it. Such an item is only ever made for itself, never as an
 * ingredient of something else.
 *
 * @param item   the exact item made, when its class holds better and worse ones
 * @param rows   the pattern, row by row, one character a cell; a space is an empty cell. A
 *               furnace recipe is its one ingredient.
 * @param key    what each pattern character stands for
 */
public record Recipe(String name, ItemClass makes, Optional<String> item, int yield, Station station, List<String> rows,
                     Map<Character, ItemClass> key) {

    /** Where a recipe is made. */
    public enum Station {
        /** The inventory's own 2x2 grid - no block needed. */
        GRID,
        /** A crafting table's 3x3 grid. */
        TABLE,
        /** A furnace: one ingredient in, one result out, a plank burnt for every 1.5. */
        FURNACE
    }

    public static final Recipe PLANKS = material("planks", ItemClass.PLANKS, 4, Station.GRID, List.of("L"), Map.of('L', ItemClass.LOG));
    public static final Recipe STICKS = material("sticks", ItemClass.STICK, 4, Station.GRID, List.of("P", "P"), Map.of('P', ItemClass.PLANKS));
    public static final Recipe CRAFTING_TABLE = material("crafting table", ItemClass.CRAFTING_TABLE, 1, Station.GRID,
            List.of("PP", "PP"), Map.of('P', ItemClass.PLANKS));
    public static final Recipe TORCHES = material("torches", ItemClass.TORCH, 4, Station.GRID, List.of("C", "S"),
            Map.of('C', ItemClass.COAL, 'S', ItemClass.STICK));
    public static final Recipe FURNACE = material("furnace", ItemClass.FURNACE, 1, Station.TABLE, List.of("CCC", "C C", "CCC"),
            Map.of('C', ItemClass.COBBLESTONE));
    public static final Recipe CHARCOAL = material("charcoal", ItemClass.COAL, 1, Station.FURNACE, List.of("L"), Map.of('L', ItemClass.LOG));
    public static final Recipe IRON_INGOT = material("iron ingot", ItemClass.IRON_INGOT, 1, Station.FURNACE, List.of("M"),
            Map.of('M', ItemClass.RAW_IRON));
    public static final Recipe COPPER_INGOT = material("copper ingot", ItemClass.COPPER_INGOT, 1, Station.FURNACE, List.of("G"),
            Map.of('G', ItemClass.RAW_COPPER));
    public static final Recipe WOODEN_HOE = material("wooden hoe", ItemClass.HOE, 1, Station.TABLE, List.of("PP", " S", " S"),
            Map.of('P', ItemClass.PLANKS, 'S', ItemClass.STICK));
    public static final Recipe BREAD = material("bread", ItemClass.FOOD, 1, Station.TABLE, List.of("WWW"), Map.of('W', ItemClass.WHEAT));
    public static final Recipe SHIELD = material("shield", ItemClass.SHIELD, 1, Station.TABLE, List.of("PIP", "PPP", " P "),
            Map.of('P', ItemClass.PLANKS, 'I', ItemClass.IRON_INGOT));
    // A site's fixtures and fittings (backlog/sites.md, backlog/cells.md). A fence and its gate want
    // one wood's planks, as vanilla's recipes do: planks are counted as one class, so a mixed stack
    // can be short at the table - said there, loudly.
    public static final Recipe CHEST = material("chest", ItemClass.CHEST, 1, Station.TABLE, List.of("PPP", "P P", "PPP"),
            Map.of('P', ItemClass.PLANKS));
    public static final Recipe LADDER = material("ladder", ItemClass.LADDER, 3, Station.TABLE, List.of("S S", "SSS", "S S"),
            Map.of('S', ItemClass.STICK));
    public static final Recipe FENCE = material("fence", ItemClass.FENCE, 3, Station.TABLE, List.of("PSP", "PSP"),
            Map.of('P', ItemClass.PLANKS, 'S', ItemClass.STICK));
    public static final Recipe FENCE_GATE = material("fence gate", ItemClass.FENCE_GATE, 1, Station.TABLE, List.of("SPS", "SPS"),
            Map.of('P', ItemClass.PLANKS, 'S', ItemClass.STICK));
    public static final Recipe BUCKET = material("bucket", ItemClass.BUCKET, 1, Station.TABLE, List.of("I I", " I "),
            Map.of('I', ItemClass.IRON_INGOT));
    /** A bed: three white wool over any three planks (backlog/rest-plan.md). */
    public static final Recipe BED = material("bed", ItemClass.BED, 1, Station.TABLE, List.of("WWW", "PPP"),
            Map.of('W', ItemClass.WOOL, 'P', ItemClass.PLANKS));

    /** String from spiders: the bow a soldier shoots back with (docs/military-doctrine.md § 8). */
    public static final Recipe BOW = new Recipe("bow", ItemClass.WEAPON, Optional.of("bow"), 1, Station.TABLE,
            List.of(" SX", "S X", " SX"), Map.of('S', ItemClass.STICK, 'X', ItemClass.STRING));
    /** Flint from gravel, a feather from a chicken: four arrows. */
    public static final Recipe ARROWS = material("arrows", ItemClass.ARROWS, 4, Station.TABLE, List.of("F", "S", "E"),
            Map.of('F', ItemClass.FLINT, 'S', ItemClass.STICK, 'E', ItemClass.FEATHER));

    public static final Recipe WOODEN_SWORD = sword("wooden", 'P', ItemClass.PLANKS);
    public static final Recipe WOODEN_PICKAXE = pickaxe("wooden", 'P', ItemClass.PLANKS);
    public static final Recipe STONE_SWORD = sword("stone", 'C', ItemClass.COBBLESTONE);
    public static final Recipe STONE_PICKAXE = pickaxe("stone", 'C', ItemClass.COBBLESTONE);
    public static final Recipe IRON_SWORD = sword("iron", 'I', ItemClass.IRON_INGOT);
    public static final Recipe IRON_PICKAXE = pickaxe("iron", 'I', ItemClass.IRON_INGOT);
    public static final Recipe IRON_AXE = new Recipe("iron axe", ItemClass.WEAPON, Optional.of("iron_axe"), 1, Station.TABLE,
            List.of("II", "IS", " S"), Map.of('I', ItemClass.IRON_INGOT, 'S', ItemClass.STICK));
    public static final Recipe COPPER_HELMET = armour("copper_helmet", List.of("III", "I I"), ItemClass.COPPER_INGOT);
    public static final Recipe COPPER_CHESTPLATE = armour("copper_chestplate", List.of("I I", "III", "III"), ItemClass.COPPER_INGOT);
    public static final Recipe COPPER_LEGGINGS = armour("copper_leggings", List.of("III", "I I", "I I"), ItemClass.COPPER_INGOT);
    public static final Recipe COPPER_BOOTS = armour("copper_boots", List.of("I I", "I I"), ItemClass.COPPER_INGOT);
    public static final Recipe DIAMOND_SWORD = sword("diamond", 'D', ItemClass.DIAMOND);
    public static final Recipe DIAMOND_PICKAXE = pickaxe("diamond", 'D', ItemClass.DIAMOND);
    public static final Recipe DIAMOND_HELMET = armour("diamond_helmet", List.of("III", "I I"), ItemClass.DIAMOND);
    public static final Recipe DIAMOND_CHESTPLATE = armour("diamond_chestplate", List.of("I I", "III", "III"), ItemClass.DIAMOND);
    public static final Recipe DIAMOND_LEGGINGS = armour("diamond_leggings", List.of("III", "I I", "I I"), ItemClass.DIAMOND);
    public static final Recipe DIAMOND_BOOTS = armour("diamond_boots", List.of("I I", "I I"), ItemClass.DIAMOND);
    public static final Recipe IRON_HELMET = armour("iron_helmet", List.of("III", "I I"));
    public static final Recipe IRON_CHESTPLATE = armour("iron_chestplate", List.of("I I", "III", "III"));
    public static final Recipe IRON_LEGGINGS = armour("iron_leggings", List.of("III", "I I", "I I"));
    public static final Recipe IRON_BOOTS = armour("iron_boots", List.of("I I", "I I"));

    /** Every recipe a soldier knows. */
    public static final List<Recipe> ALL = List.of(PLANKS, STICKS, CRAFTING_TABLE, TORCHES, FURNACE, CHARCOAL, IRON_INGOT, COPPER_INGOT, SHIELD,
            WOODEN_HOE, BREAD, CHEST, LADDER, FENCE, FENCE_GATE, BUCKET, BED, COPPER_HELMET, COPPER_CHESTPLATE, COPPER_LEGGINGS, COPPER_BOOTS,
            BOW, ARROWS,
            WOODEN_SWORD, WOODEN_PICKAXE, STONE_SWORD, STONE_PICKAXE, IRON_SWORD, IRON_PICKAXE, IRON_AXE,
            IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS,
            DIAMOND_SWORD, DIAMOND_PICKAXE, DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS);

    private static Recipe material(String name, ItemClass makes, int yield, Station station, List<String> rows, Map<Character, ItemClass> key) {
        return new Recipe(name, makes, Optional.empty(), yield, station, rows, key);
    }

    private static Recipe sword(String material, char c, ItemClass head) {
        return new Recipe(material + " sword", ItemClass.WEAPON, Optional.of(material + "_sword"), 1, Station.TABLE,
                List.of(String.valueOf(c), String.valueOf(c), "S"), Map.of(c, head, 'S', ItemClass.STICK));
    }

    private static Recipe pickaxe(String material, char c, ItemClass head) {
        String h = String.valueOf(c);
        return new Recipe(material + " pickaxe", ItemClass.PICKAXE, Optional.of(material + "_pickaxe"), 1, Station.TABLE,
                List.of(h + h + h, " S ", " S "), Map.of(c, head, 'S', ItemClass.STICK));
    }

    private static Recipe armour(String item, List<String> rows) {
        return armour(item, rows, ItemClass.IRON_INGOT);
    }

    private static Recipe armour(String item, List<String> rows, ItemClass material) {
        return new Recipe(item.replace('_', ' '), ItemClass.ARMOUR, Optional.of(item), 1, Station.TABLE, rows, Map.of('I', material));
    }

    /** Planks a furnace burns a smelt: one plank burns 300 ticks, a smelt takes 200. */
    public static final double SMELTS_PER_PLANK = 1.5;

    public Recipe {
        rows = List.copyOf(rows);
        key = Map.copyOf(key);
        int width = rows.stream().mapToInt(String::length).max().orElse(0);
        if (rows.size() > 3 || width > 3 || station == Station.GRID && (rows.size() > 2 || width > 2)) {
            throw new IllegalArgumentException(name + " doesn't fit its station's grid");
        }
    }

    /** The recipe that makes {@code itemClass} as a material - counted by class - if a soldier knows one. */
    public static Optional<Recipe> making(ItemClass itemClass) {
        return ALL.stream().filter(r -> r.makes == itemClass && r.item.isEmpty()).findFirst();
    }

    /** What one craft takes: each class and how many. */
    public Map<ItemClass, Integer> ingredients() {
        Map<ItemClass, Integer> counts = new LinkedHashMap<>();
        cells(3).values().forEach(c -> counts.merge(c, 1, Integer::sum));
        return counts;
    }

    /**
     * The pattern laid into a grid {@code width} cells wide, top left: grid slot (1 + row x width +
     * column, as a crafting window numbers them after its result slot 0) to the class that goes there.
     */
    public Map<Integer, ItemClass> cells(int width) {
        Map<Integer, ItemClass> cells = new LinkedHashMap<>();
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).length(); column++) {
                char c = rows.get(row).charAt(column);
                if (c != ' ') {
                    cells.put(1 + row * width + column, key.get(c));
                }
            }
        }
        return cells;
    }

    /** Planks to burn for {@code smelts} smelts. */
    public static int fuelFor(int smelts) {
        return (int) Math.ceil(smelts / SMELTS_PER_PLANK);
    }
}
