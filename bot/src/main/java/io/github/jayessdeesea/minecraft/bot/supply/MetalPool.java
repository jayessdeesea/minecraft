package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Stockpile;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Raw ore and ingots through the common stockpile (docs/echelons/tenets.md, specialists produce,
 * missions consume): a soldier keeps the metal its next piece of kit needs only if it can make that
 * piece with it - its own, or its own and the stockpile's together, which it then draws, ingots
 * first. Any other ore or ingot it carries is the force's, and goes in. Live, eleven raw copper lay
 * in four packs, none enough for a piece, while each soldier walked off alone to dig the rest of its
 * own eight; and (field300) the force's 2 iron and 3 copper ingots lay one or two to a pack.
 *
 * <p>Pure: what to do, from what's carried, what the next piece wants and the stockpile as last seen.
 */
public final class MetalPool {

    /** The ore each metal is smelted from. */
    private static final Map<ItemClass, ItemClass> ORE = Map.of(ItemClass.IRON_INGOT, ItemClass.RAW_IRON,
            ItemClass.COPPER_INGOT, ItemClass.RAW_COPPER);

    private MetalPool() {
    }

    /** Draw {@code ore} - raw ore or its ingot - until {@code carry} is carried. */
    public record Draw(ItemClass ore, int carry) {
    }

    /** What's drawn or stocked: each metal's raw ore and its ingot. */
    public static final Set<ItemClass> METALS = Set.of(ItemClass.RAW_IRON, ItemClass.RAW_COPPER, ItemClass.IRON_INGOT, ItemClass.COPPER_INGOT);

    /** What to do: the raw ores to put in the stockpile, and one to draw out. */
    public record Plan(Set<ItemClass> stock, Optional<Draw> draw) {
        public Plan {
            stock = Set.copyOf(stock);
        }

        /** Raw ore to put in, counted from {@code inventory}. */
        public int stocking(Inventory inventory) {
            return stock.stream().mapToInt(inventory::count).sum();
        }
    }

    /** For a soldier carrying {@code inventory}, its next piece {@code next} if any, the stockpile as {@code seen}. */
    public static Plan plan(Inventory inventory, Optional<Recipe> next, Optional<Stockpile> seen) {
        Set<ItemClass> stock = EnumSet.noneOf(ItemClass.class);
        Draw draw = null;
        for (Map.Entry<ItemClass, ItemClass> metal : ORE.entrySet()) {
            ItemClass ingot = metal.getKey(), ore = metal.getValue();
            int need = next.map(r -> needs(r, ingot)).orElse(0);
            int have = inventory.count(ore) + inventory.count(ingot);
            boolean iron = ore == ItemClass.RAW_IRON;
            int pooledOre = seen.map(s -> iron ? s.rawIron() : s.rawCopper()).orElse(0);
            int pooledIngots = seen.map(s -> iron ? s.ironIngots() : s.copperIngots()).orElse(0);
            if (need > 0 && have >= need) {
                continue; // it can make it: keep it
            }
            if (need > 0 && have + pooledOre + pooledIngots >= need) {
                // Ingots first: no smelt, and no fuel.
                draw = pooledIngots > 0
                        ? new Draw(ingot, inventory.count(ingot) + Math.min(pooledIngots, need - have))
                        : new Draw(ore, inventory.count(ore) + need - have);
                continue;
            }
            if (inventory.count(ore) > 0) {
                stock.add(ore);
            }
            if (inventory.count(ingot) > 0) {
                stock.add(ingot);
            }
        }
        return new Plan(stock, Optional.ofNullable(draw));
    }

    /** How many of {@code ingot} one {@code recipe} takes. */
    static int needs(Recipe recipe, ItemClass ingot) {
        int count = 0;
        for (String row : recipe.rows()) {
            for (char c : row.toCharArray()) {
                if (recipe.key().get(c) == ingot) {
                    count++;
                }
            }
        }
        return count;
    }
}
