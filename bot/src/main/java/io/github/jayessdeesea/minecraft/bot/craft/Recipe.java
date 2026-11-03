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
 * @param rows   the pattern, row by row, one character a cell; a space is an empty cell. A
 *               furnace recipe is its one ingredient.
 * @param key    what each pattern character stands for
 */
public record Recipe(String name, ItemClass makes, int yield, Station station, List<String> rows, Map<Character, ItemClass> key) {

    /** Where a recipe is made. */
    public enum Station {
        /** The inventory's own 2x2 grid - no block needed. */
        GRID,
        /** A crafting table's 3x3 grid. */
        TABLE,
        /** A furnace: one ingredient in, one result out, a plank burnt for every 1.5. */
        FURNACE
    }

    public static final Recipe PLANKS = new Recipe("planks", ItemClass.PLANKS, 4, Station.GRID, List.of("L"), Map.of('L', ItemClass.LOG));
    public static final Recipe STICKS = new Recipe("sticks", ItemClass.STICK, 4, Station.GRID, List.of("P", "P"), Map.of('P', ItemClass.PLANKS));
    public static final Recipe CRAFTING_TABLE = new Recipe("crafting table", ItemClass.CRAFTING_TABLE, 1, Station.GRID,
            List.of("PP", "PP"), Map.of('P', ItemClass.PLANKS));
    public static final Recipe TORCHES = new Recipe("torches", ItemClass.TORCH, 4, Station.GRID, List.of("C", "S"),
            Map.of('C', ItemClass.COAL, 'S', ItemClass.STICK));
    public static final Recipe FURNACE = new Recipe("furnace", ItemClass.FURNACE, 1, Station.TABLE, List.of("CCC", "C C", "CCC"),
            Map.of('C', ItemClass.COBBLESTONE));
    public static final Recipe WOODEN_SWORD = new Recipe("wooden sword", ItemClass.WEAPON, 1, Station.TABLE, List.of("P", "P", "S"),
            Map.of('P', ItemClass.PLANKS, 'S', ItemClass.STICK));
    public static final Recipe WOODEN_PICKAXE = new Recipe("wooden pickaxe", ItemClass.PICKAXE, 1, Station.TABLE, List.of("PPP", " S ", " S "),
            Map.of('P', ItemClass.PLANKS, 'S', ItemClass.STICK));
    public static final Recipe CHARCOAL = new Recipe("charcoal", ItemClass.COAL, 1, Station.FURNACE, List.of("L"), Map.of('L', ItemClass.LOG));

    /** Every recipe, at most one for each class made. */
    public static final List<Recipe> ALL = List.of(PLANKS, STICKS, CRAFTING_TABLE, TORCHES, FURNACE, WOODEN_SWORD, WOODEN_PICKAXE, CHARCOAL);

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

    /** The recipe that makes {@code itemClass}, if a soldier knows one. */
    public static Optional<Recipe> making(ItemClass itemClass) {
        return ALL.stream().filter(r -> r.makes == itemClass).findFirst();
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
