package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.Set;

/**
 * One soldier gathering a material around its squad's objective (docs/military-doctrine.md §12):
 * dig the nearest log - or stone, with a pickaxe it makes first if it has none ({@link Woodcraft}) -
 * within {@link Digger#SEARCH_RADIUS} of the objective, again and again, until it carries its load. Ticked by the soldier's tick loop; synchronized
 * like the other routines in this package.
 *
 * <p>Gathering stops - quietly, the logs kept - when it's no longer wanted (contact, a new
 * objective without a gather). When there's nothing left in reach it waits {@link #RETRY_TICKS}
 * before looking again, rather than searching every tick.
 */
public final class Gatherer {

    private static final Logger LOG = LogManager.getLogger(Gatherer.class);

    /** What one soldier carries before it stops: loads it can still fight with. */
    public static final int CARRY_LOGS = 16;
    public static final int CARRY_STONE = 32;
    static final int RETRY_TICKS = 600;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;

    private Digger digger;
    /** Trees this soldier failed at (out of reach, or the log wouldn't break) - not tried again. */
    private final Set<BlockPos> avoid = new java.util.HashSet<>();
    private int retry;
    private boolean reportedFull;
    private boolean exhausted;

    public Gatherer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return digger != null;
    }

    /** Whether the woods around the objective are worked out: the last search found nothing in reach. */
    public synchronized boolean exhausted() {
        return exhausted;
    }

    /** Where the soldier should be standing while it gathers, if anywhere. */
    public synchronized Optional<Position> standAt() {
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /**
     * Each tick: gathers {@code resource} around {@code around} while {@code wanted}; empty
     * {@code resource} means the squad isn't gathering.
     */
    public synchronized void tick(Position soldier, WorldView world, Optional<Resource> resource, Position around,
                                  boolean wanted) {
        if (retry > 0) {
            retry--;
        }
        if (resource.isEmpty() || !wanted) {
            stop();
            exhausted = false;
            return;
        }
        ItemClass item = resource.get() == Resource.WOOD ? ItemClass.LOG : ItemClass.COBBLESTONE;
        int load = resource.get() == Resource.WOOD ? CARRY_LOGS : CARRY_STONE;
        if (resource.get() == Resource.STONE && inventory.slotOf(ItemClass.PICKAXE) < 0) {
            stop(); // a pickaxe first - Woodcraft makes one
            return;
        }
        if (inventory.count(item) >= load) {
            if (!reportedFull) {
                LOG.info("{} carries {} {} - done gathering", name, inventory.count(item), item);
                reportedFull = true;
            }
            stop();
            return;
        }
        reportedFull = false;
        Digger.Target target = resource.get() == Resource.WOOD ? Digger.Target.LOG : Digger.Target.STONE;
        if (digger == null) {
            if (retry == 0) {
                next(world, around, target);
            }
            return;
        }
        switch (digger.tick(soldier, world)) {
            case Digger.Progress.Working working -> {
            }
            case Digger.Progress.Done done -> {
                digger.release();
                next(world, around, target);
            }
            case Digger.Progress.Failed(String why) -> {
                LOG.info("{} leaves that spot - {}", name, why);
                avoid.add(digger.spot().block());
                avoid.add(digger.spot().stand());
                digger.release();
                digger = null;
                retry = RETRY_TICKS / 10; // try another tree soon
            }
        }
    }

    /** Stops gathering: frees its claims and takes its weapon back in hand. */
    private void stop() {
        if (digger == null) {
            return;
        }
        digger.release();
        digger = null;
        for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
            if (step instanceof Quartermaster.Step.Hold(int hotbarSlot)) {
                session.selectHotbarSlot(hotbarSlot);
                inventory.selectHotbar(hotbarSlot);
            } else if (step instanceof Quartermaster.Step.BringToHand(int slot, int hotbarSlot)) {
                session.swapWithHotbar(0, 0, slot, hotbarSlot);
            }
        }
    }

    private void next(WorldView world, Position around, Digger.Target target) {
        Set<BlockPos> excluded = new java.util.HashSet<>(claims);
        excluded.addAll(avoid);
        Optional<Digger.Spot> spot = Digger.findSpot(world, BlockPos.containing(around.x(), around.y(), around.z()), excluded, target);
        if (spot.isEmpty()) {
            if (digger != null || retry == 0) {
                LOG.info("{}: no {} within {} blocks of the objective", name, target, Digger.SEARCH_RADIUS);
            }
            digger = null;
            retry = RETRY_TICKS;
            exhausted = true;
            return;
        }
        exhausted = false;
        digger = new Digger(session, inventory, claims, spot.get());
    }
}
