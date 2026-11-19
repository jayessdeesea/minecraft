package io.github.jayessdeesea.minecraft.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The squad leader's wood duties (operator, 2026-11-18; docs/echelons/tenets.md, the squad leader owns
 * each producer's duty): a squad sent for wood works the forest round its objective as woodcutters, each
 * on a strip of its own - no two on one tree - felling, picking up what drops and taking it to the
 * stockpile; and, with two or more, the last on the roster is its planter, over the whole of it, putting
 * saplings back where the felling opened the ground. Pure: the soldier only does the how.
 */
public final class WoodDuties {

    /** A soldier's part in the wood. */
    public enum Kind { WOODCUTTER, PLANTER }

    /** {@code soldier}'s duty: its kind and the ground it works. */
    public record Duty(String soldier, Kind kind, Box area) {

        /** The concrete order a woodcutter works to - a load of logs from its strip; none for a planter. */
        public Optional<Order> order() {
            return kind == Kind.WOODCUTTER ? Optional.of(new Order.Gather(Resource.WOOD, area, LOAD)) : Optional.empty();
        }

        /** Where its ground's middle is. */
        public Position centre() {
            return new Position((area.minX() + area.maxX()) / 2.0 + 0.5, (area.minY() + area.maxY()) / 2.0,
                    (area.minZ() + area.maxZ()) / 2.0 + 0.5);
        }
    }

    /** How far the forest is worked round the objective, each way. */
    public static final int REACH = 24;
    /** How far under and over the objective's height a tree is worked. */
    static final int BELOW = 8;
    static final int ABOVE = 24;
    /** A woodcutter's load before it's taken to the stockpile: what the depositor's trip is made for. */
    public static final int LOAD = 16;

    private WoodDuties() {
    }

    /**
     * The duties of {@code roster} - its soldiers in roster order - sent for wood at {@code objective}'s
     * point; none unless the objective is for wood.
     */
    public static Map<String, Duty> of(Optional<Objective> objective, List<String> roster) {
        return of(objective, roster, List.of());
    }

    /**
     * As {@link #of(Optional, List)}, with the strips its woodcutters have found cut out - {@code cutOut} - taken off the
     * forest's edges before it's cut into strips again, so a woodcutter isn't kept on ground with nothing left to fell
     * (operator, 2026-11-18: the squad leader closes the gap). Live (field474, field475), a squad's woodcutters were all
     * behind their shares in a forest cut out across most of its width.
     */
    public static Map<String, Duty> of(Optional<Objective> objective, List<String> roster, List<Box> cutOut) {
        return objective.filter(o -> o.task().equals(new Objective.Task.Gather(Resource.WOOD)))
                .map(o -> assign(roster, o.location(), cutOut)).orElse(Map.of());
    }

    /** {@code roster}'s duties in the forest round {@code forest}: woodcutters on strips, the last its planter. */
    static Map<String, Duty> assign(List<String> roster, Position forest) {
        return assign(roster, forest, List.of());
    }

    /**
     * {@code roster}'s duties in the forest round {@code forest}: woodcutters on strips of what's left of it once
     * {@code cutOut} is taken off its edges ({@link #worked}), the last its planter, over the whole.
     */
    static Map<String, Duty> assign(List<String> roster, Position forest, List<Box> cutOut) {
        if (roster.isEmpty()) {
            return Map.of();
        }
        Box whole = whole(forest);
        int cutters = roster.size() >= 2 ? roster.size() - 1 : 1;
        // Across the whole forest's way, whatever's left of it: strips cut the other way would lie across the cut-out ones.
        boolean alongX = whole.maxX() - whole.minX() >= whole.maxZ() - whole.minZ();
        List<Box> parts = worked(whole, cutOut);
        List<Box> strips = new java.util.ArrayList<>();
        int[] shares = shares(parts.stream().mapToInt(p -> alongX ? p.maxX() - p.minX() + 1 : p.maxZ() - p.minZ() + 1).toArray(), cutters);
        for (int i = 0; i < parts.size(); i++) {
            if (shares[i] > 0) {
                strips.addAll(parts.get(i).strips(shares[i], alongX));
            }
        }
        Map<String, Duty> duties = new LinkedHashMap<>();
        for (int i = 0; i < strips.size(); i++) {
            duties.put(roster.get(i), new Duty(roster.get(i), Kind.WOODCUTTER, strips.get(i)));
        }
        if (roster.size() >= 2) {
            String planter = roster.getLast();
            duties.put(planter, new Duty(planter, Kind.PLANTER, whole));
        }
        return Map.copyOf(duties);
    }

    /** {@code duties} as duty orders, their yield to {@code dump}: a woodcutter its strip, the planter the whole wood. */
    public static Map<String, DutyOrder> orders(Map<String, Duty> duties, Optional<Position> dump) {
        Map<String, DutyOrder> orders = new LinkedHashMap<>();
        duties.forEach((soldier, d) -> orders.put(soldier, new DutyOrder(soldier,
                d.kind() == Kind.PLANTER ? DutyOrder.Duty.PLANTER : DutyOrder.Duty.WOODCUTTER, Optional.of(d.area()), dump)));
        return Map.copyOf(orders);
    }

    /** The forest worked round {@code forest}: {@link #REACH} each way, from under it to over it. */
    public static Box whole(Position forest) {
        int x = (int) Math.floor(forest.x());
        int y = (int) Math.floor(forest.y());
        int z = (int) Math.floor(forest.z());
        return new Box(x - REACH, y - BELOW, z - REACH, x + REACH, y + ABOVE, z + REACH);
    }

    /**
     * {@code whole} less {@code cutOut}: the runs of it along its longer side - the way its strips ({@link Box#strips})
     * are cut - that no strip found cut out covers, each a box, in order: ground no woodcutter is sent back to taken
     * off, at an end or in the middle - a strip cut out in the middle leaves the wood in two. All of it cut out, it's
     * {@code whole} again: the trees replanted there grow.
     */
    public static List<Box> worked(Box whole, List<Box> cutOut) {
        boolean alongX = whole.maxX() - whole.minX() >= whole.maxZ() - whole.minZ();
        int from = alongX ? whole.minX() : whole.minZ(), to = alongX ? whole.maxX() : whole.maxZ();
        boolean[] cut = new boolean[to - from + 1];
        for (Box c : cutOut) {
            int a = Math.max(from, alongX ? c.minX() : c.minZ()), b = Math.min(to, alongX ? c.maxX() : c.maxZ());
            for (int i = a; i <= b; i++) {
                cut[i - from] = true;
            }
        }
        List<Box> parts = new java.util.ArrayList<>();
        for (int i = 0; i < cut.length; i++) {
            if (cut[i] || i > 0 && !cut[i - 1]) {
                continue;
            }
            int j = i;
            while (j + 1 < cut.length && !cut[j + 1]) {
                j++;
            }
            int a = from + i, b = from + j;
            parts.add(alongX ? new Box(a, whole.minY(), whole.minZ(), b, whole.maxY(), whole.maxZ())
                    : new Box(whole.minX(), whole.minY(), a, whole.maxX(), whole.maxY(), b));
        }
        return parts.isEmpty() ? List.of(whole) : List.copyOf(parts);
    }

    /**
     * {@code cutters} shared over parts {@code widths} wide: one to each of the widest while there are fewer cutters than
     * parts; else one to each and the rest to whichever has the most width a cutter.
     */
    static int[] shares(int[] widths, int cutters) {
        int[] shares = new int[widths.length];
        Integer[] widest = java.util.stream.IntStream.range(0, widths.length).boxed()
                .sorted(java.util.Comparator.comparingInt((Integer i) -> -widths[i])).toArray(Integer[]::new);
        for (int k = 0; k < Math.min(cutters, widths.length); k++) {
            shares[widest[k]] = 1;
        }
        for (int left = cutters - widths.length; left > 0; left--) {
            int most = 0;
            for (int i = 1; i < widths.length; i++) {
                if ((double) widths[i] / (shares[i] + 1) > (double) widths[most] / (shares[most] + 1)) {
                    most = i;
                }
            }
            shares[most]++;
        }
        return shares;
    }
}
