package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * One soldier's pack kept from filling with what's worth least: near full, what no routine uses is
 * thrown away; and seeds beyond {@link #SEEDS_KEPT} are, a stack at a time. Every ripe wheat harvested gives seeds back, more than its
 * cell takes to sow again: live, farmers carried 200 to 580 seeds, packs full, and the wheat of a
 * harvest had no slot to go to - bread lost on the ground (backlog/salvage-and-pack.md).
 */
public final class Pack {

    private static final Logger LOG = LogManager.getLogger(Pack.class);

    /** Seeds a soldier keeps: two stacks, enough to sow the camp's farm over. */
    public static final int SEEDS_KEPT = 128;
    /**
     * Seeds it carries before it throws any: a stack over its keep. Live, thrown at the keep itself,
     * each harvest's few seeds were thrown on their own, one stack of 1 to 6 after another, every
     * throw restarting the errand the soldier was on.
     */
    static final int THROW_FROM = SEEDS_KEPT + 64;
    /** Ticks to wait after a throw for the server's resent inventory, so a stack isn't thrown twice. */
    static final int SETTLE_TICKS = 20;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private int settle;
    private long ticks;
    /** How many of each item it had left after throwing some away, by item id - to see one picked back up. */
    private final java.util.Map<Integer, Integer> leftAfter = new java.util.HashMap<>();
    /** When it picked back up an item it threw away, by item id: not thrown again for a while. */
    private final java.util.Map<Integer, Long> pickedBack = new java.util.HashMap<>();
    /**
     * Ticks an item picked back up after it was thrown away isn't thrown again: a dropped item's life
     * on the ground (5 minutes). Live, a soldier standing still picked up what it threw as soon as it
     * could, and threw it 454 times.
     */
    static final long THROWN_FOR = 6000;

    public Pack(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    /** Each tick: with its hands {@code free}, throws away a stack of seeds it doesn't need. */
    /** Stacks thrown away so far: a caller marks the ground they lie on, so nobody fetches them back. */
    private long thrown;

    /** How many stacks it has thrown away. */
    public synchronized long thrown() {
        return thrown;
    }

    public synchronized void tick(boolean free) {
        tick(free, DIRT_KEPT, COBBLESTONE_KEPT);
    }

    /**
     * As {@link #tick(boolean)}, keeping {@code dirtKept} dirt and {@code cobblestoneKept} cobblestone
     * with its pack full - a miner's backfill only (the rules of a mine: the ore comes first in the pack).
     */
    public synchronized void tick(boolean free, int dirtKept, int cobblestoneKept) {
        tick(free, dirtKept, cobblestoneKept, false);
    }

    /**
     * As {@link #tick(boolean, int, int)}, and with {@code copperStocked} - the depot holding its most
     * of raw copper (StockLevels) - raw copper last, after the cobblestone: never ore over dirt.
     */
    public synchronized void tick(boolean free, int dirtKept, int cobblestoneKept, boolean copperStocked) {
        java.util.Map<ItemClass, Integer> kept = new java.util.LinkedHashMap<>();
        kept.put(ItemClass.DIRT, dirtKept);
        kept.put(ItemClass.SEEDS, SEEDS_KEPT);
        kept.put(ItemClass.COBBLESTONE, cobblestoneKept);
        if (copperStocked) {
            kept.put(ItemClass.RAW_COPPER, 0);
        }
        tick(free, FULL_AT, kept);
    }

    /**
     * As {@link #tick(boolean, int, int)}, shedding from {@code fullAt} free slots, and keeping of each kind
     * in {@code kept} that many - the rest thrown, the kinds in order, worth least first. A keep builder's
     * pack sheds before its load trip's mark: live (field358), builders walked to the depot with a few
     * dirt, three slots of seeds, saplings, string, flint and five crafting tables, stored a load of
     * cobblestone and came back with the rest (operator, 2026-11-17: fill a slot, dump it when
     * something better comes along, and optimize the travel away).
     */
    public synchronized void tick(boolean free, int fullAt, java.util.Map<ItemClass, Integer> kept) {
        ticks++;
        leftAfter.entrySet().removeIf(e -> {
            if (countOf(e.getKey()) > e.getValue()) {
                pickedBack.put(e.getKey(), ticks);
                return true;
            }
            return false;
        });
        if (settle > 0) {
            settle--;
            return;
        }
        if (!free) {
            return;
        }
        // The hotbar as a player keeps it (Hotbar): the sword, the pickaxe, food, torches in their
        // places, nothing there that isn't switched to in a hurry. A swap at a time.
        Optional<Hotbar.Swap> swap = Hotbar.next(inventory);
        if (swap.isPresent()) {
            session.swapWithHotbar(0, 0, swap.get().slot(), swap.get().hotbar()); // stale state: the server resends the inventory
            settle = SETTLE_TICKS;
            return;
        }
        // Part-stacks of one item merged into one: two slots for a few blocks each is a slot wasted
        // (operator, 2026-11-15).
        Optional<int[]> merge = mergeable(inventory, (a, b) -> ticks - mergeTried.getOrDefault(inventory.itemAt(a), Long.MIN_VALUE / 2) < THROWN_FOR);
        if (merge.isPresent()) {
            int from = merge.get()[0], onto = merge.get()[1];
            LOG.info("{} puts its {} of item {} onto the {} in slot {}", name, inventory.countAt(from), inventory.itemAt(from),
                    inventory.countAt(onto), onto);
            mergeTried.put(inventory.itemAt(from), ticks);
            // Picked up, put onto the other - the stack takes what it can - and what's left put back.
            session.click(0, 0, from, false); // stale state: the server resends the inventory
            session.click(0, 0, onto, false);
            session.click(0, 0, from, false);
            settle = SETTLE_TICKS;
            return;
        }
        // A pack near full sheds what no routine uses - rotten flesh, bones, gravel - before seeds.
        Optional<Integer> junk = freeSlots(inventory) <= fullAt
                ? unused(inventory, item -> ticks - pickedBack.getOrDefault(item, Long.MIN_VALUE / 2) < THROWN_FOR) : Optional.empty();
        if (junk.isPresent()) {
            int slot = junk.get();
            LOG.info("{} throws away {} of item {}, which nothing it does uses - its pack is nearly full", name,
                    inventory.countAt(slot), inventory.itemAt(slot));
            leftAfter.put(inventory.itemAt(slot), countOf(inventory.itemAt(slot)) - inventory.countAt(slot));
            session.throwStack(0, 0, slot); // stale state: the server resends the inventory
            thrown++;
            settle = SETTLE_TICKS;
            return;
        }
        // Then the bulk it carries beyond what its work wants, worth least first: dirt, then seeds,
        // then cobblestone. Never ore, ingots or tools: live, a soldier with twelve raw iron had 586
        // dirt in its pack and no slot for the pickaxe it was crafting (operator, 2026-11-15:
        // almost never dirt over iron).
        if (freeSlots(inventory) <= fullAt) {
            Optional<Integer> bulk = Optional.empty();
            for (java.util.Map.Entry<ItemClass, Integer> keep : kept.entrySet()) {
                bulk = surplus(inventory, keep.getKey(), keep.getValue());
                if (bulk.isPresent()) {
                    break;
                }
            }
            // Not one picked back up lately: live, thrown where the soldier stood, it was picked straight
            // back up and thrown again, 56 times in four minutes.
            bulk = bulk.filter(slot -> ticks - pickedBack.getOrDefault(inventory.itemAt(slot), Long.MIN_VALUE / 2) >= THROWN_FOR);
            if (bulk.isPresent()) {
                int slot = bulk.get();
                LOG.info("{} throws away {} of item {} - its pack is full, and it's worth least of what it carries", name,
                        inventory.countAt(slot), inventory.itemAt(slot));
                leftAfter.put(inventory.itemAt(slot), countOf(inventory.itemAt(slot)) - inventory.countAt(slot));
                session.throwStack(0, 0, slot); // stale state: the server resends the inventory
            thrown++;
                settle = SETTLE_TICKS;
                return;
            }
        }
        if (inventory.count(ItemClass.SEEDS) < THROW_FROM) {
            return;
        }
        // Not seeds picked back up lately, as the bulk above: live (field617), Pete stood farming and threw 62 seeds every
        // 3 to 6 s, 22 times, each throw picked back up where it fell.
        surplusSeeds(inventory).filter(slot -> ticks - pickedBack.getOrDefault(inventory.itemAt(slot), Long.MIN_VALUE / 2) >= THROWN_FOR)
                .ifPresent(slot -> {
            LOG.info("{} throws away {} seeds, keeping {} - its pack is for what's worth more", name, inventory.countAt(slot),
                    inventory.count(ItemClass.SEEDS) - inventory.countAt(slot));
            leftAfter.put(inventory.itemAt(slot), countOf(inventory.itemAt(slot)) - inventory.countAt(slot));
            session.throwStack(0, 0, slot); // stale state: the server resends the inventory
            thrown++;
            settle = SETTLE_TICKS;
        });
    }

    /** How many of item {@code item} it carries, every stack. */
    private int countOf(int item) {
        int count = 0;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (inventory.itemAt(slot) == item) {
                count += inventory.countAt(slot);
            }
        }
        return count;
    }

    /**
     * When a merge was last tried, by item: an item merged once isn't again for a while - one the
     * server didn't merge, tried pair after pair, would hold off the rest of the pack's keeping.
     */
    private final java.util.Map<Integer, Long> mergeTried = new java.util.HashMap<>();

    /** What stacks to 64 and merges as one: what a soldier gathers, mines, smelts and makes in bulk. */
    static final java.util.Set<ItemClass> STACKS = java.util.EnumSet.of(ItemClass.DIRT, ItemClass.COBBLESTONE, ItemClass.LOG,
            ItemClass.PLANKS, ItemClass.STICK, ItemClass.SEEDS, ItemClass.WHEAT, ItemClass.COAL, ItemClass.RAW_IRON,
            ItemClass.RAW_COPPER, ItemClass.IRON_INGOT, ItemClass.COPPER_INGOT, ItemClass.TORCH, ItemClass.DIAMOND,
            ItemClass.STRING, ItemClass.FLINT, ItemClass.FEATHER, ItemClass.LADDER);
    static final int STACK = 64;

    /**
     * Two part-stacks of one item that merge - the smaller, put onto the larger - or empty with
     * none, passing over the pairs {@code triedLately}. Only what's in {@link #STACKS}: a tool put
     * onto a tool swaps the two, and would be clicked for ever.
     */
    static Optional<int[]> mergeable(Inventory inventory, java.util.function.BiPredicate<Integer, Integer> triedLately) {
        for (int a = Inventory.MAIN_FIRST; a <= Inventory.HOTBAR_LAST; a++) {
            int item = inventory.itemAt(a);
            if (item < 0 || inventory.countAt(a) >= STACK || ItemClassRegistry.classOf(item).filter(STACKS::contains).isEmpty()) {
                continue;
            }
            for (int b = a + 1; b <= Inventory.HOTBAR_LAST; b++) {
                if (inventory.itemAt(b) != item || inventory.countAt(b) >= STACK) {
                    continue;
                }
                int from = inventory.countAt(a) <= inventory.countAt(b) ? a : b;
                int onto = from == a ? b : a;
                if (!triedLately.test(from, onto)) {
                    return Optional.of(new int[]{from, onto});
                }
            }
        }
        return Optional.empty();
    }

    /** Free slots at or under which the pack counts as full: room kept for a harvest's wheat and seeds. */
    public static final int FULL_AT = 2;

    /** Empty slots in the main inventory and hotbar. */
    public static int freeSlots(Inventory inventory) {
        int free = 0;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (inventory.itemAt(slot) < 0) {
                free++;
            }
        }
        return free;
    }

    /**
     * The smallest stack of an item no routine uses - one of no {@link ItemClass} - never the one in
     * hand: live, packs full of rotten flesh, bones, gravel and phantom membranes had no slot for a
     * harvest's wheat. The operator's: what's worth least goes first, a torch kept over rotten flesh.
     */
    static Optional<Integer> unused(Inventory inventory) {
        return unused(inventory, item -> false);
    }

    /** As {@link #unused(Inventory)}, passing over the items {@code recentlyThrown}. */
    static Optional<Integer> unused(Inventory inventory, java.util.function.IntPredicate recentlyThrown) {
        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        Integer best = null;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            int item = inventory.itemAt(slot);
            if (slot != held && item >= 0 && ItemClassRegistry.classOf(item).isEmpty() && !recentlyThrown.test(item)
                    && (best == null || inventory.countAt(slot) < inventory.countAt(best))) {
                best = slot;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Dirt a soldier keeps with its pack full: two stacks, to pillar, bridge and fill with. */
    public static final int DIRT_KEPT = 128;
    /** Cobblestone it keeps with its pack full: five stacks, a wall's or a stretch of slab's load. */
    public static final int COBBLESTONE_KEPT = 320;
    /**
     * What a mine's cutter keeps with its pack full: a stack of cobblestone and half of dirt, to floor
     * a gap and fill a vein's holes - never carried at the cost of a slot for ore (the rules of a mine).
     */
    public static final int MINER_COBBLESTONE_KEPT = 64, MINER_DIRT_KEPT = 32;

    /**
     * A slot of {@code bulk} that can go and leave {@code kept} or more: the smallest such stack,
     * never the one in hand.
     */
    static Optional<Integer> surplus(Inventory inventory, ItemClass bulk, int kept) {
        int carried = inventory.count(bulk);
        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        Integer best = null;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (slot != held && ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(bulk))
                    && carried - inventory.countAt(slot) >= kept
                    && (best == null || inventory.countAt(slot) < inventory.countAt(best))) {
                best = slot;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * A slot of seeds that can go and leave {@link #SEEDS_KEPT} or more: the smallest such stack,
     * never the one in hand - a farmer sowing holds its seeds.
     */
    static Optional<Integer> surplusSeeds(Inventory inventory) {
        int seeds = inventory.count(ItemClass.SEEDS);
        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        Integer best = null;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            if (slot != held && ItemClassRegistry.classOf(inventory.itemAt(slot)).equals(Optional.of(ItemClass.SEEDS))
                    && seeds - inventory.countAt(slot) >= SEEDS_KEPT
                    && (best == null || inventory.countAt(slot) < inventory.countAt(best))) {
                best = slot;
            }
        }
        return Optional.ofNullable(best);
    }
}
