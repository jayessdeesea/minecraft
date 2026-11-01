package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CohesionTest {

    private static final Order SLOT = new Order.MoveTo(0, 64, -80, 1.5, 3, Risk.LOW);

    private static SoldierStatus soldier(String name, double z, Report report) {
        return new SoldierStatus(name, new Position(0.5, 64, z), SLOT, report, Optional.empty());
    }

    @Test
    void aSquadWithinSupportingDistanceKeepsMoving() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, new Report.Moving()), soldier("Bob", 20, new Report.Moving()));

        assertEquals(List.of(SLOT, SLOT), Cohesion.orders(squad, List.of(SLOT, SLOT)));
    }

    @Test
    void thePointManHaltsForASoldierWhoFellBehind() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, new Report.Moving()), soldier("Bob", 40, new Report.Moving()));

        List<Order> orders = Cohesion.orders(squad, List.of(SLOT, SLOT));

        assertEquals(new Order.MoveTo(0.5, 64, 0, 1.5, 3, Risk.LOW), orders.get(0));
        assertEquals(SLOT, orders.get(1), "Bob keeps closing on his slot");
        assertEquals("Bob", Cohesion.waitingFor(squad).orElseThrow().name());
    }

    @Test
    void stuckRejoiningAndEngagedSoldiersDoNotHoldTheSquad() {
        List<SoldierStatus> squad = List.of(soldier("Alice", 0, new Report.Moving()),
                soldier("Bob", 40, new Report.Cantco("pit")), soldier("Carol", 60, new Report.LinkingUp()),
                soldier("Dave", 80, new Report.InContact(1)));

        assertEquals(List.of(SLOT, SLOT, SLOT, SLOT), Cohesion.orders(squad, List.of(SLOT, SLOT, SLOT, SLOT)));
    }
}
