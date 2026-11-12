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
    public synchronized void tick(boolean free) {
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
        // A pack near full sheds what no routine uses - rotten flesh, bones, gravel - before seeds.
        Optional<Integer> junk = freeSlots(inventory) <= FULL_AT
                ? unused(inventory, item -> ticks - pickedBack.getOrDefault(item, Long.MIN_VALUE / 2) < THROWN_FOR) : Optional.empty();
        if (junk.isPresent()) {
            int slot = junk.get();
            LOG.info("{} throws away {} of item {}, which nothing it does uses - its pack is nearly full", name,
                    inventory.countAt(slot), inventory.itemAt(slot));
            leftAfter.put(inventory.itemAt(slot), countOf(inventory.itemAt(slot)) - inventory.countAt(slot));
            session.throwStack(0, 0, slot); // stale state: the server resends the inventory
            settle = SETTLE_TICKS;
            return;
        }
        if (inventory.count(ItemClass.SEEDS) < THROW_FROM) {
            return;
        }
        surplusSeeds(inventory).ifPresent(slot -> {
            LOG.info("{} throws away {} seeds, keeping {} - its pack is for what's worth more", name, inventory.countAt(slot),
                    inventory.count(ItemClass.SEEDS) - inventory.countAt(slot));
            session.throwStack(0, 0, slot); // stale state: the server resends the inventory
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

    /** Free slots at or under which the pack counts as full: room kept for a harvest's wheat and seeds. */
    public static final int FULL_AT = 2;

    /** Empty slots in the main inventory and hotbar. */
    static int freeSlots(Inventory inventory) {
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
