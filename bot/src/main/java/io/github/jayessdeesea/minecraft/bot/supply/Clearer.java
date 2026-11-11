package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier clearing its camp ({@link Clearing}): the sky over it first - the lowest log or
 * leaves it can reach - then its high ground, a block of dirt at a time; one {@link Digger} job at
 * a time, keeping what it cuts. Soldiers clearing one camp share
 * {@code claims}, so each takes a different block. A block it couldn't cut is left longer each time
 * ({@link Setbacks}).
 */
public final class Clearer {

    private static final Logger LOG = LogManager.getLogger(Clearer.class);

    /** How long it waits after a cut fails, or when there's nothing in reach, before looking again. */
    static final int COOLDOWN_TICKS = 100;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;
    private final Setbacks<BlockPos> failed = new Setbacks<>(600, 12_000);
    /** How far past the furthest block the flood for stands looks, and how many cells it expands. */
    static final int REACH_MARGIN = 6, REACH_BUDGET = 40_000;

    private Digger digger;
    private long ticks;
    private int cooldown;

    public Clearer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return digger != null;
    }

    /** Where the soldier should be standing for the cut under way. */
    public synchronized Optional<Position> standAt() {
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /** Why it needs its squad leader's help, if it's out of ways of its own ({@link Setbacks#stuck}). */
    public synchronized Optional<String> stuck() {
        return failed.stuck().map(s -> "can't clear the camp: " + s);
    }

    /** Each tick: cuts the {@code cover} over the camp while {@code free}; anything else stops the cut under way. */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> cover, boolean free) {
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (cover.isEmpty()) {
            failed.noWork();
        }
        if (!free || cover.isEmpty()) {
            stop();
            return;
        }
        if (digger != null) {
            switch (digger.tick(soldier, world)) {
                case Digger.Progress.Working _ -> {
                }
                case Digger.Progress.Done _ -> {
                    failed.worked(digger.spot().block());
                    stop();
                }
                // The block gone is the cut done, whatever became of its drop: live, a leaf's sapling
                // fell out of reach and the cut counted as failed. Gone as the clearing counts it, not
                // as the digger's target does: live, soil under a wall wasn't the digger's SOIL, read
                // as gone, and was picked again every tick.
                case Digger.Progress.Failed _ when !Clearing.diggable(world, digger.spot().block().x(), digger.spot().block().y(),
                        digger.spot().block().z()) -> {
                    failed.worked(digger.spot().block());
                    stop();
                }
                case Digger.Progress.Failed(String why) -> {
                    LOG.info("{} leaves that cut for now - {}", name, why);
                    failed.failed(digger.spot().block(), ticks, why);
                    stop();
                    cooldown = COOLDOWN_TICKS;
                }
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        Set<BlockPos> excluded = failed.barredNow(ticks);
        excluded.addAll(claims);
        // Stands it can get to from where it is - on foot, on the blocks it carries, or cutting steps.
        BlockPos feet = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        int span = cover.stream().mapToInt(c -> Math.max(Math.abs(c.x() - feet.x()), Math.abs(c.z() - feet.z()))).max().orElse(0) + REACH_MARGIN;
        Set<BlockPos> reachable = Pathfinder.climbs(world, feet, span, REACH_BUDGET,
                inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT),
                inventory.slotOf(ItemClass.PICKAXE) >= 0).keySet();
        Optional<Clearing.Cut> cut = Clearing.next(world, cover, soldier, excluded, reachable::contains);
        if (cut.isEmpty()) {
            cooldown = COOLDOWN_TICKS;
            return;
        }
        Clearing.Cut c = cut.get();
        BlockPos b = c.block();
        Digger.Target target = c.log() ? Digger.Target.LOG
                : world.leaves(b.x(), b.y(), b.z()).isPresent() ? Digger.Target.LEAVES
                : world.isStone(b.x(), b.y(), b.z()) ? Digger.Target.STONE
                : world.isGravel(b.x(), b.y(), b.z()) ? Digger.Target.GRAVEL : Digger.Target.SOIL;
        if (!target.canDig(inventory)) {
            failed.failed(b, ticks, "no tool to dig the " + target + " at " + b);
            cooldown = COOLDOWN_TICKS;
            return;
        }
        digger = new Digger(name, session, inventory, claims, new Digger.Spot(target, c.stand(), c.block()));
    }

    /** Stops the cut under way, its claims given up. */
    public synchronized void stop() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }
}
