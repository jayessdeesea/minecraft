package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The squad's half of doctrine's react-to-contact battle drill (docs/military-doctrine.md §8):
 * the soldier in contact fights (its own reflex), and the rest of the squad moves to support it.
 * Without this, a soldier more than a contact radius away keeps walking its formation slot while a
 * teammate fights several hostiles alone.
 *
 * <p>Pure and stateless: a function of the squad's SITREPs and its formation orders, run by the
 * squad loop every cycle, so support starts within a second - long before the squad leader's next
 * decision, which can still override the whole picture.
 */
public final class ReactToContact {

    /** Teammates within this horizontal distance of a soldier in contact move to support it. */
    public static final double SUPPORT_DISTANCE = 24;
    static final double SUPPORT_TOLERANCE = 3;
    static final double VERTICAL_TOLERANCE = 3;

    private ReactToContact() {
    }

    /**
     * {@code orders} (one per soldier in {@code squad}, same order), with every soldier who isn't
     * in contact, rejoining, or stuck - but is within {@link #SUPPORT_DISTANCE} of one in contact -
     * sent to the nearest such teammate instead, at its order's risk.
     */
    public static List<Order> orders(List<SoldierStatus> squad, List<Order> orders) {
        List<SoldierStatus> inContact = squad.stream().filter(s -> s.report() instanceof Report.InContact).toList();
        if (inContact.isEmpty()) {
            return orders;
        }
        List<Order> result = new ArrayList<>(orders);
        for (int i = 0; i < squad.size(); i++) {
            SoldierStatus soldier = squad.get(i);
            if (soldier.report() instanceof Report.InContact || soldier.report() instanceof Report.LinkingUp
                    || soldier.report() instanceof Report.Cantco) {
                continue;
            }
            Optional<SoldierStatus> nearest = inContact.stream()
                    .filter(c -> horizontal(c.position(), soldier.position()) <= SUPPORT_DISTANCE)
                    .min(Comparator.comparingDouble(c -> horizontal(c.position(), soldier.position())));
            if (nearest.isPresent()) {
                Position at = nearest.get().position();
                Risk risk = orders.get(i) instanceof Order.MoveTo move ? move.risk() : Risk.LOW;
                result.set(i, new Order.MoveTo(at.x(), at.y(), at.z(), SUPPORT_TOLERANCE, VERTICAL_TOLERANCE, risk));
            }
        }
        return List.copyOf(result);
    }

    private static double horizontal(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
