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
 * chop the nearest log within {@link Chopper#SEARCH_RADIUS} of the objective, again and again, until
 * it carries {@link #CARRY_LOGS}. Wood only, so far. Ticked by the soldier's tick loop; synchronized
 * like the other routines in this package.
 *
 * <p>Gathering stops - quietly, the logs kept - when it's no longer wanted (contact, a new
 * objective without a gather). When there's nothing left in reach it waits {@link #RETRY_TICKS}
 * before looking again, rather than searching every tick.
 */
public final class Gatherer {

    private static final Logger LOG = LogManager.getLogger(Gatherer.class);

    /** What one soldier carries before it stops: a quarter stack, a load it can still fight with. */
    static final int CARRY_LOGS = 16;
    static final int RETRY_TICKS = 600;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;

    private Chopper chopper;
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
        return chopper != null;
    }

    /** Whether the woods around the objective are worked out: the last search found nothing in reach. */
    public synchronized boolean exhausted() {
        return exhausted;
    }

    /** Where the soldier should be standing while it gathers, if anywhere. */
    public synchronized Optional<Position> standAt() {
        return chopper == null ? Optional.empty() : Optional.of(chopper.standAt());
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
        if (inventory.count(ItemClass.LOG) >= CARRY_LOGS) {
            if (!reportedFull) {
                LOG.info("{} carries {} logs - done gathering", name, inventory.count(ItemClass.LOG));
                reportedFull = true;
            }
            stop();
            return;
        }
        reportedFull = false;
        if (chopper == null) {
            if (retry == 0) {
                next(world, around);
            }
            return;
        }
        switch (chopper.tick(soldier, world)) {
            case Chopper.Progress.Working working -> {
            }
            case Chopper.Progress.Done done -> {
                chopper.release();
                next(world, around);
            }
            case Chopper.Progress.Failed(String why) -> {
                LOG.info("{} leaves that tree - {}", name, why);
                avoid.add(chopper.tree().log());
                avoid.add(chopper.tree().stand());
                chopper.release();
                chopper = null;
                retry = RETRY_TICKS / 10; // try another tree soon
            }
        }
    }

    /** Stops gathering: frees its claims and takes its weapon back in hand. */
    private void stop() {
        if (chopper == null) {
            return;
        }
        chopper.release();
        chopper = null;
        for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
            if (step instanceof Quartermaster.Step.Hold(int hotbarSlot)) {
                session.selectHotbarSlot(hotbarSlot);
                inventory.selectHotbar(hotbarSlot);
            } else if (step instanceof Quartermaster.Step.BringToHand(int slot, int hotbarSlot)) {
                session.swapWithHotbar(0, 0, slot, hotbarSlot);
            }
        }
    }

    private void next(WorldView world, Position around) {
        Set<BlockPos> excluded = new java.util.HashSet<>(claims);
        excluded.addAll(avoid);
        Optional<Chopper.Tree> tree = Chopper.findTree(world, BlockPos.containing(around.x(), around.y(), around.z()), excluded);
        if (tree.isEmpty()) {
            if (chopper != null || retry == 0) {
                LOG.info("{}: no tree within {} blocks of the objective", name, Chopper.SEARCH_RADIUS);
            }
            chopper = null;
            retry = RETRY_TICKS;
            exhausted = true;
            return;
        }
        exhausted = false;
        chopper = new Chopper(session, inventory, claims, tree.get());
    }
}
