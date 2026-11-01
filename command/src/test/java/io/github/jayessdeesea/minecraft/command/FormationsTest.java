package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formation geometry - docs/military-doctrine.md §2. Heading north means travel toward -z; right is +x (east). */
class FormationsTest {

    private static final Position POINT = new Position(0, 64, 0);
    private static final Position NORTH = new Position(0, 64, -50);

    private static List<SoldierStatus> squadAt(Position... positions) {
        return java.util.Arrays.stream(positions)
                .map(p -> new SoldierStatus("S", p, new Order.Idle(), new Report.Idle()))
                .toList();
    }

    private static Order.MoveTo order(List<Order> orders, int i) {
        return (Order.MoveTo) orders.get(i);
    }

    @Test
    void pointManHeadsForTheObjective() {
        List<Order> orders = Formations.assign(
                new SquadOrder(NORTH, Formation.WEDGE, Interval.NORMAL, 2, "scout"), squadAt(POINT, POINT, POINT));

        assertEquals(new Order.MoveTo(0, 64, -50, Formations.POINT_TOLERANCE, 2), orders.get(0));
    }

    @Test
    void wedgeEchelonsRightThenLeftBehindThePointMan() {
        List<Order> orders = Formations.assign(
                new SquadOrder(NORTH, Formation.WEDGE, Interval.NORMAL, 2, "scout"), squadAt(POINT, POINT, POINT));

        Order.MoveTo first = order(orders, 1);
        Order.MoveTo second = order(orders, 2);
        assertTrue(first.z() > 0 && first.x() > 0, "behind (south) and right (east): " + first);
        assertTrue(second.z() > 0 && second.x() < 0, "behind (south) and left (west): " + second);
        assertEquals(Interval.NORMAL.blocks(), Math.hypot(first.x(), first.z()), 1e-9, "one interval from the point man");
        assertEquals(Interval.NORMAL.blocks() / 2, first.horizontalTolerance(), "slot is a zone, not a point");
    }

    @Test
    void fileLinesUpBehind() {
        List<Order> orders = Formations.assign(
                new SquadOrder(NORTH, Formation.FILE, Interval.TIGHT, 2, "move"), squadAt(POINT, POINT, POINT));

        assertEquals(0, order(orders, 1).x(), 1e-9);
        assertEquals(3, order(orders, 1).z(), 1e-9);
        assertEquals(6, order(orders, 2).z(), 1e-9);
    }

    @Test
    void lineDeploysAbreast() {
        List<Order> orders = Formations.assign(
                new SquadOrder(NORTH, Formation.LINE, Interval.EXTENDED, 2, "assault"), squadAt(POINT, POINT, POINT));

        assertEquals(new Position(10, 64, 0), new Position(order(orders, 1).x(), 64, order(orders, 1).z()));
        assertEquals(new Position(-10, 64, 0), new Position(order(orders, 2).x(), 64, order(orders, 2).z()));
    }

    @Test
    void slotsFollowThePointMansCurrentPosition() {
        Position movedPoint = new Position(5, 70, -20);
        List<Order> orders = Formations.assign(
                new SquadOrder(NORTH, Formation.FILE, Interval.TIGHT, 2, "move"), squadAt(movedPoint, POINT));

        Order.MoveTo slot = order(orders, 1);
        assertTrue(Math.abs(slot.x() - 5) < 1 && slot.z() > -20, "behind the point man where he is now: " + slot);
        assertEquals(70, slot.y(), "same floor as the point man");
    }

    @Test
    void onArrivalTheFormationKeepsTheApproachHeading() {
        // Point man at the objective; the other soldier is south of him, so they came from the south.
        List<Order> orders = Formations.assign(
                new SquadOrder(NORTH, Formation.FILE, Interval.TIGHT, 2, "hold"),
                squadAt(NORTH, new Position(0, 64, -40)));

        assertTrue(order(orders, 1).z() > -50, "slot stays on the approach side: " + orders.get(1));
    }
}
