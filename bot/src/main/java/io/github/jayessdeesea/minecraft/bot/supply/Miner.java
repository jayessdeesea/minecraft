package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One soldier cutting its squad's {@link Staircase} down: from the step above the first not yet
 * clear, it breaks that step's cells ({@link Breacher}), then walks down onto it and cuts the next.
 * Its state is the world's - a step is cut when its cells are open - so a run stopped anywhere, by a
 * fight or a restart, picks up where the stairs end.
 *
 * <p>Ore it can see from where it stands - beside its body or the step it cuts, outside the shaft's
 * 3x3, where taking it holes no step and no pillar - it mines first, when its pickaxe gets the drop
 * and nothing liquid lies behind it. That's how the stairs pay in iron and diamonds, not only stone.
 *
 * <p>It stops, loudly, where going on would be a flood - water or lava beside a cell it would open
 * - or a fall it can't floor. A step or a tunnel over a cave is floored: cobblestone put in the
 * gap under its next cell from the one before, as a bridge over the cave, and the cut goes on
 * ({@link #BRIDGE_TRIES} tries for the stairs, and for each tunnel). The stairs, with nothing to
 * floor with or a cave too wide, stop; a tunnel waits for blocks, or is given up for the next. Live, every branch of the mine met a cave and was given
 * up, and the squads spent their nights in a mine with nothing left to cut and no iron coming up.
 */
public final class Miner {

    private static final Logger LOG = LogManager.getLogger(Miner.class);

    /** How far off the standing cell's centre still counts as standing on it. */
    private static final double ON_CELL = 0.6;

    /** What the stairs came to this tick. */
    public sealed interface Progress {
        /** Cutting step {@code step} of {@code steps}, or walking down to it. */
        record Working(int step, int steps) implements Progress {
        }

        /** Every step cut: the stairs reach the bottom. */
        record Done() implements Progress {
        }

        /** Can't go on: {@code why}. */
        record Stopped(String why) implements Progress {
        }
    }

    /** Ticks spent on one ore before it's left: one the server won't break isn't worth the stairs. */
    static final int ORE_PATIENCE_TICKS = 600;

    private final String name;
    private final Inventory inventory;
    private final Breacher breacher;
    private final Footing footing;
    /** Blocks put under each tunnel's floor so far, by tunnel. */
    private final java.util.Map<Integer, Integer> bridged = new java.util.HashMap<>();

    /** Blocks put under a tunnel's floor before it's given up: a cave too wide to bridge isn't worth it. */
    static final int BRIDGE_TRIES = 24;
    private final Set<BlockPos> leftOre = new HashSet<>();
    private BlockPos ore;
    private int oreTicks;
    private Position standAt;
    private int lastStep = -1;

    public Miner(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.inventory = inventory;
        this.breacher = new Breacher(name, session, inventory);
        this.footing = new Footing(session, inventory);
    }

    /** Where the soldier should stand: on the step above the one it cuts. */
    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    /** The first step of {@code stairs} not yet cut, or empty when all are. */
    public static OptionalInt firstUncut(WorldView world, Staircase stairs) {
        return firstUncut(world, stairs.cuts(), Set.of());
    }

    /**
     * The first of {@code cuts} still to cut, skipping any in a {@code blocked} tunnel and any whose
     * standing cell isn't open yet - a branch off a stretch of main tunnel that was given up.
     */
    static OptionalInt firstUncut(WorldView world, List<Staircase.Cut> cuts, Set<Integer> blocked) {
        for (int k = 0; k < cuts.size(); k++) {
            Staircase.Cut cut = cuts.get(k);
            if (blocked.contains(cut.tunnel()) || cut.cells().stream().noneMatch(c -> cut.toCut(world, c))) {
                continue;
            }
            BlockPos stand = cut.stand();
            if (cut.tunnel() >= 0 && (world.isSolid(stand.x(), stand.y(), stand.z()) || world.isSolid(stand.x(), stand.y() + 1, stand.z()))) {
                continue;
            }
            return OptionalInt.of(k);
        }
        return OptionalInt.empty();
    }

    /** How far above the stairs' bottom a cutter still works a mine cut to the bottom for its ore. */
    static final int PROSPECT_FROM = 8;

    /** Tunnels given up for water, lava or a cave beside them. */
    private final Set<Integer> blocked = new HashSet<>();
    /** Tunnels over a cave left until the miner carries blocks to floor it. */
    private final Set<Integer> waiting = new HashSet<>();

    /** What a cave is floored with: cobblestone, else dirt - empty with neither. */
    private Optional<ItemClassRegistry.ItemClass> bridgeBlock() {
        return inventory.count(ItemClassRegistry.ItemClass.COBBLESTONE) > 0 ? Optional.of(ItemClassRegistry.ItemClass.COBBLESTONE)
                : inventory.count(ItemClassRegistry.ItemClass.DIRT) > 0 ? Optional.of(ItemClassRegistry.ItemClass.DIRT) : Optional.empty();
    }

    /** Each tick: cuts {@code stairs} while {@code free}; anything else stops the dig under way. */
    public synchronized Progress tick(Position soldier, WorldView world, Staircase stairs, boolean free) {
        return tick(soldier, world, stairs, free, Optional.empty());
    }

    /**
     * As {@link #tick(Position, WorldView, Staircase, boolean)}, cutting only the tunnels of
     * {@code share} ({@link Staircase#share}) - empty: all of them. Its tunnels cut, and its ore
     * taken, it's done; with tunnels of its share not reached yet - the main tunnel not cut to their
     * roots - it waits at the landing, out of the way, rather than read done.
     */
    public synchronized Progress tick(Position soldier, WorldView world, Staircase stairs, boolean free, Optional<Set<Integer>> share) {
        return tick(soldier, world, stairs, free, share, 0);
    }

    /**
     * As {@link #tick(Position, WorldView, Staircase, boolean, Optional)}, {@code place} its place
     * among the mine's cutters (0 the first): waiting, each stands a step of its own, so two waiting
     * aren't on one cell.
     */
    public synchronized Progress tick(Position soldier, WorldView world, Staircase stairs, boolean free, Optional<Set<Integer>> share, int place) {
        this.share = share;
        List<Staircase.Cut> cuts = stairs.cuts();
        OptionalInt next;
        Staircase.Cut cut;
        Optional<ItemClassRegistry.ItemClass> bridging = bridgeBlock();
        if (bridging.isPresent()) {
            waiting.clear(); // blocks again: the tunnels that waited on them are cut on
        }
        while (true) {
            Set<Integer> skipped = new HashSet<>(blocked);
            skipped.addAll(waiting);
            share.ifPresent(mine -> cuts.stream().map(Staircase.Cut::tunnel).filter(t -> !mine.contains(t)).forEach(skipped::add));
            // Its branches only once the main tunnel's cut through, by the first: started from a root
            // the main tunnel's cutter still stands on, two stood on one cell.
            boolean mainOpen = !blocked.contains(0) && share.filter(mine -> !mine.contains(0)).isPresent()
                    && notReached(world, cuts.stream().filter(c -> c.tunnel() <= 0).toList(), Set.of());
            next = mainOpen ? OptionalInt.empty() : firstUncut(world, cuts, skipped);
            if (next.isEmpty() && share.isPresent() && (mainOpen || notReached(world, cuts, skipped))) {
                // Its tunnels' roots not cut to yet: it waits at the landing once the stairs are down;
                // before, on the stairs a few steps behind the one cutting, down the mine with it - at
                // the head, its squad waited on it at the top by night.
                breacher.stop();
                OptionalInt step = firstUncut(world, cuts.stream().filter(c -> c.tunnel() < 0).toList(), Set.of());
                int behind = Math.max(0, place - 1);
                // The last step is where the main tunnel's cutter starts from: the waiting stand above it.
                int k = step.isEmpty() ? stairs.steps() - 2 - behind : step.getAsInt() - WAIT_BEHIND - behind;
                BlockPos wait = k >= 0 ? stairs.step(k) : stairs.head();
                standAt = new Position(wait.x() + 0.5, wait.y(), wait.z() + 0.5);
                return new Progress.Working(0, cuts.size());
            }
            if (next.isEmpty()) {
                // All cut: worked again for the ore in sight of what was cut - passed by, or not
                // harvestable then. Live, no miner mined ore in twelve runs: the tunnels were cut,
                // and nobody went back down them.
                // Only from down there - at the landing for the night: from the top, the mine reads
                // done and the squad holds the top. Live, working it from the top kept the squad's
                // task open, and its soldiers were ordered to the bottom 54 blocks down by a route
                // that failed: an hour and more of "no route" CANTCO a day.
                next = soldier.y() <= stairs.bottom() + PROSPECT_FROM ? prospect(world, stairs, cuts) : OptionalInt.empty();
                if (next.isEmpty()) {
                    stop();
                    return new Progress.Done();
                }
                cut = cuts.get(next.getAsInt());
                break;
            }
            cut = cuts.get(next.getAsInt());
            // The stairs closed below a step sealed: no deeper, for good (operator, 2026-11-17: water or
            // lava blocked, and that path considered closed).
            if (!stairs.head().equals(stairsClosedFor)) {
                stairsClosed = null; // another mine's stairs: what closed the last doesn't close these
                stairsClosedAt = Integer.MAX_VALUE;
            }
            if (cut.tunnel() < 0 && stairsClosed != null && next.getAsInt() >= stairsClosedAt) {
                stop();
                return new Progress.Stopped(stairsClosed);
            }
            Optional<String> hazard = hazard(world, cut, next.getAsInt());
            if (hazard.isEmpty()) {
                break;
            }
            // Water or lava: blocked first, from where it stands, with the junk it carries - the flow
            // kept out of the mine - and then the path closed.
            if (!hazard.get().startsWith("no floor")) {
                BlockPos feet = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
                Optional<BlockPos> wet = hazardCells(world, cut).stream()
                        .filter(c -> reach(feet, c) && !world.noBuild(c.x(), c.y(), c.z()) && sealTicks.getOrDefault(c, 0) < ORE_PATIENCE_TICKS)
                        .filter(c -> faceOf(world, c).isPresent()).findFirst();
                if (wet.isPresent() && bridging.isPresent() && free) {
                    if (sealTicks.merge(wet.get(), 1, Integer::sum) == 1) {
                        LOG.info("{} blocks the water or lava at {} - {}", name, wet.get(), hazard.get());
                    }
                    breacher.stop();
                    footing.place(wet.get(), bridging.get(), faceOf(world, wet.get()).orElseThrow());
                    footing.tick();
                    return new Progress.Working(next.getAsInt(), cuts.size());
                }
                // Seen from down the tunnel, out of reach: to the cut's stand first - the cuts before it are
                // what opened it - and blocked from there. Live (field357), Alice closed the main tunnel at
                // water 70 blocks off and blocked nothing, two dirt in her pack.
                BlockPos stand = cut.stand();
                boolean there = Math.hypot(soldier.x() - (stand.x() + 0.5), soldier.z() - (stand.z() + 0.5)) < ON_CELL
                        && Math.abs(soldier.y() - stand.y()) < ON_CELL;
                boolean anyWet = hazardCells(world, cut).stream().anyMatch(c -> sealTicks.getOrDefault(c, 0) < ORE_PATIENCE_TICKS);
                if (anyWet && bridging.isPresent() && free && !there
                        && sealWalkTicks.merge(stand, 1, Integer::sum) <= SEAL_WALK_TICKS) {
                    breacher.stop();
                    standAt = new Position(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
                    return new Progress.Working(next.getAsInt(), cuts.size());
                }
                if (cut.tunnel() < 0) {
                    stairsClosed = hazard.get();
                    stairsClosedAt = next.getAsInt();
                    stairsClosedFor = stairs.head();
                    LOG.warn("{} closes the stairs at step {}: {}", name, next.getAsInt() + 1, hazard.get());
                    stop();
                    return new Progress.Stopped(hazard.get());
                }
                LOG.warn("{} closes tunnel {} of the mine: {}", name, cut.tunnel(), hazard.get());
                blocked.add(cut.tunnel());
                continue;
            }
            Optional<Bridge> support = bridgeable(world, cut);
            if (cut.tunnel() < 0) {
                // A step over a cave is floored like a tunnel - the stairs are the only way down, so
                // with nothing to floor it with, or too wide a cave, they stop. Live, the stairs met
                // a cave at step 11 and stopped; the CANTCO went unanswered for an hour and the mine
                // below was out of everyone's reach.
                if (support.isPresent() && bridging.isPresent() && bridged.getOrDefault(cut.tunnel(), 0) < BRIDGE_TRIES) {
                    return bridge(soldier, cut, next.getAsInt(), cuts.size(), support.get(), bridging.get(), free);
                }
                stop();
                return new Progress.Stopped(hazard.get() + (hazard.get().startsWith("no floor")
                        ? " - not floored: " + (bridging.isEmpty() ? "no blocks carried" : whyNotBridgeable(world, cut, bridged.getOrDefault(cut.tunnel(), 0))) : ""));
            }
            if (support.isPresent() && bridged.getOrDefault(cut.tunnel(), 0) < BRIDGE_TRIES) {
                if (bridging.isPresent()) {
                    return bridge(soldier, cut, next.getAsInt(), cuts.size(), support.get(), bridging.get(), free);
                }
                // Nothing to floor it with yet: it waits, not given up - the next tunnel's stone is
                // the bridge. Live, a miner with no cobblestone gave up the main tunnel at its first
                // gap, and every branch off it with it.
                if (waiting.add(cut.tunnel())) {
                    LOG.info("{} leaves {} of the mine until it carries blocks to floor the cave at {}", name,
                            cut.tunnel() == 0 ? "the main tunnel" : "branch " + cut.tunnel(), cut.feet().offset(0, -1, 0));
                }
                continue;
            }
            // A tunnel is one of many: give it up, and go on with the next.
            LOG.warn("{} gives up tunnel {} of the mine: {}{}", name, cut.tunnel(), hazard.get(),
                    hazard.get().startsWith("no floor") ? " - not floored: " + whyNotBridgeable(world, cut, bridged.getOrDefault(cut.tunnel(), 0)) : "");
            blocked.add(cut.tunnel());
        }
        int k = next.getAsInt();
        BlockPos from = cut.stand();
        standAt = new Position(from.x() + 0.5, from.y(), from.z() + 0.5);
        if (k != lastStep) {
            if (cut.tunnel() < 0) {
                LOG.info("{} cuts step {} of {} down at {}", name, k + 1, stairs.steps(), cut.feet());
            } else if (lastStep < 0 || lastStep >= cuts.size() || cuts.get(lastStep).tunnel() != cut.tunnel()) {
                // Past the end when its mine changed under it - a level deeper has fewer cuts than the
                // stretches run before it: live (field312), every tick on the new level failed here.
                LOG.info("{} cuts {} of the mine from {}", name, cut.tunnel() == 0 ? "the main tunnel" : "branch " + cut.tunnel(), cut.feet());
            }
            lastStep = k;
        }
        boolean on = Math.hypot(soldier.x() - standAt.x(), soldier.z() - standAt.z()) < ON_CELL && Math.abs(soldier.y() - from.y()) < ON_CELL;
        if (!free || !on) {
            breacher.stop();
            return new Progress.Working(k, cuts.size());
        }
        // On the stairs, its well kept whole: holes in the pillar, the walls and the floor round the
        // step it cuts noted to fill (operator, 2026-11-15).
        if (cut.tunnel() < 0) {
            holesBeside(world, stairs, cut).forEach(refill::add);
        }
        Optional<BlockPos> seen = oreInSight(world, stairs, from, cut, tunnelFloors(cuts), bestPickaxe());
        // The ore it took the last tick, gone now: its cell is a hole to put back - on the stairs or off a
        // tunnel - but never one of the mine's own cells, which stay open (the rules of a mine: the
        // vein followed, then backfilled; the grid's strips left).
        if (ore != null && !world.isOre(ore.x(), ore.y(), ore.z()) && !Staircase.uncut(world, ore) && !mineCells(stairs).contains(ore)) {
            refill.add(ore);
        }
        if (seen.isEmpty()) {
            // The vein done: what it took out of the well's walls put back, last taken first, and the holes
            // it found filled - each against a solid face, in cobblestone, else dirt.
            // Never into a cell the force keeps open - another squad's stairs or tunnels (WorldView.noBuild):
            // live (field319), a step of the stairs under a squad's level was found solid, and two
            // soldiers stood above it with no way down to theirs.
            refill.removeIf(c -> world.isSolid(c.x(), c.y(), c.z()) || world.isHazard(c.x(), c.y(), c.z()) || !reach(from, c)
                    || world.noBuild(c.x(), c.y(), c.z()));
            Optional<BlockPos> hole = refill.isEmpty() ? Optional.empty() : Optional.of(refill.getLast());
            Optional<BlockFace> face = hole.flatMap(h -> faceOf(world, h));
            if (hole.isPresent() && refillTicks.merge(hole.get(), 1, Integer::sum) > ORE_PATIENCE_TICKS) {
                LOG.warn("{} leaves the hole at {} in the mine's wall: not filled in {} ticks", name, hole.get(), ORE_PATIENCE_TICKS);
                refill.remove(hole.get());
            } else if (hole.isPresent() && face.isPresent() && bridging.isPresent()) {
                breacher.stop();
                if (refillTicks.get(hole.get()) == 1) {
                    LOG.info("{} fills the hole at {} in the mine's wall", name, hole.get());
                }
                footing.place(hole.get(), bridging.get(), face.get());
                footing.tick();
                return new Progress.Working(k, cuts.size());
            }
            hole.filter(h -> face.isEmpty()).ifPresent(refill::remove);
        }
        if (seen.isPresent()) {
            if (!seen.get().equals(ore)) {
                ore = seen.get();
                oreTicks = 0;
                oreFound++;
                LOG.info("{} mines the ore at {} beside the stairs", name, ore);
            }
            if (++oreTicks > ORE_PATIENCE_TICKS) {
                LOG.warn("{} leaves the ore at {}: not broken in {} ticks", name, ore, ORE_PATIENCE_TICKS);
                oreLeft++;
                leftOre.add(ore);
                breacher.stop();
            } else {
                breacher.tick(world, List.of(ore));
            }
            return new Progress.Working(k, cuts.size());
        }
        breacher.keepStations(cut.recess());
        breacher.tick(world, cut.cells());
        return new Progress.Working(k, cuts.size());
    }

    /** Ore blocks it set about mining, and of them those it left unbroken: what it took is the difference. */
    private int oreFound, oreLeft;

    /** Ore blocks it has mined since it was made - the night's yield is the change over the night ({@code [Outcome]}). */
    public synchronized int oreTaken() {
        return oreFound - oreLeft;
    }

    /** Every cell {@code stairs} cuts, worked out once a staircase: a vein's hole in one of them isn't filled. */
    private Set<BlockPos> mineCells(Staircase stairs) {
        if (!stairs.equals(cellsOf)) {
            cellsOf = stairs;
            cells = stairs.cutCells();
        }
        return cells;
    }

    private Staircase cellsOf;
    private Set<BlockPos> cells = Set.of();

    /** Holes to fill in the well, last found last: what was taken out of its walls, and what it found open. */
    private final java.util.LinkedHashSet<BlockPos> refill = new java.util.LinkedHashSet<>();
    /** Ticks spent filling each hole: one the server won't take is left after {@link #ORE_PATIENCE_TICKS}. */
    private final java.util.Map<BlockPos, Integer> refillTicks = new java.util.HashMap<>();

    /**
     * The open cells round step {@code cut} that should be solid: its pillar beside it, the wall
     * outside the shaft, and the floor under its feet - not any cell the mine cuts, which is meant
     * to be open.
     */
    static List<BlockPos> holesBeside(WorldView world, Staircase stairs, Staircase.Cut cut) {
        Set<BlockPos> cutCells = stairs.cutCells();
        List<BlockPos> holes = new ArrayList<>();
        List<BlockPos> around = new ArrayList<>();
        for (BlockPos cell : cut.cells()) {
            for (int[] d : FACES) {
                if (d[1] == 0) {
                    around.add(cell.offset(d[0], 0, d[2]));
                }
            }
        }
        around.add(cut.feet().offset(0, -1, 0));
        for (BlockPos at : around) {
            boolean shouldStand = stairs.inPillar(at.x(), at.z()) || !stairs.inShaft(at.x(), at.z()) || at.equals(cut.feet().offset(0, -1, 0));
            if (shouldStand && !cutCells.contains(at) && !world.isSolid(at.x(), at.y(), at.z()) && !world.isHazard(at.x(), at.y(), at.z())
                    && underground(world, at) && !holes.contains(at)) {
                holes.add(at);
            }
        }
        return holes;
    }

    /** How far up a hole looks for the rock over it. */
    static final int COVER = 24;

    /**
     * Whether {@code cell} is under rock - something solid over it in its column within
     * {@link #COVER}: a cave's mouth or a gap in the pillar, not the open air of a hillside the stairs'
     * top steps cut into. Live, the cutter walled the open air round the mine's head.
     */
    static boolean underground(WorldView world, BlockPos cell) {
        for (int y = cell.y() + 1; y <= cell.y() + COVER; y++) {
            if (world.isSolid(cell.x(), y, cell.z())) {
                return true;
            }
        }
        return false;
    }

    /** A face of {@code cell} with a solid block against it, to put a block there against: the floor first. */
    private static Optional<BlockFace> faceOf(WorldView world, BlockPos cell) {
        return java.util.stream.Stream.of(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.UP)
                .filter(f -> world.isSolid(cell.x() + f.dx, cell.y() + f.dy, cell.z() + f.dz)).findFirst();
    }

    /** Whether {@code cell} is in reach of the eyes of one standing on {@code from}. */
    private static boolean reach(BlockPos from, BlockPos cell) {
        return Math.sqrt(Math.pow(cell.x() + 0.5 - (from.x() + 0.5), 2) + Math.pow(cell.y() + 0.5 - (from.y() + EYES), 2)
                + Math.pow(cell.z() + 0.5 - (from.z() + 0.5), 2)) <= ORE_REACH;
    }

    /** A block to put in the bridge over a tunnel's cave: its cell, and the side of it the block it goes against is on. */
    record Bridge(BlockPos cell, BlockFace support) {
    }

    /**
     * The next block of the bridge over the gap under {@code cut}'s feet, if the cut's only hazard
     * is that gap - nothing liquid near. The gap itself, against a solid block beside or under it;
     * with none - a wide cave, the stand diagonal to the gap, as off the stairs' last step - a block
     * beside the gap first, against the solid it touches, and the gap against that next. Empty
     * when neither can go anywhere.
     */
    static Optional<Bridge> bridgeable(WorldView world, Staircase.Cut cut) {
        BlockPos floor = cut.feet().offset(0, -1, 0);
        if (world.isSolid(floor.x(), floor.y(), floor.z()) || besideLiquid(world, floor)
                || cut.cells().stream().anyMatch(c -> besideLiquid(world, c) || world.isHazard(c.x(), c.y(), c.z()))) {
            return Optional.empty();
        }
        BlockPos under = cut.stand().offset(0, -1, 0);
        Optional<BlockFace> direct = supportOf(world, floor, under);
        if (direct.isPresent()) {
            return Optional.of(new Bridge(floor, direct.get()));
        }
        // A block beside the gap, nearest the stand's floor first: in reach, and on the way to it.
        List<BlockFace> sides = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.DOWN));
        sides.sort(java.util.Comparator.comparingInt(f -> Math.abs(floor.x() + f.dx - under.x()) + Math.abs(floor.y() + f.dy - under.y())
                + Math.abs(floor.z() + f.dz - under.z())));
        for (BlockFace side : sides) {
            BlockPos beside = floor.offset(side.dx, side.dy, side.dz);
            if (world.isSolid(beside.x(), beside.y(), beside.z()) || world.isHazard(beside.x(), beside.y(), beside.z()) || besideLiquid(world, beside)) {
                continue;
            }
            Optional<BlockFace> support = supportOf(world, beside, under);
            if (support.isPresent()) {
                return Optional.of(new Bridge(beside, support.get()));
            }
        }
        return Optional.empty();
    }

    /** A side of {@code cell} with a solid block on it - {@code near}'s side first - for a block put in {@code cell}. */
    private static Optional<BlockFace> supportOf(WorldView world, BlockPos cell, BlockPos near) {
        List<BlockFace> sides = new ArrayList<>(List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST, BlockFace.DOWN));
        sides.sort(java.util.Comparator.comparing(f -> cell.offset(f.dx, f.dy, f.dz).equals(near) ? 0 : 1));
        return sides.stream().filter(f -> world.isSolid(cell.x() + f.dx, cell.y() + f.dy, cell.z() + f.dz)).findFirst();
    }

    /** Why {@code cut}'s gap isn't floored, for the log: which of {@link #bridgeable}'s checks failed. */
    static String whyNotBridgeable(WorldView world, Staircase.Cut cut, int bridgedSoFar) {
        BlockPos floor = cut.feet().offset(0, -1, 0);
        if (bridgedSoFar >= BRIDGE_TRIES) {
            return bridgedSoFar + " blocks put under it already";
        }
        for (BlockPos c : List.of(floor, cut.cells().get(0), cut.cells().get(cut.cells().size() - 1))) {
            for (int[] d : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (world.isHazard(c.x() + d[0], c.y() + d[1], c.z() + d[2])) {
                    return "a hazard at " + c.offset(d[0], d[1], d[2]) + " beside " + c;
                }
            }
        }
        return "nothing solid within two blocks of the gap at " + floor + " to build out from";
    }

    /** From the cut's stand, cobblestone in the gap under its feet, against {@code support}; the cut goes on once the world shows it. */
    private Progress bridge(Position soldier, Staircase.Cut cut, int k, int steps, Bridge next, ItemClassRegistry.ItemClass block, boolean free) {
        breacher.stop();
        BlockPos from = cut.stand();
        standAt = new Position(from.x() + 0.5, from.y(), from.z() + 0.5);
        boolean on = Math.hypot(soldier.x() - standAt.x(), soldier.z() - standAt.z()) < ON_CELL && Math.abs(soldier.y() - from.y()) < ON_CELL;
        if (free && on) {
            int n = bridged.merge(cut.tunnel(), 1, Integer::sum);
            LOG.info("{} floors {} of the mine over a cave at {} ({} of {})", name,
                    cut.tunnel() < 0 ? "the stairs" : cut.tunnel() == 0 ? "the main tunnel" : "branch " + cut.tunnel(), next.cell(), n, BRIDGE_TRIES);
            footing.place(next.cell(), block, next.support());
        }
        footing.tick();
        return new Progress.Working(k, steps);
    }

    /** Steps behind the stairs' cutter a miner waiting for its share stands. */
    public static final int WAIT_BEHIND = 3;

    /** The tunnels it cuts this tick ({@link #tick(Position, WorldView, Staircase, boolean, Optional)}). */
    private Optional<Set<Integer>> share = Optional.empty();

    /** Whether a cut of a tunnel not {@code skipped} is still to cut - its stand not open yet. */
    private static boolean notReached(WorldView world, List<Staircase.Cut> cuts, Set<Integer> skipped) {
        return cuts.stream().anyMatch(c -> !skipped.contains(c.tunnel())
                && c.cells().stream().anyMatch(cell -> c.toCut(world, cell)));
    }

    /** The first cut, all open, with ore in sight of its stand ({@link #oreInSight}) - empty when none has. */
    private OptionalInt prospect(WorldView world, Staircase stairs, List<Staircase.Cut> cuts) {
        Set<BlockPos> floors = tunnelFloors(cuts);
        int tier = bestPickaxe();
        for (int k = 0; k < cuts.size(); k++) {
            Staircase.Cut cut = cuts.get(k);
            if (blocked.contains(cut.tunnel()) || share.filter(mine -> !mine.contains(cut.tunnel())).isPresent()
                    || cut.cells().stream().anyMatch(c -> cut.toCut(world, c))) {
                continue;
            }
            if (oreInSight(world, stairs, cut.stand(), cut, floors, tier).isPresent()) {
                return OptionalInt.of(k);
            }
        }
        return OptionalInt.empty();
    }

    /** How far from the eyes ore is broken: the server's block reach. */
    static final double ORE_REACH = 4.5;
    /** How far out across from the stand ore is followed: its drop near enough to be picked up. */
    static final int ORE_ACROSS = 2;
    /** Eye height over the feet. */
    private static final double EYES = 1.62;

    /**
     * Ore beside the body standing on {@code from}, or beside the cut's cells - and any other ore in
     * reach of it with a face open, nearest first: the next block of a vein whose first block was
     * just mined, or the wall of a cave the tunnel crosses (operator, 2026-11-15: miners left coal
     * and copper behind - a vein runs eight to twenty blocks, and only those touching the tunnel were
     * taken). Outside the shaft's 3x3, not under a tunnel's floor, not left before, its drop got with
     * the pickaxe carried, nothing liquid beside it.
     */
    Optional<BlockPos> oreInSight(WorldView world, Staircase stairs, BlockPos from, Staircase.Cut cut) {
        return oreInSight(world, stairs, from, cut, tunnelFloors(stairs.cuts()), bestPickaxe());
    }

    /** The cells under the tunnels' floors - ore there is left, so as not to hole a floor. */
    private static Set<BlockPos> tunnelFloors(List<Staircase.Cut> cuts) {
        return cuts.stream().filter(c -> c.tunnel() >= 0).map(c -> c.feet().offset(0, -1, 0)).collect(Collectors.toSet());
    }

    /**
     * As {@link #oreInSight(WorldView, Staircase, BlockPos, Staircase.Cut)}, with the tunnels' floors
     * and the pickaxe's tier worked out once by a caller asking it of every cut: live (commit 512's
     * profile), each cut's ask built every cut of the mine again, a sixth of the garrison's CPU.
     */
    private Optional<BlockPos> oreInSight(WorldView world, Staircase stairs, BlockPos from, Staircase.Cut cut, Set<BlockPos> floors, int tier) {
        java.util.function.Predicate<BlockPos> takeable = at -> world.isOre(at.x(), at.y(), at.z()) && !stairs.inShaft(at.x(), at.z())
                && !floors.contains(at) && !leftOre.contains(at)
                && world.hardness(at.x(), at.y(), at.z()).map(h -> h.drops(tier)).orElse(false)
                && !besideLiquid(world, at);
        List<BlockPos> near = new ArrayList<>(List.of(from, from.offset(0, 1, 0)));
        near.addAll(cut.cells());
        for (BlockPos cell : near) {
            for (int[] d : FACES) {
                BlockPos at = cell.offset(d[0], d[1], d[2]);
                if (takeable.test(at)) {
                    return Optional.of(at);
                }
            }
        }
        double ex = from.x() + 0.5, ey = from.y() + EYES, ez = from.z() + 0.5;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -ORE_ACROSS; dx <= ORE_ACROSS; dx++) {
            for (int dy = -1; dy <= 3; dy++) {
                for (int dz = -ORE_ACROSS; dz <= ORE_ACROSS; dz++) {
                    BlockPos at = from.offset(dx, dy, dz);
                    double distance = Math.sqrt(Math.pow(at.x() + 0.5 - ex, 2) + Math.pow(at.y() + 0.5 - ey, 2) + Math.pow(at.z() + 0.5 - ez, 2));
                    // Ore first: nearly every cell isn't, and that's one lookup against open()'s six.
                    if (distance <= ORE_REACH && distance < bestDistance && takeable.test(at) && open(world, at)) {
                        best = at;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** Whether a face of {@code cell} is open to the air: a block can be got at, and what it drops falls out. */
    private static boolean open(WorldView world, BlockPos cell) {
        for (int[] d : FACES) {
            if (!world.isSolid(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    private static boolean besideLiquid(WorldView world, BlockPos cell) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            if (world.isHazard(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    /** The best pickaxe's mining tier carried, or {@link Hardness#HAND} with none. */
    private int bestPickaxe() {
        int best = Hardness.HAND;
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            OptionalInt tier = ItemClassRegistry.miningTier(inventory.itemAt(slot));
            if (tier.isPresent()) {
                best = Math.max(best, tier.getAsInt());
            }
        }
        return best;
    }

    /** The water and lava cells in or beside {@code cut}'s cells: what's blocked before it's closed. */
    static List<BlockPos> hazardCells(WorldView world, Staircase.Cut cut) {
        List<BlockPos> wet = new ArrayList<>();
        for (BlockPos cell : cut.cells()) {
            for (int[] d : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                BlockPos c = cell.offset(d[0], d[1], d[2]);
                if (world.isHazard(c.x(), c.y(), c.z()) && !wet.contains(c)) {
                    wet.add(c);
                }
            }
        }
        return wet;
    }

    /** Ticks spent walking to a cut's stand to block its water, so one it can't reach is closed unblocked. */
    private final java.util.Map<BlockPos, Integer> sealWalkTicks = new java.util.HashMap<>();
    /** How long it walks to block water before closing the path without: a minute and a half. */
    static final int SEAL_WALK_TICKS = 1_800;

    /** Ticks spent blocking each water or lava cell, so one that won't take a block is left. */
    private final java.util.Map<BlockPos, Integer> sealTicks = new java.util.HashMap<>();
    /** Why the stairs are closed below {@link #stairsClosedAt}, once water or lava is met beside a step - null till then. */
    private String stairsClosed;
    private int stairsClosedAt = Integer.MAX_VALUE;
    /** The head of the stairs {@link #stairsClosed} is for. */
    private BlockPos stairsClosedFor;

    /** Why making cut {@code k} would let in water or lava, or leave a body nothing to stand on. */
    static Optional<String> hazard(WorldView world, Staircase.Cut cut, int k) {
        String what = cut.tunnel() < 0 ? "step " + (k + 1) : cut.tunnel() == 0 ? "the main tunnel" : "branch " + cut.tunnel();
        for (BlockPos cell : cut.cells()) {
            for (int[] d : new int[][]{{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                if (world.isHazard(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])) {
                    return Optional.of("water or lava beside " + what + " at " + cell.offset(d[0], d[1], d[2]));
                }
            }
        }
        BlockPos floor = cut.feet().offset(0, -1, 0);
        if (!world.isSolid(floor.x(), floor.y(), floor.z())) {
            return Optional.of("no floor under " + what + " at " + floor + " - an open cave below");
        }
        return Optional.empty();
    }

    /**
     * Whether a block is being broken now. Between blocks the soldier's hands are free, so a routine
     * ranked above this one - making a stone pickaxe from the cobblestone the stairs gave - gets its
     * turn; one ranked below doesn't, since this one starts its next block first.
     */
    public synchronized boolean active() {
        return breacher.active();
    }

    /** Stops the dig under way. */
    public synchronized void stop() {
        breacher.stop();
        standAt = null;
    }
}
