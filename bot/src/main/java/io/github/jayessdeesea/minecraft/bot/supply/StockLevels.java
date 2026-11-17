package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Subsistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * How the depot keeps each material: a reserve, held as rations and ammunition are, and a most, past
 * which none is hoarded (operator, 2026-11-17: "a reserve of spruce saplings of 64 for the supply
 * dump and 256 as the max"; ore "way way more valuable" - diamonds and iron always kept, more chests
 * built for them). Under its reserve, a kind is worth a trip of its own; between its reserve and its
 * most, it rides along on a trip made anyway; at its most, it's what a full pack throws away. Work
 * draws on it down through the reserve. Nothing here is garbage - the most is there so the packs
 * and the chests have room for what's worth more. The one table: docs/military-doctrine.md, the
 * stockpile's levels.
 */
public final class StockLevels {

    /** A kind's reserve, refilled first, and its most, never stocked past. */
    public record Level(int reserve, int most) {
        public Level {
            if (reserve < 0 || most < reserve) {
                throw new IllegalArgumentException("a reserve of " + reserve + " and a most of " + most);
            }
        }
    }

    /** No most: kept whatever there is of it, the depot's chests added to for it. */
    public static final int UNBOUNDED = Integer.MAX_VALUE;

    /** The depot's levels: dirt is everywhere underfoot, so little of it is kept; ore and diamonds all of it. */
    public static final Map<ItemClass, Level> DEPOT = depot();

    private static Map<ItemClass, Level> depot() {
        Map<ItemClass, Level> levels = new EnumMap<>(ItemClass.class);
        levels.put(ItemClass.DIAMOND, new Level(0, UNBOUNDED));
        levels.put(ItemClass.RAW_IRON, new Level(0, UNBOUNDED));
        levels.put(ItemClass.IRON_INGOT, new Level(0, UNBOUNDED));
        levels.put(ItemClass.RAW_COPPER, new Level(64, 256));
        levels.put(ItemClass.COPPER_INGOT, new Level(64, 256));
        levels.put(ItemClass.LOG, new Level(io.github.jayessdeesea.minecraft.command.Keep.LOGS_RESERVE,
                io.github.jayessdeesea.minecraft.command.Keep.LOGS_MOST));
        levels.put(ItemClass.FOOD, new Level(Subsistence.STOCKPILE_RESERVE, Subsistence.STOCKPILE_MOST));
        levels.put(ItemClass.WHEAT, new Level(64, 256));
        // So common they're all but had on demand (operator, 2026-11-17): handy, never hoarded.
        levels.put(ItemClass.COBBLESTONE, new Level(0, 256));
        levels.put(ItemClass.SAPLING, new Level(64, 256));
        levels.put(ItemClass.SEEDS, new Level(64, 256));
        levels.put(ItemClass.STRING, new Level(64, 256));
        levels.put(ItemClass.FLINT, new Level(64, 256));
        levels.put(ItemClass.FENCE, new Level(0, 64));
        levels.put(ItemClass.CRAFTING_TABLE, new Level(1, 4));
        levels.put(ItemClass.DIRT, new Level(0, 256));
        return Collections.unmodifiableMap(levels);
    }

    /** How many of a kind make a trip of their own for a short reserve: a part-stack. */
    public static final int TRIP_FOR = 16;

    private StockLevels() {
    }

    /** Whether the depot, holding {@code stocked}, has its most of {@code kind}. */
    public static boolean atMost(ItemClass kind, ToIntFunction<ItemClass> stocked) {
        Level level = DEPOT.get(kind);
        return level != null && level.most() != UNBOUNDED && stocked.applyAsInt(kind) >= level.most();
    }

    /**
     * The slots of what {@code inventory} carries of {@code kept}'s kinds past what it keeps of each -
     * what its work wants in the pack - that the depot, holding {@code stocked} of each kind, has room
     * for under its most: the smallest stacks first, never the one in hand, and never past the most
     * by a whole stack. Only {@code kept}'s kinds: ore is the metal pool's to carry in (MetalPool).
     */
    public static List<Integer> toStore(Inventory inventory, Map<ItemClass, Integer> kept, ToIntFunction<ItemClass> stocked) {
        List<Integer> out = new ArrayList<>();
        kept.forEach((kind, keep) -> {
            Level level = DEPOT.get(kind);
            if (level != null) {
                out.addAll(toStore(inventory, kind, keep, room(level, stocked.applyAsInt(kind))));
            }
        });
        return out;
    }

    private static int room(Level level, int stocked) {
        return level.most() == UNBOUNDED ? UNBOUNDED : level.most() - stocked;
    }

    /** The slots of {@code kind} past {@code kept} that fit in {@code room}. */
    static List<Integer> toStore(Inventory inventory, ItemClass kind, int kept, int room) {
        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        List<Integer> slots = new ArrayList<>();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (slot != held && ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(kind))) {
                slots.add(slot);
            }
        }
        slots.sort(Comparator.comparingInt(inventory::countAt));
        int left = inventory.count(kind);
        List<Integer> out = new ArrayList<>();
        for (int slot : slots) {
            int count = inventory.countAt(slot);
            if (room <= 0 || left - count < kept) {
                break;
            }
            out.add(slot);
            left -= count;
            room -= count;
        }
        return out;
    }

    /**
     * Whether {@code inventory} carries, past {@code kept}, at least {@link #TRIP_FOR} of a kind the
     * depot holds under its reserve - or enough to fill it: a trip of its own, the reserve refilled
     * once the work that drew on it is done (operator, 2026-11-17: second priority, getting back up
     * to the reserve).
     */
    public static boolean reserveShort(Inventory inventory, Map<ItemClass, Integer> kept, ToIntFunction<ItemClass> stocked) {
        for (Map.Entry<ItemClass, Integer> keep : kept.entrySet()) {
            Level level = DEPOT.get(keep.getKey());
            int short_ = level == null ? 0 : level.reserve() - stocked.applyAsInt(keep.getKey());
            if (short_ <= 0) {
                continue;
            }
            int storable = toStore(inventory, keep.getKey(), keep.getValue(), room(level, stocked.applyAsInt(keep.getKey())))
                    .stream().mapToInt(inventory::countAt).sum();
            if (storable >= Math.min(TRIP_FOR, short_)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What a pack keeps of each of {@code kept}'s kinds before it throws any away: its keep for a kind
     * the depot is at its most of, all of it otherwise - the rest goes into the depot, not on the ground.
     */
    public static Map<ItemClass, Integer> throwPast(Map<ItemClass, Integer> kept, ToIntFunction<ItemClass> stocked) {
        Map<ItemClass, Integer> past = new java.util.LinkedHashMap<>();
        kept.forEach((kind, keep) -> {
            past.put(kind, DEPOT.containsKey(kind) && !atMost(kind, stocked) ? Integer.MAX_VALUE : keep);
        });
        return past;
    }
}
