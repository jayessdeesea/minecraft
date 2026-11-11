package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrdersTest {

    private static final Directive FETCH = new Directive.Fetch(Resource.WOOD, 4, new Position(10, 64, 0));

    @Test
    void oneWhoseWorkLeftDoesntFallIsTimedOut() {
        WorkOrders orders = new WorkOrders();
        assertEquals(List.of(), orders.update(0, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of()));
        assertEquals(List.of(), orders.update(WorkOrders.STALL_MILLIS - 1, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of()));
        List<WorkOrders.TimedOut> out = orders.update(WorkOrders.STALL_MILLIS, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of());
        assertEquals(1, out.size());
        assertTrue(out.getFirst().why().startsWith("no progress in 120 s"), out.toString());
        assertTrue(orders.standing("Bob", WorkOrders.STALL_MILLIS + 1).orElseThrow().contains("timed out"), "its squad leader hears of it");
        assertTrue(orders.barred("Bob", FETCH, WorkOrders.STALL_MILLIS + 1), "given again at once, it's refused");
        assertTrue(!orders.barred("Bob", new Directive.Fetch(Resource.STONE, 4, new Position(10, 64, 0)), WorkOrders.STALL_MILLIS + 1),
                "another order isn't");
        assertTrue(!orders.barred("Bob", FETCH, WorkOrders.STALL_MILLIS + WorkOrders.SHOWN_MILLIS + 1), "nor this one, later");
    }

    @Test
    void progressKeepsItGoingUntilItsAllowanceRunsOut() {
        WorkOrders orders = new WorkOrders();
        long allowance = WorkOrders.allowance(FETCH, 4);
        orders.update(0, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of());
        int left = 4;
        long now = 0;
        while (now < allowance) {
            now += WorkOrders.STALL_MILLIS - 1;
            left = Math.max(1, left - 1);
            if (now > allowance) {
                break;
            }
            assertEquals(List.of(), orders.update(now, Map.of("Bob", FETCH), Map.of("Bob", left), Set.of()), "at " + now);
        }
        List<WorkOrders.TimedOut> out = orders.update(allowance + 1, Map.of("Bob", FETCH), Map.of("Bob", 1), Set.of());
        assertEquals(1, out.size(), "past its allowance with work left");
        assertTrue(out.getFirst().why().startsWith("not done in its"), out.toString());
    }

    @Test
    void contactPausesTheClock() {
        WorkOrders orders = new WorkOrders();
        orders.update(0, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of());
        orders.update(100_000, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of("Bob"));
        assertEquals(List.of(), orders.update(200_000, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of()),
                "100 s of it fighting, 100 s working");
    }

    @Test
    void aRiflemanHoldingThePointHeWasSentToIsNeverTimedOut() {
        WorkOrders orders = new WorkOrders();
        Directive post = new Directive.GoTo(new Position(20, 64, 0));
        orders.update(0, Map.of("Bob", post), Map.of("Bob", 0), Set.of());
        assertEquals(List.of(), orders.update(60 * 60_000, Map.of("Bob", post), Map.of("Bob", 0), Set.of()));
        assertEquals("there", orders.standing("Bob", 60 * 60_000).orElseThrow());
    }

    @Test
    void aNewDirectiveStartsItsOwnClock() {
        WorkOrders orders = new WorkOrders();
        orders.update(0, Map.of("Bob", FETCH), Map.of("Bob", 4), Set.of());
        Directive other = new Directive.Fetch(Resource.STONE, 8, new Position(0, 64, 0));
        assertEquals(List.of(), orders.update(WorkOrders.STALL_MILLIS, Map.of("Bob", other), Map.of("Bob", 8), Set.of()));
    }
}
