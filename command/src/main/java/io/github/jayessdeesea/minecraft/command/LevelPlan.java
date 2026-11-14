package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The keep's foreman's plan for one level (operator, 2026-11-14): the site comes down in lock step,
 * a level at a time, and at the start of each the foreman looks at what's left of it and who's on
 * hand, and gives each builder a rectangle of x and z to clear at that level - or none, when there's
 * less work than builders: a spire one block across is one builder's. The rectangles don't overlap,
 * hold near-equal shares of the level's blocks, and go to the builders nearest them, so nobody
 * crosses the site for three blocks and nobody stands idle beside a level half the squad could dig.
 *
 * <p>Stateless: a function of the level's columns and the builders' places. Nothing is kept from one
 * level to the next, or overnight - a builder lost, fighting or away is simply not in the next plan.
 * Plain Java, never a model: the squad leader appoints the foreman, the foreman plans.
 */
public final class LevelPlan {

    /** Blocks of a level a builder is worth sending for: fewer each, and fewer builders are sent. */
    public static final int MIN_SHARE = 4;

    private LevelPlan() {
    }

    /** A builder on hand: its name and where it stands. */
    public record Builder(String name, Position at) {
    }

    /** A column of the site with ground at the level. */
    public record Column(int x, int z) {
    }

    /**
     * Each builder's rectangles of {@code columns} at {@code level} - a box from the level up to
     * {@code top}, the sky over it its own to clear - for as many of {@code builders} as the level
     * has work for. A builder with none isn't in the map.
     */
    public static Map<String, List<Box>> plan(int level, int top, List<Column> columns, List<Builder> builders) {
        Map<String, List<Box>> plan = new LinkedHashMap<>();
        if (columns.isEmpty() || builders.isEmpty()) {
            return plan;
        }
        int parts = Math.max(1, Math.min(builders.size(), columns.size() / MIN_SHARE));
        List<List<Column>> shares = split(columns, parts);
        // The nearest pair first, each builder one share: the shortest walks, greedily.
        List<Builder> free = new ArrayList<>(builders);
        List<List<Column>> open = new ArrayList<>(shares);
        while (!open.isEmpty() && !free.isEmpty()) {
            Builder best = null;
            List<Column> bestShare = null;
            double bestDistance = Double.MAX_VALUE;
            for (Builder b : free) {
                for (List<Column> share : open) {
                    double d = distance(b.at(), share);
                    if (d < bestDistance) {
                        best = b;
                        bestShare = share;
                        bestDistance = d;
                    }
                }
            }
            free.remove(best);
            open.remove(bestShare);
            plan.computeIfAbsent(best.name(), n -> new ArrayList<>()).add(box(bestShare, level, top));
        }
        return plan;
    }

    /** {@code columns} in {@code parts} shares of near-equal size, each on its own side of a line of x or z. */
    static List<List<Column>> split(List<Column> columns, int parts) {
        if (parts <= 1 || columns.size() < 2) {
            return List.of(columns);
        }
        int minX = columns.stream().mapToInt(Column::x).min().orElseThrow(), maxX = columns.stream().mapToInt(Column::x).max().orElseThrow();
        int minZ = columns.stream().mapToInt(Column::z).min().orElseThrow(), maxZ = columns.stream().mapToInt(Column::z).max().orElseThrow();
        boolean alongX = maxX - minX >= maxZ - minZ;
        for (int attempt = 0; attempt < 2; attempt++, alongX = !alongX) {
            boolean byX = alongX;
            List<Column> sorted = columns.stream().sorted(Comparator.comparingInt((Column c) -> byX ? c.x() : c.z())
                    .thenComparingInt(c -> byX ? c.z() : c.x())).toList();
            int leftParts = parts / 2;
            int want = sorted.size() * leftParts / parts;
            // The cut falls between two different coordinates, so the shares' rectangles don't overlap:
            // the nearest such boundary to the share wanted.
            int cut = -1;
            for (int i = 1; i < sorted.size(); i++) {
                int a = byX ? sorted.get(i - 1).x() : sorted.get(i - 1).z(), b = byX ? sorted.get(i).x() : sorted.get(i).z();
                if (a != b && (cut < 0 || Math.abs(i - want) < Math.abs(cut - want))) {
                    cut = i;
                }
            }
            if (cut > 0) {
                List<List<Column>> out = new ArrayList<>(split(sorted.subList(0, cut), leftParts));
                out.addAll(split(sorted.subList(cut, sorted.size()), parts - leftParts));
                return out;
            }
        }
        return List.of(columns);
    }

    private static Box box(List<Column> share, int level, int top) {
        return new Box(share.stream().mapToInt(Column::x).min().orElseThrow(), level, share.stream().mapToInt(Column::z).min().orElseThrow(),
                share.stream().mapToInt(Column::x).max().orElseThrow(), Math.max(level, top), share.stream().mapToInt(Column::z).max().orElseThrow());
    }

    private static double distance(Position from, List<Column> share) {
        double x = share.stream().mapToInt(Column::x).average().orElse(0) + 0.5, z = share.stream().mapToInt(Column::z).average().orElse(0) + 0.5;
        return Math.hypot(from.x() - x, from.z() - z);
    }
}
