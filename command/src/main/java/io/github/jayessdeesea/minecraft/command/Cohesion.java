package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pace control (docs/military-doctrine.md §9): the point man halts while a soldier who can follow
 * is out of supporting distance, so the squad doesn't string out until the react-to-contact drill
 * has nobody to send. Formation slots are measured from the point man, so holding him holds the
 * whole squad's movement.
 *
 * <p>Who holds the squad and who doesn't is the design: a soldier that's merely behind (Moving,
 * Idle, InPosition) holds it; a stuck one (Cantco) doesn't - waiting for a soldier who can't come
 * would stall the mission forever, and freeing him is the squad leader's call ({@link Risk}) - nor
 * does a replacement linking up ({@link LinkUp}) or one in contact ({@link ReactToContact}).
 *
 * <p>Pure and stateless, run by the squad loop between {@link LinkUp} and {@link ReactToContact}.
 */
public final class Cohesion {

    /** Beyond this horizontal distance from the point man, a soldier who can follow halts the squad. */
    public static final double SUPPORTING_DISTANCE = ReactToContact.SUPPORT_DISTANCE;
    static final double HALT_TOLERANCE = 1.5;

    private Cohesion() {
    }

    /** Who the point man is waiting for, if anyone: the farthest soldier holding the squad. */
    public static Optional<SoldierStatus> waitingFor(List<SoldierStatus> squad) {
        if (squad.size() < 2) {
            return Optional.empty();
        }
        Position point = squad.getFirst().position();
        SoldierStatus farthest = null;
        for (SoldierStatus soldier : squad.subList(1, squad.size())) {
            boolean canFollow = !(soldier.report() instanceof Report.Cantco || soldier.report() instanceof Report.LinkingUp
                    || soldier.report() instanceof Report.InContact);
            if (canFollow && distance(point, soldier.position()) > SUPPORTING_DISTANCE
                    && (farthest == null || distance(point, soldier.position()) > distance(point, farthest.position()))) {
                farthest = soldier;
            }
        }
        return Optional.ofNullable(farthest);
    }

    /** {@code orders}, with the point man's replaced by a halt where he stands if anyone is holding the squad. */
    public static List<Order> orders(List<SoldierStatus> squad, List<Order> orders) {
        if (waitingFor(squad).isEmpty()) {
            return orders;
        }
        Position point = squad.getFirst().position();
        Risk risk = orders.getFirst() instanceof Order.MoveTo move ? move.risk() : Risk.LOW;
        List<Order> result = new ArrayList<>(orders);
        result.set(0, new Order.MoveTo(point.x(), point.y(), point.z(), HALT_TOLERANCE, ReactToContact.VERTICAL_TOLERANCE, risk));
        return List.copyOf(result);
    }

    /** Horizontal distance - what supporting distance means on the ground. */
    public static double distance(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
