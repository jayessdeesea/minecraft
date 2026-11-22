package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/**
 * A chunk flattened to a level, and its edges (operator, 2026-11-21): inside the chunk every column's top brought to the
 * level - dug down to it, filled up to it; outside, on each side whose slope is on, a ring stepped one block per block
 * out, so no wall stands round a chunk cut into a hill and no drop round one filled - a walk in and out with no steps
 * put down; on a side whose slope is off, nothing past the edge touched, and a step of more than a block to what's
 * there reported. A cell the force has made something of - a template's fence, gate, chest or torch - is never dug or
 * filled. Pure: the ground's tops handed in.
 *
 * @param dig   each column to dig down, and the top it's dug down to
 * @param fill  each column to fill up, and the top it's filled up to
 * @param steps each side's step to its neighbour more than a block high, said
 */
public record FlattenPlan(List<Column> dig, List<Column> fill, List<String> steps) {

    /** A column at {@code x}, {@code z}, from its top {@code from} to {@code to}. */
    public record Column(int x, int z, int from, int to) {
    }

    /** How far out a slope reaches at the most: past this, the ground's its own - as far as a flatten's bench ({@link Flatten#BENCH}). */
    public static final int SLOPE_MOST = Flatten.BENCH;

    /**
     * The plan for chunk ({@code chunkX}, {@code chunkZ}) brought to {@code level}, its edges by {@code slopes}, the ground's
     * top at each column {@code topAt} (the highest solid block's y), and {@code kept} each column the force has made
     * something of at a height - never dug into or filled over.
     */
    public static FlattenPlan of(int chunkX, int chunkZ, int level, Slopes slopes, IntBinaryOperator topAt, BiPredicate<Integer, Integer> kept) {
        List<Column> dig = new ArrayList<>();
        List<Column> fill = new ArrayList<>();
        for (Column c : reach(chunkX, chunkZ, level, slopes, kept)) {
            int top = topAt.applyAsInt(c.x(), c.z());
            if (top > c.to()) {
                dig.add(new Column(c.x(), c.z(), top, c.to()));
            } else if (top < c.from()) {
                fill.add(new Column(c.x(), c.z(), top, c.from()));
            }
        }
        int x0 = chunkX * 16, z0 = chunkZ * 16, x1 = x0 + 15, z1 = z0 + 15;
        List<String> steps = new ArrayList<>();
        side(steps, "north", !slopes.north(), x0, x1, z0 - 1, true, level, topAt);
        side(steps, "south", !slopes.south(), x0, x1, z1 + 1, true, level, topAt);
        side(steps, "west", !slopes.west(), z0, z1, x0 - 1, false, level, topAt);
        side(steps, "east", !slopes.east(), z0, z1, x1 + 1, false, level, topAt);
        return new FlattenPlan(List.copyOf(dig), List.copyOf(fill), List.copyOf(steps));
    }

    /** A side's step, if its slope is off and what's past its edge stands or falls more than a block from the level. */
    private static void side(List<String> steps, String name, boolean off, int from, int to, int at, boolean alongX, int level, IntBinaryOperator topAt) {
        if (!off) {
            return;
        }
        int most = 0;
        for (int i = from; i <= to; i++) {
            int top = alongX ? topAt.applyAsInt(i, at) : topAt.applyAsInt(at, i);
            most = Math.abs(top - level) > Math.abs(most) ? top - level : most;
        }
        if (Math.abs(most) > 1) {
            steps.add(String.format(Locale.ROOT, "the %s edge %d %s what's past it: no walk between", name, Math.abs(most), most > 0 ? "below" : "above"));
        }
    }

    /**
     * Every column the flatten of chunk ({@code chunkX}, {@code chunkZ}) to {@code level} works, as {@link #of} takes them:
     * inside and its slopes, not {@code kept} - each with the band its top is brought into, {@code from} the lowest
     * to {@code to} the highest: the level inside, a block wider each block out. What's over {@code to} is dug.
     */
    public static List<Column> reach(int chunkX, int chunkZ, int level, Slopes slopes, BiPredicate<Integer, Integer> kept) {
        int x0 = chunkX * 16, z0 = chunkZ * 16, x1 = x0 + 15, z1 = z0 + 15;
        List<Column> reach = new ArrayList<>();
        for (int x = x0 - SLOPE_MOST; x <= x1 + SLOPE_MOST; x++) {
            for (int z = z0 - SLOPE_MOST; z <= z1 + SLOPE_MOST; z++) {
                int out = Math.max(Math.max(x0 - x, x - x1), Math.max(z0 - z, z - z1)); // 0 inside, then blocks out
                if (out > 0 && !slopes.reaches(x < x0, x > x1, z < z0, z > z1) || kept.test(x, z)) {
                    continue;
                }
                reach.add(new Column(x, z, level - Math.max(0, out), level + Math.max(0, out)));
            }
        }
        return List.copyOf(reach);
    }

    /** Whether there's nothing left to dig or fill. */
    public boolean flat() {
        return dig.isEmpty() && fill.isEmpty();
    }

    /**
     * Whether there's nothing left to dig: nothing stands over the level inside, nor over the slope outside - the
     * flatten's own work. Its fills are the floor's inside, every cell of the level laid ({@link Objective.Task.Floor}).
     */
    public boolean cut() {
        return dig.isEmpty();
    }
}
