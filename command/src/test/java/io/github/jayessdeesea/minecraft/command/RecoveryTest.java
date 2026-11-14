package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryTest {

    private static SoldierStatus soldier(String name, Position at, Report report, int dirt) {
        Kit kit = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, false, dirt, false);
        return new SoldierStatus(name, at, new Order.Idle(), report, Optional.of(new Condition(20, 20, 0, Optional.of(kit))));
    }

    private static final Report STUCK = new Report.Cantco("no route from (7, 81, 0) to within 2 of (0, 64, 0)");
    private static final Function<SoldierStatus, Optional<List<Cell>>> NO_STAIR = s -> Optional.empty();
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

    /**
     * Reports and orders go soldier for soldier: live, a squad's reports held a detached soldier its
     * orders didn't, and recovery set the order at the stranded one's place in the reports - past
     * the end of the orders, every tick.
     */
    @Test
    void reportsThatDontMatchTheOrdersAreRefused() {
        List<SoldierStatus> four = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 64),
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Gus", new Position(30, 64, 0), new Report.InPosition(), 0),
                soldier("Dana", new Position(7.5, 81, 0.5), STUCK, 0));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> new Recovery().orders(four, IDLE, NO_STAIR));
        assertEquals("4 soldiers reported for 3 orders: [Alice, Bob, Gus, Dana]", refused.getMessage());
    }

    @Test
    void oneWithNoRouteIsStrandedWhateverItCarries() {
        assertEquals(true, Recovery.stranded(soldier("Dana", new Position(-53, 63, 51), STUCK, 2)),
                "2 dirt, and its own planner found no way with them");
        assertEquals(false, Recovery.stranded(soldier("Dana", new Position(-53, 63, 51), new Report.InPosition(), 0)));
    }

    @Test
    void oneStrandedWithNobodyToFetchItGoesDownToItsSquadAtMediumRisk() {
        List<SoldierStatus> squad = List.of(
                soldier("Gus", new Position(-50, 72, 53), STUCK, 0),
                soldier("Alice", new Position(-48, 68, 44), new Report.InPosition(), 0));

        Recovery recovery = new Recovery();
        List<Order> orders = recovery.orders(squad, List.of(new Order.Idle(), new Order.Idle()), s -> Optional.empty());

        Order.MoveTo down = new Order.MoveTo(-48, 68, 44, Recovery.ESCAPE_TOLERANCE, Recovery.ESCAPE_TOLERANCE, Risk.MEDIUM);
        assertEquals(down, orders.get(0));
        assertEquals(Optional.of(down), recovery.escape("Gus"));
        assertEquals(Optional.empty(), recovery.escape("Alice"));
    }

    @Test
    void oneStrandedWithLogsEnoughBuildsItsOwnStairFromTheTopDown() {
        Position ledge = new Position(0.5, 70, 0.5);
        Kit logs = new Kit(Optional.empty(), 0, 0, 0, 8, 0, 0, false);
        List<SoldierStatus> squad = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 0),
                new SoldierStatus("Eli", ledge, new Order.Idle(), STUCK, Optional.of(new Condition(8, 20, 0, Optional.of(logs)))));
        List<Cell> stair = List.of(new Cell(3, 64, 0), new Cell(3, 65, 0), new Cell(2, 66, 0), new Cell(2, 67, 0));

        List<Order> orders = new Recovery().orders(squad, List.of(new Order.Idle(), new Order.Idle()), s -> Optional.of(stair));

        assertEquals(new Order.Idle(), orders.get(0), "nobody fetched");
        assertEquals(new Order.Place(List.of(new Cell(1, 67, 0), new Cell(2, 67, 0), new Cell(2, 66, 0), new Cell(3, 65, 0), new Cell(3, 64, 0))),
                orders.get(1), "its own stair, from an anchor under the ledge's edge down");
    }

    @Test
    void theSquadLeaderIsShownOneBeingRecoveredAsRecoveringAtTheRear() {
        List<SoldierStatus> squad = List.of(
                soldier("Dana", new Position(7, 81, 0), STUCK, 0),
                soldier("Alice", new Position(0, 64, 0), new Report.InPosition(), 30));
        Recovery recovery = new Recovery();
        recovery.orders(squad, List.of(new Order.Idle(), new Order.Idle()), s -> Optional.of(List.of(new Cell(8, 64, 0))));

        List<SoldierStatus> shown = recovery.asRecovering(squad);

        assertEquals(List.of("Alice", "Dana"), shown.stream().map(SoldierStatus::name).toList(), "Dana at the rear");
        assertEquals(new Report.Recovering(), shown.get(1).report());
        assertEquals(squad, new Recovery().asRecovering(squad), "nobody being recovered: as it was");
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
        assertEquals(Set.of(new Cell(0, 61, 0), new Cell(0, 63, 0), new Cell(2, 61, 0), new Cell(2, 63, 0)), dig.except(),
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

    /**
     * With neither a stair to build nor a cut to dig, nobody is sent: live, two squadmates in turn
     * walked down to one in a dead end at the mine's bottom and were trapped beside it. The stranded
     * one tries its own way down to the nearest, as with nobody to fetch it.
     */
    @Test
    void withNothingLaidOutNobodyIsSentEvenWithAPickaxe() {
        Position pocket = new Position(7.5, 60, 0.5);
        Kit pick = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, true, 0, false);
        List<SoldierStatus> squad = List.of(
                soldier("Alice", new Position(20, 64, 0), new Report.InPosition(), 0),
                new SoldierStatus("Bob", new Position(3, 64, 0), new Order.Idle(), new Report.InPosition(),
                        Optional.of(new Condition(20, 20, 0, Optional.of(pick)))),
                soldier("Dana", pocket, STUCK, 0));

        List<Order> orders = new Recovery().orders(squad, IDLE, NO_STAIR);

        assertEquals(new Order.Idle(), orders.get(1), "Bob's pickaxe has nothing laid out to cut");
        assertEquals(new Order.Idle(), orders.get(0));
        assertEquals(new Order.MoveTo(3, 64, 0, Recovery.ESCAPE_TOLERANCE, Recovery.ESCAPE_TOLERANCE, Risk.MEDIUM), orders.get(2));
    }

    /**
     * Stranded below with no pickaxe and nobody carrying one to come, it digs the cut by hand - fists
     * break anything but bedrock (operator, 2026-11-14) - rather than wait; with a squadmate's pickaxe
     * on offer, that squadmate digs it from the top, twelve times as fast.
     */
    @Test
    void oneStrandedBelowWithNoPickaxeTunnelsUpByHandUnlessASquadmateWithOneCanCome() {
        Position pocket = new Position(0.5, 60, 0.5);
        Kit pick = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, true, 0, false);
        List<Cell> cut = List.of(new Cell(0, 62, 0), new Cell(1, 61, 0), new Cell(1, 62, 0));
        List<SoldierStatus> alone = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 0),
                soldier("Dana", pocket, STUCK, 0));
        List<SoldierStatus> helped = List.of(
                new SoldierStatus("Bob", new Position(3, 64, 0), new Order.Idle(), new Report.InPosition(),
                        Optional.of(new Condition(20, 20, 0, Optional.of(pick)))),
                soldier("Dana", pocket, STUCK, 0));

        Recovery byHand = new Recovery();
        assertEquals(List.of(new Order.Idle(), Recovery.dig(cut)),
                byHand.orders(alone, List.of(new Order.Idle(), new Order.Idle()), NO_STAIR, s -> Optional.of(cut)), "by hand, alone");
        assertEquals(true, byHand.ordered("Dana"));
        Recovery fetched = new Recovery();
        assertEquals(List.of(Recovery.dig(cut), new Order.Idle()),
                fetched.orders(helped, List.of(new Order.Idle(), new Order.Idle()), NO_STAIR, s -> Optional.of(cut)), "Bob's pickaxe, from the top");
        assertEquals(true, fetched.ordered("Bob"));
        assertEquals(false, fetched.ordered("Dana"));
    }

    /** Stranded below with a pickaxe of its own, it digs the engineer's cut itself: a tunnel up, nobody fetched. */
    @Test
    void oneStrandedBelowWithAPickaxeTunnelsUpItself() {
        Position pocket = new Position(0.5, 60, 0.5);
        Kit pick = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false, true, 0, false);
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Bob", new Position(3, 64, 0), new Order.Idle(), new Report.InPosition(),
                        Optional.of(new Condition(20, 20, 0, Optional.of(pick)))),
                new SoldierStatus("Dana", pocket, new Order.Idle(), STUCK, Optional.of(new Condition(20, 20, 0, Optional.of(pick)))));
        List<Cell> cut = List.of(new Cell(0, 62, 0), new Cell(1, 61, 0), new Cell(1, 62, 0));
        Recovery recovery = new Recovery();

        List<Order> orders = recovery.orders(squad, List.of(new Order.Idle(), new Order.Idle()), NO_STAIR, s -> Optional.of(cut));

        assertEquals(new Order.Idle(), orders.get(0), "nobody fetched");
        assertEquals(Recovery.dig(cut), orders.get(1));
        assertEquals(true, recovery.recovering("Dana"));
        assertEquals(false, recovery.recovering("Bob"));
        // Up a step, still in the drill: a stair is left only at its top.
        List<SoldierStatus> climbing = List.of(squad.get(0),
                new SoldierStatus("Dana", new Position(1.5, 61, 0.5), new Order.Idle(), new Report.Working("dig", 1), squad.get(1).condition()));
        assertEquals(Recovery.dig(cut), recovery.orders(climbing, List.of(new Order.Idle(), new Order.Idle()), NO_STAIR, s -> Optional.of(cut)).get(1));
        // Its stair dug, the world says: released, whatever its height.
        assertEquals(List.of("Dana"), recovery.release("Dana"));
        assertEquals(false, recovery.recovering("Dana"));
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
        Function<SoldierStatus, Optional<List<Cell>>> engineer = s -> {
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
    void noneIsSentForOneStuckForAnotherReason() {
        List<SoldierStatus> squad = List.of(
                soldier("Bob", new Position(3, 64, 0), new Report.InPosition(), 64),
                soldier("Dana", new Position(7, 81, 0), new Report.Cantco("can't make 1 wooden pickaxe"), 0));

        assertEquals(List.of(new Order.Idle(), new Order.Idle()), new Recovery().orders(squad, List.of(new Order.Idle(), new Order.Idle()), NO_STAIR));
    }

    /** No squadmate with blocks: none is sent, and the stranded one goes down to the nearest itself. In contact, nothing. */
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

        assertEquals(List.of(new Order.Idle(), new Order.Idle(),
                        new Order.MoveTo(4, 64, 0, Recovery.ESCAPE_TOLERANCE, Recovery.ESCAPE_TOLERANCE, Risk.MEDIUM)),
                new Recovery().orders(empty, IDLE, NO_STAIR), "nobody sent for Dana: she goes down to Carol, the nearest");
        assertEquals(IDLE, new Recovery().orders(fighting, IDLE, NO_STAIR));
    }
}
