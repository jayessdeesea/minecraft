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

import java.util.HashSet;
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
    /** Blocks of a tool's kind it has taken to by hand, said once each. */
    private final Set<BlockPos> byHand = new HashSet<>();

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

    /** The mining tier of the best pickaxe in {@code inventory}, or {@link Hardness#HAND} with none. */
    public static int bestPickaxeTier(Inventory inventory) {
        int slot = Digger.Target.bestTool(inventory);
        return slot < 0 ? Hardness.HAND : ItemClassRegistry.miningTier(inventory.itemAt(slot)).orElse(Hardness.HAND);
    }

    /** Each finished dig's time since the last {@link #drainTimes}, for the level's pace. */
    private final java.util.List<io.github.jayessdeesea.minecraft.command.LevelPace.Tally> times = new java.util.ArrayList<>();

    /** The finished digs' times since the last call, oldest first: one dig each (command.LevelPace). */
    public synchronized java.util.List<io.github.jayessdeesea.minecraft.command.LevelPace.Tally> drainTimes() {
        java.util.List<io.github.jayessdeesea.minecraft.command.LevelPace.Tally> out = java.util.List.copyOf(times);
        times.clear();
        return out;
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
        return failed.stuck().map(s -> "can't clear the camp: " + s).or(() -> Optional.ofNullable(wanting));
    }

    /**
     * What of its cover it has left for want of a tool, said up for help once it has nothing else
     * to cut (operator, 2026-11-22: as with every job, the tools for it in hand, re-checked as things change; learning an
     * iron pickaxe is wanted, ask - someone else may be assigned it). Null with none.
     */
    private String wanting;
    /** A pickaxe's name by its mining tier (ItemClassRegistry.miningTier). */
    private static final String[] PICKAXES = {"wooden", "stone", "iron", "diamond", "netherite"};

    /**
     * Ore at {@code c} left for want of a pickaxe, said for help: "the emerald ore at ... wants an iron pickaxe for its
     * drop, the best carried stone - left for one who has it". {@code whose} says whose best {@code bestTier} is.
     */
    public static String oreWanting(WorldView world, BlockPos c, int bestTier, String whose) {
        int wanted = world.hardness(c.x(), c.y(), c.z()).orElseThrow(() -> new IllegalStateException("no break time for the ore at " + c))
                .pickaxeTier();
        return "the " + world.oreOf(c.x(), c.y(), c.z()).orElse("unknown") + " ore at " + c + " wants a" + (wanted == 2 ? "n " : " ")
                + PICKAXES[wanted] + " pickaxe for its drop, " + (bestTier == Hardness.HAND ? "no pickaxe" : whose + " " + PICKAXES[bestTier])
                + " - left for one who has it";
    }

    /** A block at {@code c} with no break time known, said for orders: "the chest C at ... has no break time known - left for orders". */
    public static String unknownSaid(WorldView world, BlockPos c) {
        return "the " + world.describeAt(c.x(), c.y(), c.z()) + " at " + c + " has no break time known - left for orders";
    }

    /** Why it isn't cutting, as of its last tick - empty while it is (the flatten's pace telemetry). */
    private String idle;

    /** Why it's not cutting, as of its last tick, if it isn't: not free, nothing to cut, nothing in reach, a cut failed. */
    public synchronized Optional<String> idle() {
        return digger != null ? Optional.empty() : Optional.ofNullable(idle);
    }

    /** How many times it has looked for a cut: its idle reason stands through the rest after each, so a change is a new look. */
    private int looks;

    /** How many times it has looked for a cut - one past the last when the {@link #idleReason} is that look's. */
    public synchronized int looks() {
        return looks;
    }

    /** Why it isn't cutting: nothing in reach, a cut failed, no tool, not free - empty while it is. */
    public synchronized Optional<String> idleReason() {
        return digger != null ? Optional.empty() : Optional.ofNullable(idle);
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
        tick(soldier, world, cover, free, strip, false);
    }

    /**
     * As {@link #tick(Position, WorldView, List, boolean, Optional)}, or cutting a {@code tunnel}: the
     * recovery drill's stair up, cell by cell from where it stands, under rock with no open top
     * ({@link Clearing#nextInTunnel}).
     */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> cover, boolean free, Optional<Box> strip, boolean tunnel) {
        tick(soldier, world, cover, free, strip, tunnel, c -> false);
    }

    /** As {@link #tick(Position, WorldView, List, boolean, Optional, boolean)}, knowing the force's own {@code steps} (Clearing#next). */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> cover, boolean free, Optional<Box> strip, boolean tunnel,
                                  Predicate<BlockPos> steps) {
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (cover.isEmpty()) {
            failed.noWork();
            wanting = null;
        }
        if (!free || cover.isEmpty()) {
            stop();
            idle = !free ? "not free" : "nothing to cut";
            return;
        }
        if (digger != null) {
            idle = null;
            switch (digger.tick(soldier, world)) {
                case Digger.Progress.Working _ -> {
                }
                case Digger.Progress.Done _ -> {
                    failed.worked(digger.spot().block());
                    Digger.Time t = digger.time();
                    times.add(new io.github.jayessdeesea.minecraft.command.LevelPace.Tally(1, t.bestBreak(), t.bestWalk(), t.walk(), t.hand(),
                            t.breaking(), t.ack(), t.pickup()));
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
                    idle = "a cut failed";
                }
            }
            return;
        }
        if (cooldown > 0) {
            return;
        }
        looks++;
        Set<BlockPos> excluded = failed.barredNow(ticks);
        excluded.addAll(claims);
        // What its tools can't dig is left to a squadmate's, or until the tool is made: a pickaxe's
        // block with no pickaxe carried, or one whose least tier is above its best - dug by hand or
        // with a lesser pickaxe it would break, slowly, and drop nothing (operator, 2026-11-13: a block
        // that needs a tool of a strength it hasn't got is left alone). Live, soldiers with no pickaxe
        // were handed the cobblestone over the camp's floor, and each failed at it three times.
        int bestPickaxe = Digger.Target.bestTool(inventory);
        int bestTier = bestPickaxe < 0 ? Hardness.HAND : ItemClassRegistry.miningTier(inventory.itemAt(bestPickaxe)).orElse(Hardness.HAND);
        // Not in a tunnel: a stair up out of a pocket is a way out, and the way out comes first - live
        // (field355), Ivy's stair up ran through an ore block her stone pickaxe couldn't take for its
        // drop, it was set aside, and she stood under it with no cut.
        List<BlockPos> beyondItsTools = tunnel ? List.of() : cover.stream()
                // Only ore: a clear is for the ground gone, not the drop, and anything else is dug with the
                // best tool carried, fists included (operator, 2026-11-14) - stone by hand in 7.5 seconds for
                // no cobblestone, rather than a soldier stood still for the want of a pickaxe.
                .filter(c -> world.isOre(c.x(), c.y(), c.z()))
                .filter(c -> world.hardness(c.x(), c.y(), c.z()).filter(h -> h.tool() == Hardness.Tool.PICKAXE && !h.drops(bestTier)).isPresent())
                .toList();
        excluded.addAll(beyondItsTools);
        wanting = beyondItsTools.stream().findFirst().map(c -> oreWanting(world, c, bestTier, "the best carried")).orElse(null);
        if (beyondItsTools.size() != leftForTools) {
            leftForTools = beyondItsTools.size();
            if (leftForTools > 0) {
                LOG.info("{} leaves {} ore block(s) its tools can't dig for their drop - {} - for a better pickaxe", name, leftForTools,
                        bestTier == Hardness.HAND ? "no pickaxe carried" : "the best carried is tier " + bestTier);
            }
        }
        // Stands it can get to from where it is - on foot, on the blocks it carries, or cutting steps.
        BlockPos feet = Pathfinder.standingCell(world, soldier.x(), soldier.y(), soldier.z());
        int span = cover.stream().mapToInt(c -> Math.max(Math.abs(c.x() - feet.x()), Math.abs(c.z() - feet.z()))).max().orElse(0) + REACH_MARGIN;
        Set<BlockPos> reachable = Pathfinder.climbs(world, feet, span, REACH_BUDGET,
                inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT),
                inventory.slotOf(ItemClass.PICKAXE) >= 0).keySet();
        Predicate<BlockPos> within = s -> strip.map(b -> s.x() >= b.minX() && s.x() <= b.maxX() && s.z() >= b.minZ() && s.z() <= b.maxZ())
                .orElse(true);
        // A stand in the air beside its strip's edge is its own: the rectangles keep the blocks apart,
        // not where their builders stand. Live (field304), a tower's last block, one column wide, was
        // a rectangle of one cell with every stand beside it outside, and went from builder to builder
        // as each timed out.
        Predicate<BlockPos> beside = s -> strip.map(b -> s.x() >= b.minX() - 1 && s.x() <= b.maxX() + 1
                && s.z() >= b.minZ() - 1 && s.z() <= b.maxZ() + 1).orElse(true);
        // Not a cell with a block in it - a shut gate, which a route passes through and nothing stands in: in the squad
        // simulation a flatten beside a farm chose the farm's gate for its stand, "the stand ... has a block in it", again
        // and again, and its ground was never cut (live, the flattens stalled beside every built farm's line).
        Predicate<BlockPos> standable = s -> reachable.contains(s) && stands.test(world, s) && beside.test(s) && !world.isSolid(s.x(), s.y(), s.z());
        Optional<Clearing.Cut> cut = tunnel ? Clearing.nextInTunnel(world, cover, soldier, excluded, standable)
                : Clearing.next(world, cover, soldier, excluded, standable, steps);
        // Nothing on the ground reaches: the top layer from the air, on steps it builds - only with
        // blocks to build them of. Live, a hilltop over the keep stood while its builders stood under it.
        if (cut.isEmpty() && !tunnel && strip.isPresent() && inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.COBBLESTONE) > 0) {
            cut = Clearing.fromTheAir(world, cover, excluded, beside, steps);
            cut.ifPresent(c -> LOG.info("{} builds up to the top of its strip to cut {} from {}", name, c.block(), c.stand()));
        }
        if (cut.isEmpty()) {
            cooldown = COOLDOWN_TICKS;
            // Why nothing reached: a block too high with nothing to climb on, or no stand at all.
            idle = !tunnel && strip.isPresent() && inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.COBBLESTONE) == 0
                    ? "nothing in reach, no blocks to climb on" : "nothing in reach";
            return;
        }
        Clearing.Cut c = cut.get();
        BlockPos b = c.block();
        Digger.Target target = targetFor(world, b, c.log());
        // Without the tool the kind wants - stone with no pickaxe - it's dug as any other block, by
        // hand in its own break time, for the clear and not the drop: a way out, a snag, the ground.
        if (!target.canDig(inventory)) {
            if (world.hardness(b.x(), b.y(), b.z()).isEmpty()) {
                failed.failed(b, ticks, "no tool to dig the " + target + " at " + b + ", and no break time known to dig it by hand");
                cooldown = COOLDOWN_TICKS;
                idle = "no tool for it";
                return;
            }
            if (!byHand.contains(b)) {
                byHand.add(b);
                LOG.info("{} digs the {} at {} by hand - no {} carried; it drops nothing that way", name, target, b, target.tool);
            }
            target = Digger.Target.OTHER;
        }
        // Given the time its walk takes - a climb up a hill to its top is no 20-second walk: live, the
        // keep's builders gave up cut after cut on the hill's summit, each walk timed out on the way.
        digger = new Digger(name, session, inventory, claims, new Digger.Spot(target, c.stand(), c.block()),
                Digger.walkTicks(soldier, c.stand()));
    }

    /**
     * What a clear digs {@code block} as: a tree's log or leaves, stone, cobblestone, gravel and soil
     * for their drops - and ore of every kind for theirs, taken and picked up, not broken as a block
     * in the way. Live, a day's flattening went through ore after ore, the raw iron left lying where
     * it fell, and no soldier of eight held an ingot. Anything else is dug to clear it.
     */
    static Digger.Target targetFor(WorldView world, BlockPos b, boolean log) {
        // A log with a block over it is no gatherer's LOG (Digger.Target#underTheSky) - its dig would read it as gone
        // a tick in - but it's in the clear's way all the same. Live (field674-675), the camp's clearers took the same
        // roofed logs nine times in a row, each walk given up as "the LOG is gone".
        return world.isGate(b.x(), b.y(), b.z()) ? Digger.Target.GATE
                : log && Digger.Target.LOG.at(world, b) ? Digger.Target.LOG
                : log ? Digger.Target.ROOFED_LOG
                : world.leaves(b.x(), b.y(), b.z()).isPresent() ? Digger.Target.LEAVES
                : world.isIronOre(b.x(), b.y(), b.z()) ? Digger.Target.IRON_ORE
                : Digger.Target.COPPER_ORE.at(world, b) ? Digger.Target.COPPER_ORE
                : Digger.Target.COAL_ORE.at(world, b) ? Digger.Target.COAL_ORE
                : world.isOre(b.x(), b.y(), b.z()) ? Digger.Target.ORE
                : world.isStone(b.x(), b.y(), b.z()) ? Digger.Target.STONE
                : world.isCobblestone(b.x(), b.y(), b.z()) ? Digger.Target.COBBLESTONE
                : world.isGravel(b.x(), b.y(), b.z()) ? Digger.Target.GRAVEL
                // Soil under a block - a tunnel's, a road cut into a hillside - is no SOIL to its digger,
                // which takes soil only from an open top: dug as any block. Live in the squad simulation,
                // the keep's haul road was given up 53 times, each "the SOIL is gone" a tick in.
                : world.isSoil(b.x(), b.y(), b.z()) && !world.isSolid(b.x(), b.y() + 1, b.z()) ? Digger.Target.SOIL : Digger.Target.OTHER;
    }

    /**
     * The walk to its cut's stand has no route: the cut passed over a while and the cut stopped, so the next it takes
     * is another - not the same one again after its owner's cooldown. Live (field548-549), Carol stood four minutes
     * and more at a farm's earthwork, its stand inside a block, the same cut taken again each time.
     */
    public synchronized void unreachable(String why) {
        if (digger == null) {
            return;
        }
        LOG.info("{} leaves that cut for now - {}", name, why);
        failed.failed(digger.spot().block(), ticks, why);
        stop();
    }

    /** Stops the cut under way, its claims given up. */
    public synchronized void stop() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }
}
