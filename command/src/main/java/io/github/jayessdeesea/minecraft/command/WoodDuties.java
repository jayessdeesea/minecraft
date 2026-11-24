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
    public enum Kind {
        WOODCUTTER, PLANTER,
        /** A woodcutter with no strip left to work: it stands by, and fells nobody else's trees. */
        STANDBY
    }

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
     * point, each woodcutter on its first strip; none unless the objective is for wood.
     */
    public static Map<String, Duty> of(Optional<Objective> objective, List<String> roster) {
        return objective.filter(o -> o.task().equals(new Objective.Task.Gather(Resource.WOOD)))
                .map(o -> assign(roster, o.location())).orElse(Map.of());
    }

    /** {@code roster}'s duties in the forest round {@code forest}: woodcutters on strips, the last its planter. */
    static Map<String, Duty> assign(List<String> roster, Position forest) {
        return duties(roster, forest, strips(roster, forest));
    }

    /**
     * The strip each of {@code roster}'s woodcutters starts on in the forest round {@code forest}: the whole of it cut
     * evenly across its longer side, one each, in roster order - all but the last of two or more, its planter.
     */
    public static Map<String, Box> strips(List<String> roster, Position forest) {
        Map<String, Box> strips = new LinkedHashMap<>();
        if (roster.isEmpty()) {
            return strips;
        }
        Box whole = whole(forest);
        int cutters = roster.size() >= 2 ? roster.size() - 1 : 1;
        List<Box> cut = whole.strips(cutters, whole.maxX() - whole.minX() >= whole.maxZ() - whole.minZ());
        for (int i = 0; i < cut.size(); i++) {
            strips.put(roster.get(i), cut.get(i));
        }
        return strips;
    }

    /**
     * {@code roster}'s duties with its woodcutters on {@code strips} as they stand - one with no strip stands by: it has
     * a duty still, so the squad's own order for wood isn't its to roam the others' strips with - and, with two or more,
     * the last its planter over the whole forest round {@code forest}.
     */
    public static Map<String, Duty> duties(List<String> roster, Position forest, Map<String, Box> strips) {
        Map<String, Duty> duties = new LinkedHashMap<>();
        List<String> cutters = roster.size() >= 2 ? roster.subList(0, roster.size() - 1) : roster;
        cutters.forEach(soldier -> duties.put(soldier, strips.containsKey(soldier) ? new Duty(soldier, Kind.WOODCUTTER, strips.get(soldier))
                : new Duty(soldier, Kind.STANDBY, whole(forest))));
        if (roster.size() >= 2) {
            String planter = roster.getLast();
            duties.put(planter, new Duty(planter, Kind.PLANTER, whole(forest)));
        }
        return Map.copyOf(duties);
    }

    /**
     * {@code strip} cut in two along a line between its trees - {@code trees}, each a standing tree's x and z - across its
     * longer side, half the trees each side: the lower part, then the upper. None if its trees don't stand on two lines
     * across it: there's no line to cut along. A strip cut out moves only its own woodcutter, onto half of the strip with
     * the most standing; the rest keep the trees they've begun (operator, 2026-11-18: each in a specific area).
     */
    public static Optional<List<Box>> split(Box strip, List<int[]> trees) {
        boolean alongX = strip.maxX() - strip.minX() >= strip.maxZ() - strip.minZ();
        int[] at = trees.stream().mapToInt(t -> alongX ? t[0] : t[1]).distinct().sorted().toArray();
        if (at.length < 2) {
            return Optional.empty();
        }
        int line = Math.floorDiv(at[at.length / 2 - 1] + at[at.length / 2], 2); // the last cell of the lower part
        return Optional.of(alongX
                ? List.of(new Box(strip.minX(), strip.minY(), strip.minZ(), line, strip.maxY(), strip.maxZ()),
                        new Box(line + 1, strip.minY(), strip.minZ(), strip.maxX(), strip.maxY(), strip.maxZ()))
                : List.of(new Box(strip.minX(), strip.minY(), strip.minZ(), strip.maxX(), strip.maxY(), line),
                        new Box(strip.minX(), strip.minY(), line + 1, strip.maxX(), strip.maxY(), strip.maxZ())));
    }

    /** {@code duties} as duty orders, their yield to {@code dump}: a woodcutter its strip, the planter the whole wood. */
    public static Map<String, DutyOrder> orders(Map<String, Duty> duties, Optional<Position> dump) {
        Map<String, DutyOrder> orders = new LinkedHashMap<>();
        duties.forEach((soldier, d) -> {
            if (d.kind() != Kind.STANDBY) { // standing by has no producer's order: nothing is owed of it
                orders.put(soldier, new DutyOrder(soldier,
                        d.kind() == Kind.PLANTER ? DutyOrder.Duty.PLANTER : DutyOrder.Duty.WOODCUTTER, Optional.of(d.area()), dump));
            }
        });
        return Map.copyOf(orders);
    }

    /** The forest worked round {@code forest}: {@link #REACH} each way, from under it to over it. */
    public static Box whole(Position forest) {
        int x = (int) Math.floor(forest.x());
        int y = (int) Math.floor(forest.y());
        int z = (int) Math.floor(forest.z());
        return new Box(x - REACH, y - BELOW, z - REACH, x + REACH, y + ABOVE, z + REACH);
    }
}
