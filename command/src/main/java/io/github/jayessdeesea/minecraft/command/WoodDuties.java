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
        return objective.filter(o -> o.task().equals(new Objective.Task.Gather(Resource.WOOD)))
                .map(o -> assign(roster, o.location())).orElse(Map.of());
    }

    /** {@code roster}'s duties in the forest round {@code forest}: woodcutters on strips, the last its planter. */
    static Map<String, Duty> assign(List<String> roster, Position forest) {
        if (roster.isEmpty()) {
            return Map.of();
        }
        int x = (int) Math.floor(forest.x());
        int y = (int) Math.floor(forest.y());
        int z = (int) Math.floor(forest.z());
        Box whole = new Box(x - REACH, y - BELOW, z - REACH, x + REACH, y + ABOVE, z + REACH);
        int cutters = roster.size() >= 2 ? roster.size() - 1 : 1;
        List<Box> strips = whole.strips(cutters);
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
}
