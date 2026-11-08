package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryTest {

    private static SoldierStatus soldier(String name, Position at, Report report, int dirt) {
        Kit kit = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, false, dirt, false);
        return new SoldierStatus(name, at, new Order.Idle(), report, Optional.of(new Condition(20, 20, 0, Optional.of(kit))));
    }

    private static final Report STUCK = new Report.Cantco("no route from (7, 81, 0) to within 2 of (0, 64, 0)");
    private static final List<Order> IDLE = List.of(new Order.Idle(), new Order.Idle(), new Order.Idle());

    @Test
    void theNearestSquadmateWithBlocksIsSentToOneStrandedWithNone() {
        Position ledge = new Position(7.5, 81, 0.5);
        List<SoldierStatus> squad = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 64),
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", ledge, STUCK, 0));

        List<Order> orders = Recovery.orders(squad, IDLE);

        assertEquals(new Order.Idle(), orders.get(0));
        assertEquals(new Order.MoveTo(7.5, 81, 0.5, Recovery.TOLERANCE, Recovery.TOLERANCE, Risk.LOW), orders.get(1));
        assertEquals(new Order.Idle(), orders.get(2));
    }

    @Test
    void noneIsSentForOneWithBlocksToBuildItsOwnWayOrStuckForAnotherReason() {
        List<SoldierStatus> squad = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Carol", new Position(7, 81, 0), STUCK, 30),
                soldier("Dana", new Position(7, 81, 0), new Report.Cantco("can't make 1 wooden pickaxe"), 0));

        assertEquals(IDLE, Recovery.orders(squad, IDLE));
    }

    @Test
    void noneWithoutBlocksIsSentAndNoneInContact() {
        List<SoldierStatus> empty = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 4),
                soldier("Carol", new Position(4, 64, 0), new Report.InPosition(), 0),
                soldier("Dana", new Position(7, 81, 0), STUCK, 0));
        List<SoldierStatus> fighting = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InContact(1), 64),
                soldier("Carol", new Position(4, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", new Position(7, 81, 0), STUCK, 0));

        assertEquals(IDLE, Recovery.orders(empty, IDLE));
        assertEquals(IDLE, Recovery.orders(fighting, IDLE));
    }
}
