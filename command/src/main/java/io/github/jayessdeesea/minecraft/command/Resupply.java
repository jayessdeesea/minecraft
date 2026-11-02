package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resupply at the supply point (docs/military-doctrine.md §4): when the general has sent the squad
 * there - its objective within {@link #SUPPLY_RADIUS} of the point - each soldier short of kit goes
 * to the chest and draws what it lacks, on its own initiative within that intent. The drawing
 * itself is the soldier's ({@code bot.supply}); this drill only gets it there.
 *
 * <p>Pure and stateless like {@link ReactToContact}, and run before it in the squad loop, so a
 * fight at the supply point still takes priority.
 */
public final class Resupply {

    /** The squad is at the supply point when its objective is this close to it, horizontally. */
    public static final double SUPPLY_RADIUS = 16;
    static final double CHEST_TOLERANCE = 2;
    static final double VERTICAL_TOLERANCE = 1;

    private Resupply() {
    }

    /**
     * The supply point from its configuration, {@code "x,y,z"} - the chest's block - or empty when
     * the operator set none (an empty string).
     */
    public static Optional<Position> supplyPoint(String config) {
        if (config.isBlank()) {
            return Optional.empty();
        }
        String[] parts = config.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("a supply point is \"x,y,z\", not \"" + config + "\"");
        }
        return Optional.of(new Position(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim())));
    }

    /** Short of kit: nothing to fight with, or an empty armour slot. Unknown kit isn't short. */
    public static boolean isShort(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit)
                .map(kit -> kit.weapon().isEmpty() || kit.armour() < Kit.ARMOUR_SLOTS)
                .orElse(false);
    }

    /**
     * {@code orders} (one per soldier in {@code squad}, same order), with every soldier who is
     * short of kit and not in contact, rejoining or stuck sent to the chest - when there is a
     * supply point and the squad's {@code objective} is at it.
     */
    public static List<Order> orders(List<SoldierStatus> squad, List<Order> orders, Position objective,
                                     Optional<Position> supplyPoint) {
        if (supplyPoint.isEmpty() || Cohesion.distance(objective, supplyPoint.get()) > SUPPLY_RADIUS) {
            return orders;
        }
        Position chest = supplyPoint.get();
        List<Order> result = new ArrayList<>(orders);
        for (int i = 0; i < squad.size(); i++) {
            SoldierStatus soldier = squad.get(i);
            if (!isShort(soldier) || soldier.report() instanceof Report.InContact
                    || soldier.report() instanceof Report.LinkingUp || soldier.report() instanceof Report.Cantco) {
                continue;
            }
            Risk risk = orders.get(i) instanceof Order.MoveTo move ? move.risk() : Risk.LOW;
            result.set(i, new Order.MoveTo(Math.floor(chest.x()) + 0.5, chest.y(), Math.floor(chest.z()) + 0.5,
                    CHEST_TOLERANCE, VERTICAL_TOLERANCE, risk));
        }
        return List.copyOf(result);
    }
}
