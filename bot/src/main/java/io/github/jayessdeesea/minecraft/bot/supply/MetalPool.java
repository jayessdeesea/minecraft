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
 * Raw ore through the common stockpile (docs/echelons/tenets.md, specialists produce, missions
 * consume): a soldier keeps the ore of a metal its next piece of kit needs only if it can make
 * that piece with it - its own, or its own and the stockpile's together, which it then draws. Any
 * other raw ore it carries is the force's, and goes in. Live, eleven raw copper lay in four packs,
 * none enough for a piece, while each soldier walked off alone to dig the rest of its own eight.
 *
 * <p>Pure: what to do, from what's carried, what the next piece wants and the stockpile as last seen.
 */
public final class MetalPool {

    /** The ore each metal is smelted from. */
    private static final Map<ItemClass, ItemClass> ORE = Map.of(ItemClass.IRON_INGOT, ItemClass.RAW_IRON,
            ItemClass.COPPER_INGOT, ItemClass.RAW_COPPER);

    private MetalPool() {
    }

    /** Draw {@code ore} until {@code carry} is carried. */
    public record Draw(ItemClass ore, int carry) {
    }

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
            int pooled = seen.map(s -> ore == ItemClass.RAW_IRON ? s.rawIron() : s.rawCopper()).orElse(0);
            if (need > 0 && have >= need) {
                continue; // it can make it: keep it
            }
            if (need > 0 && have + pooled >= need) {
                draw = new Draw(ore, inventory.count(ore) + need - have);
                continue;
            }
            if (inventory.count(ore) > 0) {
                stock.add(ore);
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
