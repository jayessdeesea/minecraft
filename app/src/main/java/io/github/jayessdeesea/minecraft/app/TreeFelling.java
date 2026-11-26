package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.Hands;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * The keep cutters' tree felling: the tree each is on, its patience with it, the trees let go as out of reach, and the
 * dig order that takes one tree down whole - logs and crown, its steps dug out after - then the next nearest.
 * What belongs to the keep (the logs fallen to pick up, the stumps to replant, the steps) comes in as arguments.
 */
final class TreeFelling {
    private static final Logger LOG = LogManager.getLogger(SoldierRunner.class);

    /** How far across from its first log a tree's logs are taken in: an oak's or a spruce's spread. */
    static final int TREE_ACROSS = 4;
    /** What takes a cutter's hands off its tree for a while without its tree being the trouble. */
    private static final Set<Hands.Routine> ERRANDS = java.util.EnumSet.of(Hands.Routine.SUPPLY_RUN, Hands.Routine.TORCHMAKER,
            Hands.Routine.RATIONS, Hands.Routine.SEALER, Hands.Routine.HUNTER, Hands.Routine.ARMOURER, Hands.Routine.DEPOSITOR,
            Hands.Routine.HANDOVER);
    /** How long a cutter works a tree with no log coming off it before it's let go. */
    static final long TREE_PATIENCE_MILLIS = 180_000;
    /** How long a cutter stands where its tree's logs fell, its scavenger taking them up. */
    static final long COLLECT_MILLIS = 15_000;
    /** How far down a felled trunk's column its logs are looked for. */
    static final int FALL_LOOK_DOWN = 24;
    /** How far down its trunk's column a felled tree's ground is looked for, to replant on. */
    static final int STUMP_LOOK_DOWN = 4;
    /** How long a cutter tries to replant a stump - a sapling from the leaves it dug, or broken for - before it goes on. */
    static final long STUMP_PLANT_MILLIS = 90_000;
    /** The most logs taken as one tree. */
    static final int TREE_LOGS = 96;
    /** Leaves over a tree's top log, dug with it: a spruce's or an oak's crown. */
    static final int TREE_CROWN = 3;
    /** How far round where it's sent a cutter looks for its next tree. */
    static final int TREE_LOOK = 8;
    /** The tallest a tree is looked for over the ground: a giant spruce. */
    static final int TREE_TALLEST = 32;

    private final WorldView world;
    private final LongSupplier clock;

    /** The tree each keep cutter is felling: its logs as first found - the dig is its box until they're down and its steps out. */
    private final Map<String, Set<BlockPos>> felling = new ConcurrentHashMap<>();
    /** Logs left on each cutter's tree when last one came off, and when - its patience with it. */
    private final Map<String, Integer> fellingLeft = new ConcurrentHashMap<>();
    private final Map<String, Long> fellingSince = new ConcurrentHashMap<>();
    /** Trees let go as out of reach: never handed out again this run. */
    private final Set<BlockPos> treesLeft = ConcurrentHashMap.newKeySet();

    /** Felling in {@code world}, on the time {@code clock} reads (the runner's clock can be swapped for a simulation's). */
    TreeFelling(WorldView world, LongSupplier clock) {
        this.world = world;
        this.clock = clock;
    }

    /** Whether the tree with {@code log} in it has been let go as out of reach. */
    boolean letGo(BlockPos log) {
        return treesLeft.contains(log);
    }

    /** Lets go of the tree, and the patience, of every cutter not in {@code cutters}. */
    void retainCutters(Collection<String> cutters) {
        felling.keySet().retainAll(cutters);
        fellingLeft.keySet().retainAll(cutters);
        fellingSince.keySet().retainAll(cutters);
    }

    /**
     * {@code cutter}'s dig of the tree it's felling, or the next tree nearest {@code at}: the box round
     * its logs and crown, only logs and leaves in it dug - the ground and anything else kept - and its
     * own steps kept while a log stands, dug out once none does. Empty with no tree near. The logs it
     * cut, its stump and the keep's steps are read and written in {@code keep}, {@code scaffolding};
     * {@code errandOf} says what a cutter's hands are held to.
     */
    Optional<Order> dig(String cutter, Position at, Set<Cell> except, KeepState keep, Set<BlockPos> scaffolding,
                        Function<String, Hands.Routine> errandOf) {
        // The logs it cut picked up first: they fall to the ground under the trunk, and a cutter gone on to
        // its next tree leaves them lying. It stands where they fell while its scavenger takes them up.
        BlockPos fell = keep.collect.get(cutter);
        if (fell != null) {
            if (clock.getAsLong() - keep.collectSince.getOrDefault(cutter, 0L) < COLLECT_MILLIS) {
                return Optional.of(new Order.MoveTo(fell.x() + 0.5, fell.y(), fell.z() + 0.5, 2, 3, Risk.LOW));
            }
            keep.collect.remove(cutter);
            keep.collectSince.remove(cutter);
        }
        // Its stump replanted before the next tree: it stands on it, its forester plants it (KeepState.stumps).
        BlockPos stump = keep.stumps.get(cutter);
        if (stump != null) {
            long since = keep.stumpSince.getOrDefault(cutter, clock.getAsLong());
            if (TreeFarm.planted(world, stump) || clock.getAsLong() - since > STUMP_PLANT_MILLIS) {
                if (!TreeFarm.planted(world, stump)) {
                    LOG.warn("[Keep] {} couldn't replant the stump at {} in {} s - left for the leaves' saplings", cutter, stump,
                            STUMP_PLANT_MILLIS / 1000);
                }
                keep.stumps.remove(cutter);
                keep.stumpSince.remove(cutter);
            } else {
                return Optional.of(new Order.MoveTo(stump.x() + 0.5, stump.y(), stump.z() + 0.5, 2, 3, Risk.LOW));
            }
        }
        Set<BlockPos> tree = felling.get(cutter);
        // A tree nobody can reach - a trunk left floating over a shaft - is let go once no log has come off
        // it a while, and never handed out again.
        long now = clock.getAsLong();
        if (tree != null) {
            int left = (int) tree.stream().filter(c -> world.isLog(c.x(), c.y(), c.z())).count();
            Integer was = fellingLeft.get(cutter);
            // Its patience runs only while it's at the tree's work, not on an errand: a cutter away making
            // tools or fetching iron isn't leaving a tree it never reached.
            Hands.Routine errand = errandOf.apply(cutter);
            if (errand != null && ERRANDS.contains(errand)) {
                fellingSince.put(cutter, now);
            }
            if (was == null || left < was) {
                fellingLeft.put(cutter, left);
                fellingSince.put(cutter, now);
            } else if (left > 0 && now - fellingSince.getOrDefault(cutter, now) > TREE_PATIENCE_MILLIS) {
                LOG.warn("[Keep] {} leaves the tree at {}: no log off it in {} s - out of reach", cutter,
                        tree.stream().min(Comparator.comparingInt(BlockPos::y)).orElseThrow(), TREE_PATIENCE_MILLIS / 1000);
                treesLeft.addAll(tree);
                felling.remove(cutter);
                fellingLeft.remove(cutter);
                tree = null;
            }
        }
        boolean standing = tree != null && tree.stream().anyMatch(c -> world.isLog(c.x(), c.y(), c.z()));
        Box held = tree == null ? null : treeBox(tree);
        boolean steps = held != null && !standing && scaffolding.stream().anyMatch(c -> held.contains(c.x(), c.y(), c.z())
                && world.isSolid(c.x(), c.y(), c.z()));
        Box box = held;
        if (!standing && !steps) {
            if (tree != null) {
                BlockPos lowest = tree.stream().min(Comparator.comparingInt(BlockPos::y)).orElseThrow();
                LOG.info("[Keep] {} has felled the tree at {} and dug out its steps", cutter, lowest);
                felling.remove(cutter);
                // The ground its trunk stood on, down its column: its lowest log can stand over air.
                BlockPos foot = lowest;
                for (int down = 0; down < STUMP_LOOK_DOWN && !world.isSolid(foot.x(), foot.y() - 1, foot.z()); down++) {
                    foot = foot.offset(0, -1, 0);
                }
                // Where its logs fell: the ground under the trunk, however far down.
                BlockPos ground = foot;
                for (int down = 0; down < FALL_LOOK_DOWN && !world.isSolid(ground.x(), ground.y() - 1, ground.z()); down++) {
                    ground = ground.offset(0, -1, 0);
                }
                keep.collect.put(cutter, ground);
                keep.collectSince.put(cutter, clock.getAsLong());
                // Replanted where it stood, its headroom dug clear with its crown (operator, 2026-11-17).
                if (world.isSoil(foot.x(), foot.y() - 1, foot.z()) && !world.isSolid(foot.x(), foot.y(), foot.z())) {
                    keep.stumps.put(cutter, foot);
                    keep.stumpSince.put(cutter, clock.getAsLong());
                    return Optional.of(new Order.MoveTo(foot.x() + 0.5, foot.y(), foot.z() + 0.5, 2, 3, Risk.LOW));
                }
            }
            // A tree of its own: never one another cutter is felling (work in parallel - nobody on another's
            // share).
            Set<BlockPos> others = new HashSet<>();
            felling.forEach((other, logs) -> {
                if (!other.equals(cutter)) {
                    others.addAll(logs);
                }
            });
            tree = nearestLog(at, others).map(this::treeAt).orElse(null);
            if (tree == null) {
                felling.remove(cutter);
                return Optional.empty();
            }
            felling.put(cutter, tree);
            fellingLeft.put(cutter, tree.size());
            fellingSince.put(cutter, now);
            box = treeBox(tree);
            standing = true;
            LOG.info("[Keep] {} fells the tree at {}: {} log(s), dug as {}", cutter, tree.stream().min(Comparator.comparingInt(BlockPos::y)).orElseThrow(),
                    tree.size(), box);
        }
        Set<Cell> kept = new HashSet<>(except);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    boolean wood = world.isLog(x, y, z) || world.leaves(x, y, z).isPresent();
                    boolean step = scaffolding.contains(new BlockPos(x, y, z));
                    if (world.isSolid(x, y, z) && !wood && (standing || !step)) {
                        kept.add(new Cell(x, y, z));
                    } else if (step && !standing) {
                        kept.remove(new Cell(x, y, z));
                    }
                }
            }
        }
        return Optional.of(new Order.Dig(box, kept));
    }

    /** The log nearest {@code at}, within {@link #TREE_LOOK} across and up or down. */
    Optional<BlockPos> nearestLog(Position at, Set<BlockPos> held) {
        int ax = (int) Math.floor(at.x()), ay = (int) Math.floor(at.y()), az = (int) Math.floor(at.z());
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = ax - TREE_LOOK; x <= ax + TREE_LOOK; x++) {
            for (int z = az - TREE_LOOK; z <= az + TREE_LOOK; z++) {
                for (int y = ay - TREE_LOOK; y <= ay + TREE_LOOK; y++) {
                    double d = Math.hypot(x - ax, z - az) + Math.abs(y - ay) * 0.5;
                    BlockPos cell = new BlockPos(x, y, z);
                    if (d < bestDistance && world.isLog(x, y, z) && !treesLeft.contains(cell) && !held.contains(cell)) {
                        best = new BlockPos(x, y, z);
                        bestDistance = d;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Every log joined to {@code log}, within {@link #TREE_ACROSS} across of it: one tree. */
    Set<BlockPos> treeAt(BlockPos log) {
        Set<BlockPos> tree = new HashSet<>(List.of(log));
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>(List.of(log));
        while (!open.isEmpty() && tree.size() < TREE_LOGS) {
            BlockPos c = open.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = c.offset(dx, dy, dz);
                        if (Math.abs(n.x() - log.x()) <= TREE_ACROSS && Math.abs(n.z() - log.z()) <= TREE_ACROSS
                                && world.isLog(n.x(), n.y(), n.z()) && tree.add(n)) {
                            open.add(n);
                        }
                    }
                }
            }
        }
        return Set.copyOf(tree);
    }

    /** The box round {@code tree}'s logs and its crown. */
    static Box treeBox(Set<BlockPos> tree) {
        return new Box(tree.stream().mapToInt(BlockPos::x).min().orElseThrow() - 2, tree.stream().mapToInt(BlockPos::y).min().orElseThrow(),
                tree.stream().mapToInt(BlockPos::z).min().orElseThrow() - 2, tree.stream().mapToInt(BlockPos::x).max().orElseThrow() + 2,
                tree.stream().mapToInt(BlockPos::y).max().orElseThrow() + TREE_CROWN, tree.stream().mapToInt(BlockPos::z).max().orElseThrow() + 2);
    }
}
