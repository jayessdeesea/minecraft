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
    private static final java.util.function.Function<SoldierStatus, Optional<List<Cell>>> NO_STAIR = s -> Optional.empty();
    private static final List<Order> IDLE = List.of(new Order.Idle(), new Order.Idle(), new Order.Idle());

    @Test
    void theNearestSquadmateWithBlocksIsSentToOneStrandedWithNone() {
        Position ledge = new Position(7.5, 81, 0.5);
        List<SoldierStatus> squad = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 64),
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", ledge, STUCK, 0));

        List<Order> orders = new Recovery().orders(squad, IDLE, NO_STAIR);

        assertEquals(new Order.Idle(), orders.get(0));
        assertEquals(new Order.MoveTo(7.5, 81, 0.5, Recovery.TOLERANCE, Recovery.TOLERANCE, Risk.LOW), orders.get(1));
        assertEquals(new Order.Idle(), orders.get(2));
    }

    @Test
    void withAStairUpLaidTheSquadmateWithAPickaxeDigsJustItsCells() {
        Position pocket = new Position(0.5, 60, 0.5);
        Kit pick = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, true, 0, false);
        List<SoldierStatus> squad = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 0),
                new SoldierStatus("Bob", new Position(3, 64, 0), new Order.Idle(), new Report.InPosition(),
                        Optional.of(new Condition(20, 20, 0, Optional.of(pick)))),
                soldier("Dana", pocket, STUCK, 0));
        List<Cell> stair = List.of(new Cell(0, 62, 0), new Cell(1, 61, 0), new Cell(1, 62, 0), new Cell(1, 63, 0), new Cell(2, 62, 0));

        List<Order> orders = new Recovery().orders(squad, IDLE, NO_STAIR, s -> Optional.of(stair));

        Order.Dig dig = (Order.Dig) orders.get(1);
        assertEquals(new Box(0, 61, 0, 2, 63, 0), dig.box());
        assertEquals(java.util.Set.of(new Cell(0, 61, 0), new Cell(0, 63, 0), new Cell(2, 61, 0), new Cell(2, 63, 0)), dig.except(),
                "every cell of the box but the stair's kept");
    }

    @Test
    void aSoldierIsFetchedOnlyOnceItsBeenStrandedTheReportsAsked() {
        Position ledge = new Position(7.5, 81, 0.5);
        List<SoldierStatus> stuck = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 64),
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", ledge, STUCK, 0));
        List<SoldierStatus> moving = List.of(stuck.get(0), stuck.get(1), soldier("Dana", ledge, new Report.Moving(), 0));
        Recovery recovery = new Recovery(3);

        assertEquals(IDLE, recovery.orders(stuck, IDLE, NO_STAIR));
        assertEquals(IDLE, recovery.orders(stuck, IDLE, NO_STAIR));
        assertEquals(IDLE, recovery.orders(moving, IDLE, NO_STAIR), "a report not stranded starts the count again");
        assertEquals(IDLE, recovery.orders(stuck, IDLE, NO_STAIR));
        assertEquals(IDLE, recovery.orders(stuck, IDLE, NO_STAIR));
        assertEquals(new Order.MoveTo(7.5, 81, 0.5, Recovery.TOLERANCE, Recovery.TOLERANCE, Risk.LOW),
                recovery.orders(stuck, IDLE, NO_STAIR).get(1), "three in a row");
    }

    @Test
    void withNoStairToLayASquadmateWithAPickaxeIsSentToCutTheWay() {
        Position pocket = new Position(7.5, 60, 0.5);
        Kit pick = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, true, 0, false);
        List<SoldierStatus> squad = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 0),
                new SoldierStatus("Bob", new Position(3, 64, 0), new Order.Idle(), new Report.InPosition(),
                        Optional.of(new Condition(20, 20, 0, Optional.of(pick)))),
                soldier("Dana", pocket, STUCK, 0));

        List<Order> orders = new Recovery().orders(squad, IDLE, NO_STAIR);

        assertEquals(new Order.MoveTo(7.5, 60, 0.5, Recovery.TOLERANCE, Recovery.TOLERANCE, Risk.LOW), orders.get(1));
        assertEquals(new Order.Idle(), orders.get(0), "no pickaxe, no blocks");
    }

    @Test
    void theSquadmateBuildsTheStairTheEngineerLaysFromWhereTheStrandedOneStands() {
        List<Cell> stair = List.of(new Cell(5, 64, 0), new Cell(6, 65, 0));
        List<SoldierStatus> squad = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", new Position(7.5, 81, 0.5), STUCK, 0));

        List<Order> orders = new Recovery().orders(squad, List.of(new Order.Idle(), new Order.Idle()),
                s -> s.name().equals("Dana") ? Optional.of(stair) : Optional.empty());

        assertEquals(List.of(new Order.Place(stair), new Order.Idle()), orders);
    }

    @Test
    void aRecoveryLastsThroughTheStrandedOnesFlickeringReportUntilItsMovedOffWhereItWasStuck() {
        Recovery recovery = new Recovery();
        List<Cell> stair = List.of(new Cell(5, 64, 0));
        int[] laidOut = {0};
        java.util.function.Function<SoldierStatus, Optional<List<Cell>>> engineer = s -> {
            laidOut[0]++;
            return Optional.of(stair);
        };
        List<Order> idle = List.of(new Order.Idle(), new Order.Idle(), new Order.Idle());
        Position ledge = new Position(7.5, 81, 0.5);

        recovery.orders(List.of(soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Carol", new Position(4, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", ledge, STUCK, 0)), idle, engineer);
        // Trying its route again, it reports moving; Carol, the nearer, building, carries fewer than it takes to be picked.
        List<Order> trying = recovery.orders(List.of(soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Carol", new Position(5, 65, 0), new Report.InPosition(), 10),
                soldier("Dana", ledge, new Report.Moving(), 0)), idle, engineer);
        List<Order> down = recovery.orders(List.of(soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Carol", new Position(5, 65, 0), new Report.InPosition(), 0),
                soldier("Dana", new Position(4.5, 78, 0.5), new Report.Moving(), 0)), idle, engineer);

        assertEquals(List.of(new Order.Idle(), new Order.Place(stair), new Order.Idle()), trying);
        assertEquals(idle, down);
        assertEquals(1, laidOut[0]);
    }

    @Test
    void noneIsSentForOneWithBlocksToBuildItsOwnWayOrStuckForAnotherReason() {
        List<SoldierStatus> squad = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Carol", new Position(7, 81, 0), STUCK, 30),
                soldier("Dana", new Position(7, 81, 0), new Report.Cantco("can't make 1 wooden pickaxe"), 0));

        assertEquals(IDLE, new Recovery().orders(squad, IDLE, NO_STAIR));
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

        assertEquals(IDLE, new Recovery().orders(empty, IDLE, NO_STAIR));
        assertEquals(IDLE, new Recovery().orders(fighting, IDLE, NO_STAIR));
    }
}
