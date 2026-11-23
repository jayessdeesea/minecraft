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
    /**
     * Whether {@code failure} - "short of {RAW_IRON=3, LOG=1, COBBLESTONE=2}" - lists {@code item} among what it's short
     * of, wherever in the list: live (field542), Olga's sword batched with a bucket read cobblestone third, and no draw
     * matched it.
     */
    static boolean shortOf(String failure, String item) {
        return java.util.regex.Pattern.compile("[{ ]" + item + "=").matcher(failure).find();
    }

    /** What a chest holds of {@code metal} - raw ore, an ingot, logs or cobblestone - by its stockpile's count. */
    static java.util.function.ToIntFunction<Stockpile> held(io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass metal) {
        return switch (metal) {
            case RAW_IRON -> Stockpile::rawIron;
            case RAW_COPPER -> Stockpile::rawCopper;
            case IRON_INGOT -> Stockpile::ironIngots;
            case COPPER_INGOT -> Stockpile::copperIngots;
            case LOG -> Stockpile::logs;
            case COBBLESTONE -> Stockpile::stone;
            default -> throw new IllegalArgumentException(metal + " isn't drawn from the chest holding it");
        };
    }

    /** How far across, and how far up or down, a chest is still in reach of a soldier on a mission's task. */
    static final double MISSION_REACH = 48, MISSION_REACH_UP = 12;

    /**
     * Whether {@code chest} is in reach of a soldier at {@code from} whose squad is on a mission's task (operator,
     * 2026-11-22: a soldier on a task stays on it): near by, and near its own height. Live (field692), two of a squad
     * flattening a chunk walked 300 blocks each to a chest at the bottom of the mine, 60 under them, for eight logs.
     */
    static boolean inMissionReach(Position chest, Position from) {
        return Math.hypot(chest.x() - from.x(), chest.z() - from.z()) <= MISSION_REACH && Math.abs(chest.y() - from.y()) <= MISSION_REACH_UP;
    }

    /** Cobblestone drawn for a weapon or tool short of it: a stone sword's blade, a pickaxe's head, and some over. */
    static final int DRAW_STONE = 8;

    /**
     * Whether a soldier whose making is short of cobblestone draws it from the stockpile, as one short of logs does: live
     * (field541), four soldiers gave their stone swords up "short of COBBLESTONE=2", the camp's chests holding 896.
     */
    static boolean drawsStone(Optional<String> failure, int stone, Optional<Stockpile> seen, Optional<Position> stockpile) {
        return stockpile.isPresent() && stone < DRAW_STONE && failure.filter(f -> shortOf(f, "COBBLESTONE")).isPresent()
                && seen.map(st -> st.stone() >= DRAW_STONE).orElse(true);
    }

    static boolean drawsLogs(Optional<String> failure, int logs, Optional<Stockpile> seen, Optional<Position> stockpile) {
        return stockpile.isPresent() && logs < 2 && failure.filter(f -> f.contains("no LOG") || shortOf(f, "LOG")).isPresent()
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
        kept.put(ItemClassRegistry.ItemClass.SAPLING, 0); // a planter's to carry, no one else's (PLANTER_CARRIED)
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
            ItemClassRegistry.ItemClass.SAPLING, 0, ItemClassRegistry.ItemClass.STRING, 64, ItemClassRegistry.ItemClass.FLINT, 64,
            ItemClassRegistry.ItemClass.FENCE, 0, ItemClassRegistry.ItemClass.CRAFTING_TABLE, 1, ItemClassRegistry.ItemClass.WHEAT, 64,
            ItemClassRegistry.ItemClass.PLANKS, 16, ItemClassRegistry.ItemClass.STICK, 16);

    /**
     * What a planter keeps: its saplings, a stack - anyone else's go into the stockpile with its next load (operator,
     * 2026-11-21: soldiers don't carry saplings round unless they're replanting; live, field630, squadmates off the grove
     * carried 6 and 11 while the foresters had none).
     */
    static final Map<ItemClassRegistry.ItemClass, Integer> PLANTER_CARRIED = planterCarried();

    private static Map<ItemClassRegistry.ItemClass, Integer> planterCarried() {
        Map<ItemClassRegistry.ItemClass, Integer> kept = new java.util.HashMap<>(CARRIED);
        kept.put(ItemClassRegistry.ItemClass.SAPLING, 64);
        return java.util.Collections.unmodifiableMap(kept);
    }

    /** What a harvester keeps: no wheat - it goes to the stockpile, for the baker (command.FarmDuties) - and its seed. */
    static final Map<ItemClassRegistry.ItemClass, Integer> HARVESTER_CARRIED = harvesterCarried();

    private static Map<ItemClassRegistry.ItemClass, Integer> harvesterCarried() {
        Map<ItemClassRegistry.ItemClass, Integer> kept = new java.util.HashMap<>(CARRIED);
        kept.put(ItemClassRegistry.ItemClass.WHEAT, 0);
        return java.util.Collections.unmodifiableMap(kept);
    }

    /**
     * Logs a soldier not gathering wood carries before the rest is a load for the stockpile: a stack. Live
     * (field452-454), a draw for 8 took a whole stack of 28 - a shift-click moves a stack - and it went back in a
     * minute later at 16, the stockpile bare for the next.
     */
    static final int LOGS_KEPT_DRAWN = 64;

    /** What a mine's cutter keeps: its backfill's dirt, and ore before the rest (the rules of a mine). */
    static final Map<ItemClassRegistry.ItemClass, Integer> MINE_CARRIED = Map.of(
            ItemClassRegistry.ItemClass.DIRT, Pack.MINER_DIRT_KEPT, ItemClassRegistry.ItemClass.CRAFTING_TABLE, 1,
            // And a stack of cobblestone, its floors' and backfill's (the rules of a mine): live (field462), Alice
            // stored every block and stood a night at a cave in her level's main tunnel with none to floor it.
            ItemClassRegistry.ItemClass.COBBLESTONE, Pack.MINER_COBBLESTONE_KEPT);

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

    /**
     * What a flatten builder's load leaves at a dump: cobblestone or dirt past its most ({@link StockLevels})
     * across the surface chests, or either once every chest it could go to is full.
     */
    static java.util.Set<ItemClassRegistry.ItemClass> surplusKinds(java.util.Collection<io.github.jayessdeesea.minecraft.command.Stockpile> seen,
                                                                   Optional<io.github.jayessdeesea.minecraft.command.Position> deposit) {
        int stone = seen.stream().mapToInt(io.github.jayessdeesea.minecraft.command.Stockpile::stone).sum();
        int dirt = seen.stream().mapToInt(io.github.jayessdeesea.minecraft.command.Stockpile::dirt).sum();
        java.util.Set<ItemClassRegistry.ItemClass> kinds = java.util.EnumSet.noneOf(ItemClassRegistry.ItemClass.class);
        if (deposit.isEmpty() || stone >= io.github.jayessdeesea.minecraft.bot.supply.StockLevels.DEPOT.get(ItemClassRegistry.ItemClass.COBBLESTONE).most()) {
            kinds.add(ItemClassRegistry.ItemClass.COBBLESTONE);
        }
        if (deposit.isEmpty() || dirt >= io.github.jayessdeesea.minecraft.bot.supply.StockLevels.DEPOT.get(ItemClassRegistry.ItemClass.DIRT).most()) {
            kinds.add(ItemClassRegistry.ItemClass.DIRT);
        }
        return kinds;
    }

    /**
     * How many of {@code kind} the chests seen ({@code seen}) hold, by {@code chestStock}: seed counted apart - the
     * general stockpile's apart from each farm's own chest ({@code farmChests}), each kept to its own 256 (operator,
     * 2026-11-20) - so a load bound for a farm's chest ({@code at}) counts that chest's seed, any other the rest's.
     */
    static int stocked(java.util.Map<io.github.jayessdeesea.minecraft.bot.nav.BlockPos, java.util.Map<ItemClass, Integer>> chestStock,
                       java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> seen, ItemClass kind,
                       java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> farmChests,
                       Optional<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> at) {
        boolean toFarm = kind == ItemClass.SEEDS && at.filter(farmChests::contains).isPresent();
        return chestStock.entrySet().stream().filter(e -> seen.contains(e.getKey()))
                .filter(e -> kind != ItemClass.SEEDS || (toFarm ? e.getKey().equals(at.get()) : !farmChests.contains(e.getKey())))
                .mapToInt(e -> e.getValue().getOrDefault(kind, 0)).sum();
    }

    /** The nearest to {@code from} of the chests {@code seen}, not {@code condemned}, last seen holding {@code kind} by {@code chestStock}. */
    static Optional<io.github.jayessdeesea.minecraft.command.Position> holding(
            java.util.Map<io.github.jayessdeesea.minecraft.bot.nav.BlockPos, java.util.Map<ItemClass, Integer>> chestStock,
            java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> seen, java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> condemned,
            ItemClass kind, io.github.jayessdeesea.minecraft.command.Position from) {
        return chestStock.entrySet().stream()
                .filter(e -> seen.contains(e.getKey()) && !condemned.contains(e.getKey()) && e.getValue().getOrDefault(kind, 0) > 0)
                .map(e -> new io.github.jayessdeesea.minecraft.command.Position(e.getKey().x() + 0.5, e.getKey().y(), e.getKey().z() + 0.5))
                .min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.x() - from.x(), p.z() - from.z())));
    }
}
