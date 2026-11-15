package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The one way everyone takes between two places on a work site - the keep's depot and its floor
 * (operator, 2026-11-15: a cheap route the pathfinding will prefer, kept up as the dig goes down):
 * a walk of steps no more than a block up or down, carved out of the ground it crosses - headroom
 * dug, never a block placed where it can be helped - so once it's cut, walking it costs nothing and
 * the pathfinder takes it over any stairs of its own. Pure: the world in, the route out.
 *
 * @param feet   the cells the feet pass through, from the start to the goal
 * @param toCut  the solid cells to dig out for it: headroom and the way through
 * @param floors the cells under its feet with no floor - a block each to put down
 */
public record AccessRoute(List<BlockPos> feet, List<BlockPos> toCut, List<BlockPos> floors) {

    public AccessRoute {
        feet = List.copyOf(feet);
        toCut = List.copyOf(toCut);
        floors = List.copyOf(floors);
    }

    /** Each step's own cost; and a block dug, and a floor put down, on top of it - placing is dearest. */
    static final double STEP = 1, DIG = 3, FLOOR = 20;
    /** How far across and up the search goes round its start. */
    static final int ACROSS = 32, UP = 12, DOWN = 24;

    /** Whether it's cut: nothing left to dig, no floor to put down. */
    public boolean cut() {
        return toCut.isEmpty() && floors.isEmpty();
    }

    /**
     * The cheapest route from {@code start} - a cell the feet stand in - to any cell {@code goal}
     * takes, crossing no cell {@code keepOut} holds but the goal itself, and nothing liquid. Empty
     * with none in reach of the search.
     */
    public static Optional<AccessRoute> plan(WorldView world, BlockPos start, Predicate<BlockPos> goal, Predicate<BlockPos> keepOut) {
        Map<BlockPos, Double> cost = new HashMap<>();
        Map<BlockPos, BlockPos> from = new HashMap<>();
        PriorityQueue<Map.Entry<BlockPos, Double>> open = new PriorityQueue<>(Map.Entry.comparingByValue());
        cost.put(start, 0.0);
        open.add(Map.entry(start, 0.0));
        BlockPos reached = null;
        while (!open.isEmpty()) {
            Map.Entry<BlockPos, Double> head = open.poll();
            BlockPos at = head.getKey();
            if (head.getValue() > cost.getOrDefault(at, Double.MAX_VALUE)) {
                continue;
            }
            if (!at.equals(start) && goal.test(at)) {
                reached = at;
                break;
            }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos next = at.offset(d[0], dy, d[1]);
                    if (Math.abs(next.x() - start.x()) > ACROSS || Math.abs(next.z() - start.z()) > ACROSS
                            || next.y() > start.y() + UP || next.y() < start.y() - DOWN) {
                        continue;
                    }
                    if (keepOut.test(next) && !goal.test(next)) {
                        continue;
                    }
                    Optional<Double> step = stepCost(world, at, next);
                    if (step.isEmpty()) {
                        continue;
                    }
                    double c = head.getValue() + step.get();
                    if (c < cost.getOrDefault(next, Double.MAX_VALUE)) {
                        cost.put(next, c);
                        from.put(next, at);
                        open.add(Map.entry(next, c));
                    }
                }
            }
        }
        if (reached == null) {
            return Optional.empty();
        }
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos p = reached; p != null; p = from.get(p)) {
            path.add(p);
        }
        Collections.reverse(path);
        Set<BlockPos> toCut = new LinkedHashSet<>();
        Set<BlockPos> floors = new LinkedHashSet<>();
        for (int i = 0; i < path.size(); i++) {
            BlockPos f = path.get(i);
            BlockPos prev = i == 0 ? f : path.get(i - 1);
            for (BlockPos c : clearFor(prev, f)) {
                if (world.isSolid(c.x(), c.y(), c.z())) {
                    toCut.add(c);
                }
            }
            if (!world.isSolid(f.x(), f.y() - 1, f.z())) {
                floors.add(f.offset(0, -1, 0));
            }
        }
        return Optional.of(new AccessRoute(path, List.copyOf(toCut), List.copyOf(floors)));
    }

    /** What a step from {@code from} to {@code to} costs, or empty when it can't be taken: liquid in the way. */
    static Optional<Double> stepCost(WorldView world, BlockPos from, BlockPos to) {
        double c = STEP;
        for (BlockPos cell : clearFor(from, to)) {
            // Nothing liquid, and never through a depot's table, furnace or chest.
            if (world.isHazard(cell.x(), cell.y(), cell.z()) || world.workstation(cell.x(), cell.y(), cell.z()).isPresent()
                    || world.isChest(cell.x(), cell.y(), cell.z())) {
                return Optional.empty();
            }
            if (world.isSolid(cell.x(), cell.y(), cell.z())) {
                c += DIG;
            }
        }
        BlockPos under = to.offset(0, -1, 0);
        if (world.isHazard(under.x(), under.y(), under.z())) {
            return Optional.empty();
        }
        if (!world.isSolid(under.x(), under.y(), under.z())) {
            c += FLOOR;
        }
        return Optional.of(c);
    }

    /**
     * The cells a body needs open to step from {@code from} to {@code to}: the feet and head at
     * {@code to}; going up, the head room over {@code from} to jump; going down, the head room over
     * {@code to} to step down into it.
     */
    static List<BlockPos> clearFor(BlockPos from, BlockPos to) {
        List<BlockPos> cells = new ArrayList<>(List.of(to, to.offset(0, 1, 0)));
        if (to.y() > from.y()) {
            cells.add(from.offset(0, 2, 0));
        } else if (to.y() < from.y()) {
            cells.add(to.offset(0, 2, 0));
        }
        return cells;
    }
}
