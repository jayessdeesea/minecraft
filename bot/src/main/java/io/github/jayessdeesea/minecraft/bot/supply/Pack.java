package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * One soldier's pack kept from filling with what's worth least: seeds beyond {@link #SEEDS_KEPT}
 * are thrown away, a stack at a time. Every ripe wheat harvested gives seeds back, more than its
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

    public Pack(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    /** Each tick: with its hands {@code free}, throws away a stack of seeds it doesn't need. */
    public synchronized void tick(boolean free) {
        if (settle > 0) {
            settle--;
            return;
        }
        if (!free || inventory.count(ItemClass.SEEDS) < THROW_FROM) {
            return;
        }
        surplusSeeds(inventory).ifPresent(slot -> {
            LOG.info("{} throws away {} seeds, keeping {} - its pack is for what's worth more", name, inventory.countAt(slot),
                    inventory.count(ItemClass.SEEDS) - inventory.countAt(slot));
            session.throwStack(0, 0, slot); // stale state: the server resends the inventory
            settle = SETTLE_TICKS;
        });
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
