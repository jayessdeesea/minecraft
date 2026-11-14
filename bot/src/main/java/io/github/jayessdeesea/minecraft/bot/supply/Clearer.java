package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

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
    /** How many of the cover's blocks its tools can't dig for their drop, as last counted: said once when it changes. */
    private int leftForTools;

    /** Where it may stand to cut, in the world as it is: anywhere in reach, unless its owner says otherwise. */
    private final BiPredicate<WorldView, BlockPos> stands;

    public Clearer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this(name, session, inventory, claims, (world, stand) -> true);
    }

    /** As {@link #Clearer(String, PlaySession, Inventory, Set)}, cutting only from stands that pass {@code stands}. */
    public Clearer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, BiPredicate<WorldView, BlockPos> stands) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.stands = stands;
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

    /**
     * A tick passed with no {@link #tick} - its owner at other work: the time still counts toward
     * the wait before it looks again. Live, a farmer's earthwork looked once, found nothing in reach,
     * and with its wait counted only on its own ticks - one each job the farmer finished - never
     * looked again.
     */
    public synchronized void pass() {
        if (digger == null) {
            ticks++;
            if (cooldown > 0) {
                cooldown--;
            }
        }
    }

    /** Each tick: cuts the {@code cover} over the camp while {@code free}; anything else stops the cut under way. */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> cover, boolean free) {
        tick(soldier, world, cover, free, Optional.empty());
    }

    /**
     * As {@link #tick(Position, WorldView, List, boolean)}, standing only in the columns of
     * {@code strip} when there is one - a neighbour works the next strip (operator, 2026-11-13) -
     * and with nothing on the ground in reach, cutting the top layer from steps it builds up to it.
     */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> cover, boolean free, Optional<Box> strip) {
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
        // What its tools can't dig is left to a squadmate's, or until the tool is made: a pickaxe's
        // block with no pickaxe carried, or one whose least tier is above its best - dug by hand or
        // with a lesser pickaxe it would break, slowly, and drop nothing (operator, 2026-11-13: a block
        // that needs a tool of a strength it hasn't got is left alone). Live, soldiers with no pickaxe
        // were handed the cobblestone over the camp's floor, and each failed at it three times.
        int bestPickaxe = Digger.Target.bestTool(inventory);
        int bestTier = bestPickaxe < 0 ? Hardness.HAND : ItemClassRegistry.miningTier(inventory.itemAt(bestPickaxe)).orElse(Hardness.HAND);
        List<BlockPos> beyondItsTools = cover.stream()
                .filter(c -> world.hardness(c.x(), c.y(), c.z()).filter(h -> h.tool() == Hardness.Tool.PICKAXE && !h.drops(bestTier)).isPresent())
                .toList();
        excluded.addAll(beyondItsTools);
        if (beyondItsTools.size() != leftForTools) {
            leftForTools = beyondItsTools.size();
            if (leftForTools > 0) {
                LOG.info("{} leaves {} block(s) its tools can't dig for their drop - {} - for a better pickaxe", name, leftForTools,
                        bestTier == Hardness.HAND ? "no pickaxe carried" : "the best carried is tier " + bestTier);
            }
        }
        // Stands it can get to from where it is - on foot, on the blocks it carries, or cutting steps.
        BlockPos feet = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        int span = cover.stream().mapToInt(c -> Math.max(Math.abs(c.x() - feet.x()), Math.abs(c.z() - feet.z()))).max().orElse(0) + REACH_MARGIN;
        Set<BlockPos> reachable = Pathfinder.climbs(world, feet, span, REACH_BUDGET,
                inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT),
                inventory.slotOf(ItemClass.PICKAXE) >= 0).keySet();
        Predicate<BlockPos> within = s -> strip.map(b -> s.x() >= b.minX() && s.x() <= b.maxX() && s.z() >= b.minZ() && s.z() <= b.maxZ())
                .orElse(true);
        Optional<Clearing.Cut> cut = Clearing.next(world, cover, soldier, excluded,
                s -> reachable.contains(s) && stands.test(world, s) && within.test(s));
        // Nothing on the ground reaches: the top layer from the air, on steps it builds - only with
        // blocks to build them of. Live, a hilltop over the keep stood while its builders stood under it.
        if (cut.isEmpty() && strip.isPresent() && inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.COBBLESTONE) > 0) {
            cut = Clearing.fromTheAir(world, cover, excluded, within);
            cut.ifPresent(c -> LOG.info("{} builds up to the top of its strip to cut {} from {}", name, c.block(), c.stand()));
        }
        if (cut.isEmpty()) {
            cooldown = COOLDOWN_TICKS;
            return;
        }
        Clearing.Cut c = cut.get();
        BlockPos b = c.block();
        Digger.Target target = c.log() ? Digger.Target.LOG
                : world.leaves(b.x(), b.y(), b.z()).isPresent() ? Digger.Target.LEAVES
                : world.isStone(b.x(), b.y(), b.z()) ? Digger.Target.STONE
                : world.isCobblestone(b.x(), b.y(), b.z()) ? Digger.Target.COBBLESTONE
                : world.isGravel(b.x(), b.y(), b.z()) ? Digger.Target.GRAVEL
                : world.isSoil(b.x(), b.y(), b.z()) ? Digger.Target.SOIL : Digger.Target.OTHER;
        if (!target.canDig(inventory)) {
            failed.failed(b, ticks, "no tool to dig the " + target + " at " + b);
            cooldown = COOLDOWN_TICKS;
            return;
        }
        // Given the time its walk takes - a climb up a hill to its top is no 20-second walk: live, the
        // keep's builders gave up cut after cut on the hill's summit, each walk timed out on the way.
        digger = new Digger(name, session, inventory, claims, new Digger.Spot(target, c.stand(), c.block()),
                Digger.walkTicks(soldier, c.stand()));
    }

    /** Stops the cut under way, its claims given up. */
    public synchronized void stop() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }
}
