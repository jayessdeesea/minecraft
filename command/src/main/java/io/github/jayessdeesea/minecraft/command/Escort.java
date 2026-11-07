package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Close protection (docs/military-doctrine.md §10): the squad escorts a principal - a player the
 * operator named - wherever it goes. The principal moves every tick, far faster than the squad
 * leader decides, so the geometry is a drill, recomputed each squad cycle from where the principal
 * is now: {@link #slots} forms the soldiers in a ring around it, and {@link #intercept} sends the
 * nearest free soldier at each hostile that comes within {@link #THREAT_RADIUS} of it - the
 * soldier's own reflex takes over from there. The squad leader still chooses the interval (the
 * ring's size) and the risk.
 *
 * <p>Pure and stateless like {@link ReactToContact}, which still runs after these.
 */
public final class Escort {

    /** A hostile this close to the principal, horizontally, is a threat to it. */
    public static final double THREAT_RADIUS = 12;
    static final double SLOT_TOLERANCE = 1.5;
    static final double INTERCEPT_TOLERANCE = 2;
    /** Never closer than this to the principal, so the ring doesn't crowd the player. */
    static final double MIN_RADIUS = 2;

    private Escort() {
    }

    /** The ring's radius: half the interval, so TIGHT keeps the detail at arm's reach. */
    static double radius(Interval interval) {
        return Math.max(MIN_RADIUS, interval.blocks() / 2);
    }

    /**
     * One order per soldier in {@code squad}: evenly spaced around {@code principal}, the first
     * soldier to its north, at the order's interval, vertical tolerance and each soldier's risk.
     */
    public static List<Order> slots(List<SoldierStatus> squad, SquadOrder order, Position principal) {
        double radius = radius(order.interval());
        List<Order> orders = new ArrayList<>(squad.size());
        for (int i = 0; i < squad.size(); i++) {
            double angle = 2 * Math.PI * i / squad.size();
            orders.add(new Order.MoveTo(principal.x() + radius * Math.sin(angle), principal.y(),
                    principal.z() - radius * Math.cos(angle), SLOT_TOLERANCE, order.verticalTolerance(),
                    order.riskFor(squad.get(i).name()), true)); // a ring left behind is no protection
        }
        return List.copyOf(orders);
    }

    /**
     * {@code orders} with one soldier sent at each threat - hostiles within {@link #THREAT_RADIUS}
     * of the principal, nearest the principal first - taking the nearest soldier not already in
     * contact, rejoining or stuck. Threats beyond the number of free soldiers wait for the next
     * cycle; soldiers left over hold the ring.
     */
    public static List<Order> intercept(List<SoldierStatus> squad, List<Order> orders, Position principal,
                                        List<Position> hostiles) {
        List<Position> threats = hostiles.stream()
                .filter(h -> Cohesion.distance(h, principal) <= THREAT_RADIUS)
                .sorted(Comparator.comparingDouble(h -> Cohesion.distance(h, principal)))
                .toList();
        List<Order> result = new ArrayList<>(orders);
        boolean[] assigned = new boolean[squad.size()];
        for (Position threat : threats) {
            int nearest = -1;
            for (int i = 0; i < squad.size(); i++) {
                Report report = squad.get(i).report();
                if (assigned[i] || report instanceof Report.InContact || report instanceof Report.LinkingUp
                        || report instanceof Report.Cantco) {
                    continue;
                }
                if (nearest < 0 || Cohesion.distance(squad.get(i).position(), threat)
                        < Cohesion.distance(squad.get(nearest).position(), threat)) {
                    nearest = i;
                }
            }
            if (nearest < 0) {
                break;
            }
            assigned[nearest] = true;
            Risk risk = orders.get(nearest) instanceof Order.MoveTo move ? move.risk() : Risk.LOW;
            result.set(nearest, new Order.MoveTo(threat.x(), threat.y(), threat.z(), INTERCEPT_TOLERANCE,
                    ReactToContact.VERTICAL_TOLERANCE, risk, true));
        }
        return List.copyOf(result);
    }
}
