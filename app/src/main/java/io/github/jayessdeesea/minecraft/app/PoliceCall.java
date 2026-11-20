package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Police call (FM 7-21.13: a unit leaves its area as it found it): a soldier without work takes back the steps that
 * routes put down and left standing - litter - nearest first, the top of a stack first (operator, 2026-11-20: a block
 * is placed with intent; the steps left behind littered the camp, 11,745 of them never taken down). Only a step that
 * stands proud - open on at least three of its four sides, open over it - is litter: one flush with the ground filled a
 * hole, and taken back it would open the hole again. A soldier keeps its step till it's gone or it has other work; one
 * not gone in {@link #GIVE_UP_MILLIS} is passed over for {@link #PASSED_MILLIS}, by everyone. Its choice is pure over a
 * {@link WorldView}; the jobs in hand are this object's, one per runner.
 */
final class PoliceCall {

    /** How long a soldier is at one step before it's passed over: a dig that takes longer isn't getting done. */
    static final long GIVE_UP_MILLIS = 60_000;
    /** How long a step given up on is passed over. */
    static final long PASSED_MILLIS = 10 * 60_000;

    private static final Logger LOG = LoggerFactory.getLogger(PoliceCall.class);

    private record Job(BlockPos step, long since) {
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> passed = new ConcurrentHashMap<>();
    /**
     * Since when each soldier has reported a "no route" CANTCO with no block to make one with, by the reason: a minute
     * of it, it cuts a stone whatever its idle watch says - live (field526), Eli's squad read as resting, and his own
     * minute stood still was never alarmed.
     */
    private final Map<String, Map.Entry<Position, Long>> noRoute = new ConcurrentHashMap<>();
    /** How far a soldier moves off the spot its "no route" began at before that spell's over. */
    static final double MOVED_OFF = 2;

    /**
     * How long a step stands before it's litter: one put down lately may be a route's still - in the squad
     * simulation, the steps a soldier put down to reach litter were taken back as litter, and put down again.
     */
    static final long LITTER_MILLIS = 5 * 60_000;
    /** How far a soldier goes for litter: its own neighbourhood, not the map. */
    static final double REACH = 16;
    /**
     * How far above or below it a soldier goes for litter: its own level. Live (field519), Eli at the bottom of his
     * mine, y=20, was given the steps 43 blocks over him on the surface.
     */
    static final int REACH_UP_DOWN = 4;
    /** How near a body a step is left: it may be standing on it. */
    static final double BODY_CLEAR = 1.5;

    /** Whether {@code soldier} has a step in hand to take back. */
    boolean on(String soldier) {
        return jobs.containsKey(soldier);
    }

    /**
     * The dig for {@code soldier} at {@code at}, if it's to take back a step: {@link #step}, of {@code steps} put down
     * ({@code placedAt}, none for one kept from before a restart) {@link #LITTER_MILLIS} ago or more, as {@link #litter}
     * finds them for it.
     */
    Optional<Order> order(SoldierStatus status, boolean busy, boolean withoutWork, WorldView world, Set<BlockPos> steps, Map<BlockPos, Long> placedAt,
                          Collection<Position> bodies, Set<BlockPos> kept, long now) {
        String soldier = status.name();
        Position at = status.position();
        Optional<Kit> kit = status.condition().flatMap(Condition::kit);
        boolean pickaxe = kit.map(Kit::pickaxe).orElse(false);
        // With no route and no block to make one with, but a pickaxe: a stone off the wall by its head (wayBlock).
        // Timed from its first "no route", where it stood, till it moves off that spot or carries a block: live
        // (field530), Eli's "no route" broke for a moment's "Mining" between, and a timer kept by the report never ran.
        boolean blockless = pickaxe && kit.map(k -> k.stone() + k.dirt() == 0).orElse(false);
        boolean noWay = status.report() instanceof Report.Cantco(String why) && why.startsWith("no route");
        Map.Entry<Position, Long> since = noRoute.get(soldier);
        if (!blockless || since != null && Math.hypot(since.getKey().x() - at.x(), since.getKey().z() - at.z()) > MOVED_OFF) {
            noRoute.remove(soldier);
            since = null;
        }
        if (since == null && blockless && noWay) {
            since = Map.entry(at, now);
            noRoute.put(soldier, since);
        }
        boolean wayDue = since != null && now - since.getValue() >= GIVE_UP_MILLIS;
        Optional<BlockPos> had = Optional.ofNullable(jobs.get(soldier)).map(Job::step);
        // Its stone first, over its own work and any litter: the work waits on the route, and litter elsewhere is no
        // nearer than its goal. Live (field528), Eli was handed litter he had no route to, again and again.
        boolean due = wayDue;
        if (due) {
            jobs.computeIfPresent(soldier, (k, j) -> steps.contains(j.step()) ? null : j); // litter in hand dropped for it
        }
        Optional<BlockPos> step = step(soldier, busy && !due, withoutWork || due, world, now, skip -> {
            Set<BlockPos> not = new java.util.HashSet<>(kept);
            not.addAll(skip);
            placedAt.forEach((c, t) -> {
                if (now - t < LITTER_MILLIS) {
                    not.add(c);
                }
            });
            return due ? wayBlock(world, at) : litter(world, steps, at, bodies, pickaxe, not);
        });
        step.filter(c -> !step.equals(had)).ifPresent(c -> LOG.info(steps.contains(c) ? "[Police] {} takes back a step left at {} {} {}"
                : "[Police] {} cuts a stone for its way at {} {} {} - no route, and no block to make one with", soldier, c.x(), c.y(), c.z()));
        // A step left stands open to the sky, a ground dig's; a block for the way is in a wall, under rock - a tunnel's:
        // live (field532), Eli's stone as a ground dig read "0 left" and done at once.
        return step.map(c -> steps.contains(c) ? new Order.Dig(new Box(c.x(), c.y(), c.z(), c.x(), c.y(), c.z()), Set.of())
                : Order.Dig.of(Set.of(new io.github.jayessdeesea.minecraft.command.Cell(c.x(), c.y(), c.z()))));
    }

    /**
     * The step {@code soldier} is to take back at {@code now}: the one in hand, while it stands and the soldier has no
     * other work ({@code busy}); else, {@code withoutWork}, a fresh one from {@code find} not passed over.
     */
    Optional<BlockPos> step(String soldier, boolean busy, boolean withoutWork, WorldView world, long now,
                            java.util.function.Function<Set<BlockPos>, Optional<BlockPos>> find) {
        Job job = jobs.get(soldier);
        if (job != null && (busy || !world.isSolid(job.step().x(), job.step().y(), job.step().z()))) {
            jobs.remove(soldier);
            job = null;
        } else if (job != null && now - job.since() >= GIVE_UP_MILLIS) {
            passed.put(job.step(), now);
            jobs.remove(soldier);
            job = null;
        }
        if (job == null && !busy && withoutWork) {
            passed.values().removeIf(at -> now - at >= PASSED_MILLIS);
            Set<BlockPos> skip = new java.util.HashSet<>(passed.keySet());
            jobs.values().forEach(j -> skip.add(j.step()));
            Optional<BlockPos> found = find.apply(Set.copyOf(skip));
            if (found.isPresent()) {
                job = new Job(found.get(), now);
                jobs.put(soldier, job);
            }
        }
        return Optional.ofNullable(job).map(Job::step);
    }

    /**
     * The step for a soldier at {@code at} to take back, of {@code steps}: litter within {@link #REACH}, none under or
     * beside one of {@code bodies}, none of {@code kept} (a wall's, a hut's), soil, or stone if it carries a pickaxe.
     */
    static Optional<BlockPos> litter(WorldView world, Set<BlockPos> steps, Position at, Collection<Position> bodies, boolean pickaxe,
                                     Set<BlockPos> kept) {
        return steps.stream()
                .filter(c -> Math.hypot(c.x() + 0.5 - at.x(), c.z() + 0.5 - at.z()) <= REACH && Math.abs(c.y() - at.y()) <= REACH_UP_DOWN)
                .filter(c -> !kept.contains(c))
                .filter(c -> world.isSolid(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z()))
                .filter(c -> pickaxe || world.isSoil(c.x(), c.y(), c.z()))
                .filter(c -> proud(world, c))
                .filter(c -> bodies.stream().noneMatch(b -> Math.hypot(c.x() + 0.5 - b.x(), c.z() + 0.5 - b.z()) <= BODY_CLEAR
                        && b.y() >= c.y() && b.y() <= c.y() + 3))
                .min(Comparator.comparingDouble((BlockPos c) -> Math.hypot(c.x() + 0.5 - at.x(), c.z() + 0.5 - at.z()))
                        .thenComparing(Comparator.comparingInt(BlockPos::y).reversed()));
    }

    /** How far from its head a soldier looks for a block to cut for its way: its arm's reach. */
    static final int WAY_REACH = 4;

    /**
     * The block for a soldier at {@code at} to cut for one to bridge or step with: the nearest plain stone or soil within
     * its reach with a face open to dig at - it drops cobblestone or dirt, the blocks a route builds with - its own
     * height first, never the floor under its feet. Live (field512-531), Eli stood for good at a gap in his gallery's
     * floor at y=20, a pickaxe in hand and no block; the stone beside his head, andesite or tuff, wouldn't have served.
     */
    static Optional<BlockPos> wayBlock(WorldView world, Position at) {
        BlockPos head = BlockPos.containing(at.x(), at.y(), at.z()).offset(0, 1, 0);
        BlockPos floor = head.offset(0, -2, 0);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -WAY_REACH; dx <= WAY_REACH; dx++) {
            for (int dy = -WAY_REACH; dy <= WAY_REACH; dy++) {
                for (int dz = -WAY_REACH; dz <= WAY_REACH; dz++) {
                    BlockPos c = head.offset(dx, dy, dz);
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > WAY_REACH || c.equals(floor) || !(world.isStone(c.x(), c.y(), c.z()) || world.isSoil(c.x(), c.y(), c.z())) || !exposed(world, c)) {
                        continue;
                    }
                    double score = d + 0.1 * Math.abs(dy);
                    if (score < bestScore) {
                        best = c;
                        bestScore = score;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether {@code c} has a face open to dig at. */
    private static boolean exposed(WorldView world, BlockPos c) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            if (!world.isSolid(c.x() + d[0], c.y() + d[1], c.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code c} stands proud: open on three or four of its sides. */
    static boolean proud(WorldView world, BlockPos c) {
        int solid = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            solid += world.isSolid(c.x() + d[0], c.y(), c.z() + d[1]) ? 1 : 0;
        }
        return solid <= 1;
    }
}
