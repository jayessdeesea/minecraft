package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic translation of a {@link SquadOrder} into each soldier's {@link Order} - the part of
 * squad leading that needs no judgement, so no LLM: the squad leader chooses objective, formation
 * and interval (seconds apart); this re-derives slots from where the squad actually is (run often,
 * so the formation follows the point man as he moves).
 *
 * <p>Soldier 0 is the point man: he heads for the objective. Everyone else's slot is offset from
 * the point man's <em>current</em> position along the direction of travel - ATP 3-21.8 ¶3-44
 * ("know their location in the formation relative to ... the team leader"), ¶3-47 (leader in
 * front, others echeloned behind). Each slot is a zone half an interval wide, inside which the
 * soldier picks its own footing (¶3-47 "the positions for all but the leader may vary").
 * docs/military-doctrine.md §2.
 */
public final class Formations {

    /** How close the point man aims to get - he's the squad's reference, so tighter than the slots. */
    static final double POINT_TOLERANCE = 1.5;

    private Formations() {
    }

    /** Orders positionally matched to {@code soldiers}, like {@link SquadLeader#decide}. */
    public static List<Order> assign(SquadOrder order, List<SoldierStatus> soldiers) {
        if (soldiers.isEmpty()) {
            return List.of();
        }
        Position objective = order.objective();
        Position point = soldiers.get(0).position();
        double[] forward = heading(point, objective, soldiers);
        double interval = order.interval().blocks();

        List<Order> orders = new ArrayList<>(soldiers.size());
        orders.add(new Order.MoveTo(objective.x(), objective.y(), objective.z(), POINT_TOLERANCE, order.verticalTolerance(), order.risk()));
        for (int i = 1; i < soldiers.size(); i++) {
            double[] offset = order.formation().offset(i, interval);
            double x = point.x() + forward[0] * offset[0] - forward[1] * offset[1];
            double z = point.z() + forward[1] * offset[0] + forward[0] * offset[1];
            orders.add(new Order.MoveTo(x, point.y(), z, interval / 2, order.verticalTolerance(), order.risk()));
        }
        return orders;
    }

    /**
     * Unit direction of travel in the x/z plane: point man toward the objective; once he's there,
     * the direction the rest of the squad approached from (so the formation doesn't spin); +z
     * (south) if even that is degenerate.
     */
    static double[] heading(Position point, Position objective, List<SoldierStatus> soldiers) {
        double dx = objective.x() - point.x();
        double dz = objective.z() - point.z();
        if (Math.hypot(dx, dz) < 1) {
            double cx = 0;
            double cz = 0;
            for (int i = 1; i < soldiers.size(); i++) {
                cx += soldiers.get(i).position().x();
                cz += soldiers.get(i).position().z();
            }
            int others = soldiers.size() - 1;
            dx = others == 0 ? 0 : point.x() - cx / others;
            dz = others == 0 ? 0 : point.z() - cz / others;
        }
        double length = Math.hypot(dx, dz);
        return length < 1e-9 ? new double[]{0, 1} : new double[]{dx / length, dz / length};
    }
}
