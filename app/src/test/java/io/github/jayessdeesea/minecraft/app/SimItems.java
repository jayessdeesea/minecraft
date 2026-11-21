package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.BlockChange;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.ChunkUpdate;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.EntityMove;
import io.github.jayessdeesea.minecraft.protocol.EntityUpdate;
import io.github.jayessdeesea.minecraft.protocol.HealthUpdate;
import io.github.jayessdeesea.minecraft.protocol.InventoryUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.PlayState;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

import static io.github.jayessdeesea.minecraft.app.SimServer.*;

/** The sim server's items: their stacks and uses, what a block drops and an item places, smelting and crafting. Pure. */
final class SimItems {

    private SimItems() {
    }

/** How many of {@code item} a slot holds, as vanilla's: one tool, weapon or piece of armour; 64 of the rest. */
    static int maxStack(int item) {
        Optional<ItemClass> itemClass = ItemClassRegistry.classOf(item);
        boolean one = itemClass.filter(c -> c == ItemClass.WEAPON || c == ItemClass.ARMOUR || c == ItemClass.PICKAXE
                || c == ItemClass.SHIELD || c == ItemClass.HOE || c == ItemClass.BUCKET || c == ItemClass.WATER_BUCKET).isPresent()
                || ItemClassRegistry.meleeRank(item).isPresent() || ItemClassRegistry.nameOf(item).equals(Optional.of("bow"));
        return one ? 1 : 64;
    }

    /** Uses a pickaxe has in it, as vanilla's: wooden 59, stone 131. */
    static int uses(int item) {
        return item == ITEM_WOODEN_PICKAXE ? 59 : item == ITEM_STONE_PICKAXE ? 131 : Integer.MAX_VALUE;
    }

    /**
     * Whether a dig of {@code state} with {@code held} in hand (-1 for none), begun at {@code started} (null: never begun),
     * finished at {@code now} too soon: under 70% of its break time with that tool, as the server holds it. Without it the
     * simulation broke any block at once whatever was in hand (operator, 2026-11-20: simulate the hotbar - the tool must
     * be in hand to use it).
     */
    static boolean dugTooFast(int state, int held, Long started, long now) {
        Optional<io.github.jayessdeesea.minecraft.bot.world.Hardness> hardness = io.github.jayessdeesea.minecraft.bot.world.ChunkCache.hardnessOf(state);
        if (hardness.isEmpty() || hardness.get().hardness() == 0) {
            return false;
        }
        int hand = io.github.jayessdeesea.minecraft.bot.world.Hardness.HAND;
        int tier = held < 0 ? hand : ItemClassRegistry.miningTier(held).orElse(hand);
        double speed = held < 0 ? 1 : switch (hardness.get().tool()) {
            case AXE -> ItemClassRegistry.axeSpeed(held).orElse(1);
            case SHOVEL -> ItemClassRegistry.shovelSpeed(held).orElse(1);
            default -> 1;
        };
        return started == null || (now - started) / 50.0 < 0.7 * hardness.get().ticks(tier, speed);
    }

    /**
     * The item a block drops whose own item the client's tables don't list - granite, andesite, tuff: as live, the soldier
     * carries it and counts it as nothing it builds or makes with.
     */
    static final int UNLISTED_ITEM = 4000;

    /** What a block drops broken with what's in hand: stone and ore only to a pickaxe. */
    static Optional<Integer> drop(int state, Optional<Integer> held) {
        boolean pickaxe = held.filter(i -> i == ITEM_WOODEN_PICKAXE || i == ITEM_STONE_PICKAXE).isPresent();
        boolean stonePick = held.filter(i -> i == ITEM_STONE_PICKAXE).isPresent();
        return switch (state) {
            case GRASS, DIRT -> Optional.of(ITEM_DIRT);
            case GLASS -> Optional.empty(); // glass drops nothing without silk touch
            case STONE, COBBLESTONE -> pickaxe ? Optional.of(ITEM_COBBLESTONE) : Optional.empty();
            case COAL_ORE -> pickaxe ? Optional.of(ITEM_COAL) : Optional.empty();
            case IRON_ORE -> stonePick || held.filter(i -> i == itemNamedOrMinus("iron_pickaxe")).isPresent() ? Optional.of(ITEM_RAW_IRON) : Optional.empty();
            // Emerald ore drops its emerald only to an iron pickaxe or better.
            case EMERALD_ORE -> held.filter(i -> i == itemNamedOrMinus("iron_pickaxe")).isPresent()
                    ? Optional.of(itemNamedOrMinus("emerald")).filter(i -> i >= 0) : Optional.empty();
            case TORCH -> Optional.of(ITEM_TORCH);
            case OAK_LOG -> Optional.of(ITEM_OAK_LOG);
            case SPRUCE_LOG -> Optional.of(itemNamed("spruce_log"));
            case SPRUCE_SAPLING -> Optional.of(itemNamed("spruce_sapling"));
            case CRAFTING_TABLE -> Optional.of(itemOf(ItemClass.CRAFTING_TABLE));
            case CHEST -> Optional.of(itemOf(ItemClass.CHEST));
            case FURNACE -> pickaxe ? Optional.of(itemOf(ItemClass.FURNACE)) : Optional.empty();
            case WHEAT_RIPE -> Optional.of(itemOf(ItemClass.WHEAT));
            case WHEAT_SPROUT, WHEAT_SPROUT + 1, WHEAT_SPROUT + 2, WHEAT_SPROUT + 3, WHEAT_SPROUT + 4, WHEAT_SPROUT + 5, WHEAT_SPROUT + 6
                    -> Optional.of(itemOf(ItemClass.SEEDS));
            // Any other block as the server has it: its own item - deepslate's cobbled - and rock only to a pickaxe. A recorded
            // world (WorldRecords) brings in granite, andesite, tuff and the rest; live, they drop as they're named.
            default -> {
                String name = io.github.jayessdeesea.minecraft.bot.world.ChunkCache.blockName(state)
                        .orElseThrow(() -> new UnsupportedOperationException("simulated: breaking block state " + state));
                boolean rock = io.github.jayessdeesea.minecraft.bot.world.ChunkCache.isRock(state);
                int item = SimServer.itemNamedOrMinus(name.equals("deepslate") ? "cobbled_deepslate" : name);
                yield rock && !pickaxe ? Optional.empty() : Optional.of(item >= 0 ? item : UNLISTED_ITEM);
            }
        };
    }

    /** The block an item places, if it's one the simulation knows. */
    /** An oak fence, and an oak fence gate shut and open, facing north: as the server numbers their states. */
    static final int FENCE = 8666, GATE = 10387, GATE_OPEN = 10385;

    static Optional<Integer> placesAs(int item) {
        return switch (item) {
            case ITEM_COBBLESTONE -> Optional.of(COBBLESTONE);
            case ITEM_DIRT -> Optional.of(DIRT);
            case ITEM_OAK_LOG -> Optional.of(OAK_LOG);
            case ITEM_TORCH -> Optional.of(TORCH);
            default -> item == itemNamed("spruce_sapling") ? Optional.of(SPRUCE_SAPLING) : item == itemNamed("oak_fence") ? Optional.of(FENCE)
                    : item == itemNamed("oak_fence_gate") ? Optional.of(GATE) : Optional.empty();
        };
    }

    static void take(int[][] holder, int index) {
        holder[index] = holder[index][1] == 1 ? null : new int[]{holder[index][0], holder[index][1] - 1};
    }

    /** What smelting {@code input} makes: the furnace recipe for its class. */
    static int smelted(int input) {
        ItemClass in = ItemClassRegistry.classOf(input).orElseThrow();
        Recipe recipe = Recipe.ALL.stream().filter(r -> r.station() == Recipe.Station.FURNACE && r.key().containsValue(in)).findFirst()
                .orElseThrow(() -> new AssertionError("nothing smelts " + in));
        return itemOf(recipe.makes());
    }

    static int itemNamed(String name) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of(name)))
                .findFirst().orElseThrow();
    }

    /** The recipe a grid's contents match - shaped, anywhere in the grid. */
    static Optional<Recipe> match(int[][] grid, int width) {
        int minRow = 9, minColumn = 9, maxRow = -1, maxColumn = -1;
        for (int cell = 1; cell < grid.length; cell++) {
            if (grid[cell] != null) {
                int row = (cell - 1) / width, column = (cell - 1) % width;
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < 0) {
            return Optional.empty();
        }
        for (Recipe recipe : Recipe.ALL) {
            if (recipe.station() == Recipe.Station.FURNACE || recipe.rows().size() != maxRow - minRow + 1) {
                continue;
            }
            int columns = recipe.rows().stream().mapToInt(String::length).max().orElseThrow();
            if (columns != maxColumn - minColumn + 1 || columns > width) {
                continue;
            }
            boolean matches = true;
            for (int row = 0; row < recipe.rows().size() && matches; row++) {
                String pattern = recipe.rows().get(row);
                for (int column = 0; column < columns && matches; column++) {
                    char c = column < pattern.length() ? pattern.charAt(column) : ' ';
                    int[] item = grid[1 + (minRow + row) * width + minColumn + column];
                    matches = c == ' ' ? item == null
                            : item != null && ItemClassRegistry.classOf(item[0]).equals(Optional.of(recipe.key().get(c)));
                }
            }
            if (matches) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }
}
