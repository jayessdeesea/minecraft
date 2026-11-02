package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscortTest {

    private static final Position PRINCIPAL = new Position(100, 64, 100);

    private static SoldierStatus soldier(String name, double x, double z, Report report) {
        return new SoldierStatus(name, new Position(x, 64, z), new Order.Idle(), report, Optional.empty());
    }

    private static Order.MoveTo move(Order order) {
        return (Order.MoveTo) order;
    }

    @Test
    void theSquadFormsARingAroundThePrincipalSizedByTheInterval() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, 0, new Report.Moving()),
                soldier("Bob", 0, 0, new Report.Moving()), soldier("Carol", 0, 0, new Report.Moving()),
                soldier("Dan", 0, 0, new Report.Moving()));
        SquadOrder order = new SquadOrder(PRINCIPAL, Formation.WEDGE, Interval.NORMAL, 2, Risk.LOW,
                Map.of("Dan", Risk.MEDIUM), "escort");

        List<Order> orders = Escort.slots(squad, order, PRINCIPAL);

        assertEquals(100, move(orders.get(0)).x(), 1e-9);
        assertEquals(97, move(orders.get(0)).z(), 1e-9, "first soldier north, at half the 6-block interval");
        assertEquals(103, move(orders.get(1)).x(), 1e-9, "then east");
        assertEquals(103, move(orders.get(2)).z(), 1e-9, "then south");
        assertEquals(Risk.MEDIUM, move(orders.get(3)).risk());
    }

    @Test
    void theNearestFreeSoldierInterceptsEachThreatToThePrincipal() {
        List<SoldierStatus> squad = List.of(
                soldier("Alice", 100, 97, new Report.Moving()),
                soldier("Bob", 103, 100, new Report.InContact(1)),  // already fighting
                soldier("Carol", 100, 103, new Report.Moving()));
        List<Order> ring = Escort.slots(squad, new SquadOrder(PRINCIPAL, Formation.WEDGE, Interval.NORMAL, 2, Risk.LOW, "escort"), PRINCIPAL);
        Position zombie = new Position(110, 64, 100);   // 10 from the principal, nearest Bob - who's busy
        Position farAway = new Position(130, 64, 100);  // no threat to the principal

        List<Order> orders = Escort.intercept(squad, ring, PRINCIPAL, List.of(farAway, zombie));

        assertEquals(new Order.MoveTo(110, 64, 100, 2, 3, Risk.LOW), orders.get(0), "Alice is nearer than Carol");
        assertEquals(ring.get(1), orders.get(1));
        assertEquals(ring.get(2), orders.get(2), "Carol holds the ring");
    }
}
