package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Recovery;
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
    /** The soldiers quarrying stone for their way. */
    private final Set<String> waying = ConcurrentHashMap.newKeySet();
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
        return jobs.containsKey(soldier) || waying.contains(soldier);
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
        // With no route and too few blocks to make one with, but a pickaxe: stone quarried near it, by the gatherer's own
        // stands - ones its routes reach - to Recovery.WAY_BLOCKS. Live (field512-535), Eli's gap in his gallery at y=20
        // was five cells over a cave, and a single stone picked by distance stood behind granite he couldn't stand by.
        // Timed from its first "no route", where it stood, till it moves off that spot: live (field530), Eli's "no route"
        // broke for a moment's "Mining" between, and a timer kept by the report never ran.
        boolean blockless = pickaxe && kit.map(k -> k.stone() + k.dirt() < Recovery.WAY_BLOCKS).orElse(false);
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
        // Its stone first, over its own work and any litter: the work waits on the route, and litter elsewhere is no
        // nearer than its goal. Live (field528), Eli was handed litter he had no route to, again and again.
        if (since != null && now - since.getValue() >= GIVE_UP_MILLIS) {
            jobs.remove(soldier);
            if (waying.add(soldier)) {
                LOG.info("[Police] {} quarries stone for its way - no route, and {} blocks to make one with", soldier,
                        kit.map(k -> k.stone() + k.dirt()).orElse(0));
            }
            int x = (int) Math.floor(at.x()), y = (int) Math.floor(at.y()), z = (int) Math.floor(at.z());
            return Optional.of(new Order.Gather(io.github.jayessdeesea.minecraft.command.Resource.STONE,
                    new Box(x - WAY_REACH, y - WAY_REACH, z - WAY_REACH, x + WAY_REACH, y + WAY_REACH, z + WAY_REACH), Recovery.WAY_BLOCKS));
        }
        waying.remove(soldier);
        Optional<BlockPos> had = Optional.ofNullable(jobs.get(soldier)).map(Job::step);
        Optional<BlockPos> step = step(soldier, busy, withoutWork, world, now, skip -> {
            Set<BlockPos> not = new java.util.HashSet<>(kept);
            not.addAll(skip);
            placedAt.forEach((c, t) -> {
                if (now - t < LITTER_MILLIS) {
                    not.add(c);
                }
            });
            return litter(world, steps, at, bodies, pickaxe, not);
        });
        step.filter(c -> !step.equals(had)).ifPresent(c -> LOG.info("[Police] {} takes back a step left at {} {} {}", soldier, c.x(), c.y(), c.z()));
        return step.map(c -> new Order.Dig(new Box(c.x(), c.y(), c.z(), c.x(), c.y(), c.z()), Set.of()));
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

    /** How far round it a soldier quarries stone for its way. */
    static final int WAY_REACH = 6;

    /** Whether {@code c} stands proud: open on three or four of its sides. */
    static boolean proud(WorldView world, BlockPos c) {
        int solid = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            solid += world.isSolid(c.x() + d[0], c.y(), c.z() + d[1]) ? 1 : 0;
        }
        return solid <= 1;
    }
}
