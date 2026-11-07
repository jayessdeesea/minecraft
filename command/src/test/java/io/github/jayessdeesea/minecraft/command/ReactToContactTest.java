package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactToContactTest {

    private static final Order SLOT = new Order.MoveTo(0, 64, -50, 3, 3, Risk.MEDIUM);

    private static SoldierStatus soldier(String name, double x, Report report) {
        return new SoldierStatus(name, new Position(x, 64, 0), SLOT, report, Optional.empty());
    }

    @Test
    void withNobodyInContactTheFormationOrdersStand() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, new Report.Moving()), soldier("Bob", 10, new Report.Moving()));

        assertEquals(List.of(SLOT, SLOT), ReactToContact.orders(squad, List.of(SLOT, SLOT)));
    }

    @Test
    void teammatesInSupportingDistanceCloseOnTheSoldierInContact() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, new Report.InContact(3)),
                soldier("Bob", 10, new Report.Moving()), soldier("Carol", 20, new Report.Moving()));

        List<Order> orders = ReactToContact.orders(squad, List.of(SLOT, SLOT, SLOT));

        assertEquals(SLOT, orders.get(0), "the soldier in contact fights on its reflex");
        assertEquals(new Order.MoveTo(0, 64, 0, 3, 3, Risk.MEDIUM, true), orders.get(1));
        assertEquals(new Order.MoveTo(0, 64, 0, 3, 3, Risk.MEDIUM, true), orders.get(2));
    }

    @Test
    void farAwayStuckAndRejoiningSoldiersAreNotPulledIn() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, new Report.InContact(1)),
                soldier("Bob", 100, new Report.Moving()), soldier("Carol", 5, new Report.Cantco("pit")),
                soldier("Dave", 5, new Report.LinkingUp()));

        assertEquals(List.of(SLOT, SLOT, SLOT, SLOT), ReactToContact.orders(squad, List.of(SLOT, SLOT, SLOT, SLOT)));
    }
}
