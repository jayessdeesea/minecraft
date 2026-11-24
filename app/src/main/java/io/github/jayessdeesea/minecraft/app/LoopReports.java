package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.ActivityTrack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A soldier's own report that it's going round in a loop (operator, 2026-11-24: the soldiers should be self-reporting
 * looping behaviour, so it can be dug into): {@code [Loop]} lines, one when it has gone to and fro between two
 * activities {@link ActivityTrack#LOOP_RETURNS} times inside {@link ActivityTrack#LOOP_MILLIS}, one when it has broken
 * or put the same block {@link #SAME_CELL} times inside {@link #CELL_MILLIS} - each said again at as many more, never
 * every time. What it says is what the soldier knew: its report, its order, the routine that had its hands.
 */
final class LoopReports {

    private static final Logger LOG = LogManager.getLogger(LoopReports.class);

    /** So many breaks or placings of one cell by one soldier inside {@link #CELL_MILLIS} is a loop. */
    static final int SAME_CELL = 3;
    static final long CELL_MILLIS = 10 * 60_000;

    /** When each soldier last did each thing to each cell: by "soldier verb x y z". */
    private final Map<String, ArrayDeque<Long>> cells = new ConcurrentHashMap<>();

    /** {@code soldier}'s activity changed: said as its state always is, and as a loop when it's one. */
    static void changed(String soldier, ActivityTrack.Change change, Object report, String order) {
        LOG.info("[State] {}: {} (report {}, order {})", soldier, change.describe(), report, order);
        if (change.loops()) {
            LOG.warn("[Loop] {}: {} <-> {}, {} returns in {} min (report {}, order {})", soldier, change.to().label(), change.from().label(),
                    change.returnsLately(), ActivityTrack.LOOP_MILLIS / 60_000, report, order);
        }
    }

    /**
     * {@code soldier} did {@code verb} - "breaks", "puts" - to the cell at {@code now}, {@code routine} having its hands.
     *
     * @return how many times inside {@link #CELL_MILLIS}, this one counted
     */
    int cell(String soldier, String verb, int x, int y, int z, String routine, long now) {
        ArrayDeque<Long> times = cells.computeIfAbsent(soldier + " " + verb + " " + x + " " + y + " " + z, k -> new ArrayDeque<>());
        synchronized (times) {
            times.add(now);
            while (now - times.peek() > CELL_MILLIS) {
                times.poll();
            }
            if (times.size() % SAME_CELL == 0) {
                LOG.warn("[Loop] {}: {} ({}, {}, {}) {} times in {} min - {}", soldier, verb, x, y, z, times.size(), CELL_MILLIS / 60_000, routine);
            }
            return times.size();
        }
    }
}
