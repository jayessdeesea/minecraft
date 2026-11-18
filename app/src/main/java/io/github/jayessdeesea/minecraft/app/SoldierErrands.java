package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.supply.Pack;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;

import java.util.Map;
import java.util.Optional;

/**
 * The errands a soldier still decides for itself - when to draw food, logs or a bed's string from the
 * stockpile, what its pack keeps before it stores the rest, when it hunts. Each is a what, and the
 * soldier owns only the how (docs/echelons/tenets.md): these are to move up to the squad leader as
 * orders, one by one, and this class to empty (backlog/split-oversized-sources.md).
 */
final class SoldierErrands {

    private SoldierErrands() {
    }

    /** At this food level or below, with no rations, a soldier free to hunts for meat: it has missed a meal or two. */
    static final int HUNT_BELOW_FOOD = 14;

    /**
     * Whether a soldier with no rations needs food: at {@link #HUNT_BELOW_FOOD} or below, or
     * wounded and short of the food it heals at - otherwise a wound taken at food 15 never heals.
     */
    static boolean wantsFood(Condition condition) {
        return condition.kit().map(k -> Subsistence.short_(k.rations())).orElse(true)
                && (condition.food() <= HUNT_BELOW_FOOD || condition.health() < Condition.MAX_HEALTH
                && condition.food() < Condition.REGENERATION_FOOD);
    }

    /** Within this many blocks of the stockpile, a soldier below {@link Subsistence#RESUPPLY} draws bread from it on its way. */
    static final double DRAW_FOOD_NEAR = 16;

    /**
     * Whether a soldier carrying {@code kit} goes to {@code stockpile} for bread: there's bread in it
     * ({@code seen}, as the chest was last opened), and it is below {@link Subsistence#FOOD_FIRST} -
     * food before its work, wherever it is - or below {@link Subsistence#RESUPPLY} and near it, as
     * it's convenient.
     */
    static boolean drawsFood(Kit kit, Optional<Stockpile> seen, Position at, Optional<Position> stockpile) {
        if (kit == null || stockpile.isEmpty() || seen.map(st -> st.rations() == 0).orElse(true)) {
            return false;
        }
        return Subsistence.short_(kit.rations()) || Subsistence.low(kit.rations())
                && Cohesion.distance(at, stockpile.get()) <= DRAW_FOOD_NEAR;
    }

    /**
     * The workshop {@code squads} - its squad's - for a soldier, but the camp's for one of a squad on
     * the keep that isn't on the keep's work ({@code onKeepWork}): fed first at the camp's farm, it
     * bakes and stocks there. Live, a farmer walked 70 blocks to the keep's table to bake a loaf.
     */
    static Workshop soldiersWorkshop(Workshop squads, Workshop keep, Workshop camp, boolean onKeepWork) {
        return squads != null && squads == keep && !onKeepWork && camp != null ? camp : squads;
    }

    /** Logs a soldier short of them for its kit draws from the stockpile: a table, sticks and a tool's worth. */
    static final int DRAW_LOGS = 8;

    /**
     * Whether a soldier goes to {@code stockpile} for logs: its kit's last try gave up for want of a
     * tree in reach ({@code failure}), it carries too few to make a table and a tool from, and the
     * chest was last seen holding a load - or hasn't been seen since a restart: the trip looks, and
     * what it sees is then known. Live, a bare-handed miner with no tree within 48 blocks stood a day
     * in the mine, while the camp's chest held 85 logs nobody had opened it to see.
     */
    static boolean drawsLogs(Optional<String> failure, int logs, Optional<Stockpile> seen, Optional<Position> stockpile) {
        return stockpile.isPresent() && logs < 2 && failure.filter(f -> f.contains("no LOG") || f.contains("{LOG=")).isPresent()
                && seen.map(st -> st.logs() >= DRAW_LOGS).orElse(true);
    }

    /**
     * What a keep builder keeps of each kind once its pack is near full, worth least first: a slot of
     * dirt to climb on, a stack of seeds, a few saplings, no fences, a bed's string and a little flint, one
     * crafting table, the slab's cobblestone. The rest goes into the depot on its next trip while the
     * depot's under its most of it ({@link StockLevels}), and is thrown where it stands only once it
     * isn't (operator, 2026-11-17: fill a slot, dump it when something better comes along; none of it
     * is garbage).
     */
    static final Map<ItemClassRegistry.ItemClass, Integer> KEEP_PACK = keepPack();

    private static Map<ItemClassRegistry.ItemClass, Integer> keepPack() {
        Map<ItemClassRegistry.ItemClass, Integer> kept = new java.util.LinkedHashMap<>();
        kept.put(ItemClassRegistry.ItemClass.DIRT, 64);
        kept.put(ItemClassRegistry.ItemClass.SEEDS, 64);
        kept.put(ItemClassRegistry.ItemClass.FENCE, 0);
        kept.put(ItemClassRegistry.ItemClass.SAPLING, 16);
        kept.put(ItemClassRegistry.ItemClass.STRING, 12); // a bed's three wool
        kept.put(ItemClassRegistry.ItemClass.FLINT, 16);
        kept.put(ItemClassRegistry.ItemClass.CRAFTING_TABLE, 1);
        kept.put(ItemClassRegistry.ItemClass.PLANKS, 16);
        kept.put(ItemClassRegistry.ItemClass.STICK, 16);
        kept.put(ItemClassRegistry.ItemClass.COBBLESTONE, Pack.COBBLESTONE_KEPT);
        return java.util.Collections.unmodifiableMap(kept);
    }

    /** What any other soldier keeps in its pack of the depot's kinds before it stores the rest. */
    static final Map<ItemClassRegistry.ItemClass, Integer> CARRIED = Map.of(
            ItemClassRegistry.ItemClass.DIRT, Pack.DIRT_KEPT, ItemClassRegistry.ItemClass.SEEDS, Pack.SEEDS_KEPT,
            ItemClassRegistry.ItemClass.SAPLING, 64, ItemClassRegistry.ItemClass.STRING, 64, ItemClassRegistry.ItemClass.FLINT, 64,
            ItemClassRegistry.ItemClass.FENCE, 0, ItemClassRegistry.ItemClass.CRAFTING_TABLE, 1, ItemClassRegistry.ItemClass.WHEAT, 64,
            ItemClassRegistry.ItemClass.PLANKS, 16, ItemClassRegistry.ItemClass.STICK, 16);

    /** What a mine's cutter keeps: its backfill's dirt, and ore before the rest (the rules of a mine). */
    static final Map<ItemClassRegistry.ItemClass, Integer> MINE_CARRIED = Map.of(
            ItemClassRegistry.ItemClass.DIRT, Pack.MINER_DIRT_KEPT, ItemClassRegistry.ItemClass.CRAFTING_TABLE, 1);

    /**
     * The string a soldier still wants for a bed: none once it has one, else what its wool and string fall
     * short of the bed's three wool, four string a wool.
     */
    static int bedString(Inventory inventory) {
        if (inventory.count(ItemClass.BED) > 0) {
            return 0;
        }
        int perWool = Recipe.WOOL.ingredients().get(ItemClass.STRING);
        int wanted = Recipe.BED.ingredients().get(ItemClass.WOOL) - inventory.count(ItemClass.WOOL);
        return Math.max(0, wanted * perWool - inventory.count(ItemClass.STRING));
    }
}
