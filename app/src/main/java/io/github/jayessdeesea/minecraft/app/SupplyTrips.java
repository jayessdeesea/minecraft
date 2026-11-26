package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.craft.LoadPlan;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.supply.Depositor;
import io.github.jayessdeesea.minecraft.bot.supply.MetalPool;
import io.github.jayessdeesea.minecraft.bot.supply.StockLevels;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.DutyOrder;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.Tasking;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;

/**
 * When a soldier fits a chest, dumps, draws each kind from a chest, or sets out to store what it carries - the
 * decisions the depositor's turn makes, over values the soldier hands in. Each answers a question and acts on
 * nothing: the soldier owns the depositor, its orders and its logs, and puts the answer to them.
 */
final class SupplyTrips {

    private SupplyTrips() {
    }

    /** A chest fitted for the store first: the workshop's own is fitted at its stand when the pack is full, the depot's once a builder has its logs. */
    static boolean depotLogs(boolean holdsFlattenWork, Inventory inventory) {
        return holdsFlattenWork && inventory.count(ItemClass.LOG) >= SoldierRunner.DEPOT_LOGS;
    }

    /** A producer with its yield and no dump yet, the camp's chest not standing (field503: 83 harvests, nothing stockpiled). */
    static boolean undumped(Optional<DutyOrder> duty, Inventory inventory) {
        return duty.filter(o -> o.dump().isEmpty()).isPresent() && inventory.count(ItemClass.WHEAT) + inventory.count(ItemClass.LOG) >= Subsistence.BREAD_WHEAT;
    }

    /**
     * Whether the workshop's chest is to be fitted now: at a mine, for ore, with a chest carried or logs to spare; elsewhere
     * for a full load, the depot's logs, a surplus of food, ore for the pool, or a producer with no dump. Not with a supply
     * point, a chest standing or barred, a contact, or a keep to build. {@code chestStands} and {@code barred} are asked last.
     */
    static boolean fitStore(boolean atMine, boolean stashOre, Inventory inventory, boolean fullLoad, boolean depotLogs, boolean poolOre,
                            boolean undumped, boolean supplyPointEmpty, Optional<Workshop> shop, BooleanSupplier chestStands,
                            boolean threatened, boolean noFort, BooleanSupplier barred) {
        return (atMine ? stashOre && (inventory.count(ItemClass.CHEST) > 0 || inventory.count(ItemClass.LOG) >= SoldierRunner.STASH_LOGS)
                : fullLoad || depotLogs || Depositor.surplusFood(inventory) || poolOre || undumped) && supplyPointEmpty && shop.isPresent() && shop.get().chest().isPresent()
                && !chestStands.getAsBoolean() && !threatened && noFort && !barred.getAsBoolean();
    }

    /** A full load with a kind held past what the depositor keeps of it, among the {@code surplus} kinds. */
    static boolean dumpNow(boolean fullLoad, Set<ItemClass> surplus, Inventory inventory) {
        return fullLoad && surplus.stream().anyMatch(c -> inventory.count(c) > Depositor.KEEP);
    }

    /** One draw from a chest: where, of what, how much to carry, and whether the hands are free to go. */
    record Draw(Optional<Position> chest, ItemClass kind, int count, boolean go) {
    }

    /**
     * What the draw ladder reads. {@code safe} is hands free and no contact, {@code safeInBase} that and no keep to build;
     * {@code holding} and {@code metalChest} are the nearest chest last seen holding a kind, to where the soldier stands.
     * The suppliers are the questions that cost something to ask, so each is asked where the ladder reaches it.
     */
    record DrawCase(Depositor depositor, DutyBook dutyBook, String name, Inventory inventory, Kit carried, boolean safe, boolean safeInBase,
                    Function<ItemClass, Optional<Position>> holding, Function<ItemClass, Optional<Position>> metalChest,
                    BooleanSupplier soilFilling, Optional<SoldierErrands.Makings> makings, Optional<SoldierRunner.Unstash> unstash,
                    BooleanSupplier climbDrawing, BooleanSupplier drawingStone, Optional<Position> stockpile,
                    BooleanSupplier drawsLogs, Optional<Position> logsAt, BooleanSupplier drawsStone, BooleanSupplier drawsFood,
                    Optional<MetalPool.Draw> metalDraw) {
    }

    /**
     * The draw to make, the first of these that holds, or none when the soldier is to store: bread when it has no rations
     * and a chest holds some - before metal and stone, from the nearest chest not only its own depot's (live, field568,
     * a soldier drew iron and cobblestone all run and was shot dead at 1 health) - then the dirt of its fill, its sowing's
     * seeds, planting's saplings or scouting's food, a mine's stash, stone for the wall, logs, stone, food, the baker's
     * wheat, a bed's string, and the metal pool's ore.
     */
    static Optional<Draw> draw(DrawCase c) {
        Depositor depositor = c.depositor();
        Inventory inventory = c.inventory();
        if (c.carried() != null && Subsistence.short_(c.carried().rations()) && c.holding().apply(ItemClass.FOOD).isPresent()) {
            return Optional.of(new Draw(c.holding().apply(ItemClass.FOOD), ItemClass.FOOD, Subsistence.RESUPPLY, c.safe()));
        } else if (c.soilFilling().getAsBoolean() && inventory.count(ItemClass.DIRT) < Tasking.LOW && c.holding().apply(ItemClass.DIRT).isPresent()) { // its fill's dirt, drawn from the nearest chest holding some (operator, 2026-11-22)
            return Optional.of(new Draw(c.holding().apply(ItemClass.DIRT), ItemClass.DIRT, Tasking.LOAD, c.safe()));
        } else if (c.makings().isPresent()) { // its sowing's seeds, its planting's saplings, its scouting's food, drawn (SoldierErrands.makingsDrawn)
            return Optional.of(new Draw(c.holding().apply(c.makings().get().kind()), c.makings().get().kind(), c.makings().get().count(), c.safe()));
        } else if (c.unstash().isPresent()) {
            return Optional.of(new Draw(Optional.of(c.unstash().get().chest()), c.unstash().get().ore(), c.unstash().get().carry(), c.safe()));
        } else if (c.climbDrawing().getAsBoolean()) {
            // From the nearest chest holding stone, as metal and logs are drawn.
            return Optional.of(new Draw(c.metalChest().apply(ItemClass.COBBLESTONE).or(() -> c.stockpile()), ItemClass.COBBLESTONE,
                    SoldierRunner.WALL_BLOCKS_LOAD, c.safe()));
        } else if (c.drawingStone().getAsBoolean()) {
            return Optional.of(new Draw(c.stockpile(), ItemClass.COBBLESTONE, SoldierRunner.WALL_BLOCKS_LOAD, c.safe()));
        } else if (c.drawsLogs().getAsBoolean() || depositor.drawing(ItemClass.LOG)) {
            return Optional.of(new Draw(c.logsAt().or(depositor::chestAt), ItemClass.LOG, SoldierErrands.DRAW_LOGS, c.safe() && c.drawsLogs().getAsBoolean()));
        } else if (c.drawsStone().getAsBoolean()) {
            return Optional.of(new Draw(c.metalChest().apply(ItemClass.COBBLESTONE).or(() -> c.stockpile()), ItemClass.COBBLESTONE,
                    SoldierErrands.DRAW_STONE, c.safe()));
        } else if (c.drawsFood().getAsBoolean() || depositor.drawing(ItemClass.FOOD)) {
            return Optional.of(new Draw(c.stockpile(), ItemClass.FOOD, Subsistence.RESUPPLY, c.safe() && c.drawsFood().getAsBoolean()));
        } else if (depositor.drawing(ItemClass.WHEAT) || c.dutyBook().drawsWheat(c.name(), inventory.count(ItemClass.WHEAT))) {
            // The baker's wheat: from the chest its duty order names, when and how much by its batch (command.BakerDuty: the squad leader's, not the soldier's).
            return Optional.of(new Draw(c.dutyBook().of(c.name()).flatMap(DutyOrder::source).or(depositor::chestAt), ItemClass.WHEAT,
                    c.dutyBook().wheatDrawnTo(c.name()), c.safeInBase() && c.dutyBook().drawsWheat(c.name(), inventory.count(ItemClass.WHEAT))));
        } else if (depositor.drawing(ItemClass.STRING) || SoldierErrands.bedString(inventory) > 0
                && c.holding().apply(ItemClass.STRING).isPresent()) {
            // A bed's string from the depot, for a soldier without one (operator, 2026-11-18: beds, for a
            // safe respawn): the night's spiders' string is stored there, four to a wool.
            return Optional.of(new Draw(c.holding().apply(ItemClass.STRING).or(depositor::chestAt), ItemClass.STRING,
                    inventory.count(ItemClass.STRING) + SoldierErrands.bedString(inventory), c.safeInBase() && SoldierErrands.bedString(inventory) > 0));
        } else if (c.metalDraw().isPresent() || MetalPool.METALS.stream().anyMatch(depositor::drawing)) {
            ItemClass ore = c.metalDraw().map(MetalPool.Draw::ore)
                    .orElseGet(() -> MetalPool.METALS.stream().filter(depositor::drawing).findFirst().orElseThrow());
            Optional<Position> holding = c.metalDraw().flatMap(d -> c.metalChest().apply(d.ore())).or(depositor::chestAt);
            return Optional.of(new Draw(holding, ore, c.metalDraw().map(MetalPool.Draw::carry).orElse(inventory.count(ore)),
                    c.safeInBase() && c.metalDraw().isPresent()));
        }
        return Optional.empty();
    }

    /** The kinds put in the depot with a load: the metal pool's stock, and a mine's stash when the soldier works one, minus what the stores hold to their most (StockLevels). */
    static Set<ItemClass> pooled(Set<ItemClass> metalStock, boolean stashOre, ToIntFunction<ItemClass> stockedAnywhere) {
        Set<ItemClass> pooled = new HashSet<>(metalStock);
        pooled.removeIf(kind -> StockLevels.atMost(kind, stockedAnywhere)); // copper past 256 (StockLevels)
        if (stashOre) {
            pooled.addAll(SoldierRunner.STASHED);
        }
        return pooled;
    }

    /** A trip of its own once what it carries past its work's load is a few slots (LoadPlan: field505, Gus 11 shovels, Dana 5 swords). */
    static boolean surplusTrip(boolean upgradeAllowed, Optional<LoadPlan> plan, Inventory inventory) {
        return upgradeAllowed && plan.map(p -> p.surplus(inventory).size() >= SoldierRunner.SURPLUS_TRIP).orElse(false);
    }

    /** What the pack keeps before the rest is stored, by the work the soldier is on. */
    static Map<ItemClass, Integer> packKept(boolean planting, boolean flatteningOrPaving, boolean cutting, boolean harvester) {
        return planting ? SoldierErrands.PLANTER_CARRIED : flatteningOrPaving ? SoldierErrands.KEEP_PACK
                : cutting ? SoldierErrands.MINE_CARRIED
                : harvester ? SoldierErrands.HARVESTER_CARRIED : SoldierErrands.CARRIED;
    }

    /** Not up out of the mine by night for a load - the stash's ore goes in below; the rest waits for day (live, the night shift's loads were for the camp's chest 85 blocks off and up the stairs, into the dark). */
    static boolean downForTheNight(Integer nightFloor, double y, int mineBelow, boolean stashOre) {
        return nightFloor != null && y < nightFloor - mineBelow && !stashOre;
    }

    /**
     * Whether the soldier sets out to store this tick. The depositor is asked last, in the order of the list: a full load, ore
     * for the pool or the stash, a surplus trip, past halfway to a chest, or - for a soldier that stocks - a reserve run short or
     * surplus food it stores. Never in contact, with a keep to build, down a mine for the night, or while the recovery drill has
     * it: its way out is its work, as for the supply run (live, field424, a soldier on his stair up set off for the depot 49
     * times in ten minutes, each trip taking his hands from the stair and dropped as no longer wanted).
     */
    static boolean tripGoes(Depositor depositor, boolean free, boolean fullLoad, boolean poolOre, boolean stashOre, boolean surplusTrip,
                            BooleanSupplier stocks, boolean stockTrip, boolean breadWanted, boolean threatened, boolean noFort,
                            boolean downForTheNight, Set<String> recovering, String name) {
        return free && (fullLoad || poolOre || stashOre || surplusTrip || depositor.pastHalfway()
                || stocks.getAsBoolean() && (stockTrip || breadWanted && depositor.surplusFood())) && !threatened && noFort && !downForTheNight && !recovering.contains(name);
    }

    /** What the stores hold of {@code kind}, for the slots to store: none of a kind they're capped on, no sapling unless it plants. */
    static int stored(ItemClass kind, Optional<Stockpile> stores, boolean planting, IntSupplier stockedAtDeposit) {
        return SoldierErrands.capped(kind, stores) ? Integer.MAX_VALUE : kind == ItemClass.SAPLING && !planting ? 0 : stockedAtDeposit.getAsInt();
    }

    /**
     * The slots a trip puts in a chest: its work's surplus, and - to a depot - what the pack keeps of its kinds past what the
     * depot holds (saplings put back whatever's held, operator 2026-11-25). A paver's cobblestone is its work's: never put in
     * (in the squad simulation one drew a chest's 627 and put 625 back, round and round).
     */
    static List<Integer> slots(boolean digger, boolean planting, boolean paving, boolean toDepot, Inventory inv, Optional<LoadPlan> plan,
                               Map<ItemClass, Integer> packKept, ToIntFunction<ItemClass> stored) {
        List<Integer> slots = SoldierErrands.tripSlots(digger, planting, inv, plan.map(p -> p.surplus(inv)).orElse(List.of()));
        if (toDepot) {
            StockLevels.toStore(inv, packKept, stored).stream().filter(s -> !slots.contains(s)).forEach(slots::add);
        }
        return paving ? slots.stream().filter(sl -> ItemClassRegistry.classOf(inv.itemAt(sl)).filter(ItemClass.COBBLESTONE::equals).isEmpty()).toList() : slots;
    }
}
