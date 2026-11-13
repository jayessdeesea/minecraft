package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskingTest {

    private static final Cell FEET = new Cell(0, 64, 0);
    private static final Box DIG = Groundwork.dig(FEET, 7);
    private static final Box FILL = Groundwork.fill(FEET, 7);

    @Test
    void whileThereIsDiggingEachSoldierDigsItsOwnStrip() {
        Map<String, Order> orders = Tasking.allocate(List.of(new Tasking.Hand("Alice", 0), new Tasking.Hand("Bob", 0)),
                FEET, 7, Optional.of(DIG), Optional.of(FILL), Set.of(), Set.of());

        assertEquals(new Order.Dig(new Box(-7, 64, -7, -1, 88, 7), Set.of()), orders.get("Alice"));
        assertEquals(new Order.Dig(new Box(0, 64, -7, 7, 88, 7), Set.of()), orders.get("Bob"));
    }

    @Test
    void thenFillingAndOneShortOfBlocksIsSentOffBaseForDirt() {
        Map<String, Order> orders = Tasking.allocate(List.of(new Tasking.Hand("Alice", 30), new Tasking.Hand("Bob", 2)),
                FEET, 7, Optional.empty(), Optional.of(FILL), Set.of(), Set.of());

        assertTrue(orders.get("Alice") instanceof Order.Fill, orders.toString());
        Order.Gather run = (Order.Gather) orders.get("Bob");
        assertEquals(Resource.SOIL, run.resource());
        assertEquals(Tasking.LOAD, run.amount());
        assertTrue(run.box().minX() > 7 || run.box().maxX() < -7 || run.box().minZ() > 7 || run.box().maxZ() < -7,
                "off base, clear of the site: " + run.box());
    }

    @Test
    void oneSentForDirtComesBackWithAFullLoad() {
        List<Tasking.Hand> hands = List.of(new Tasking.Hand("Bob", 20));

        assertTrue(Tasking.allocate(hands, FEET, 7, Optional.empty(), Optional.of(FILL), Set.of(), Set.of("Bob")).get("Bob")
                instanceof Order.Gather, "still gathering at 20");
        assertTrue(Tasking.allocate(hands, FEET, 7, Optional.empty(), Optional.of(FILL), Set.of(), Set.of()).get("Bob")
                instanceof Order.Fill, "20 is enough to start filling with");
    }

    @Test
    void nothingLeftIsNoOrders() {
        assertEquals(Map.of(), Tasking.allocate(List.of(new Tasking.Hand("Alice", 0)), FEET, 7, Optional.empty(), Optional.empty(),
                Set.of(), Set.of()));
    }

    @Test
    void eachSoldiersBorrowPitIsOnItsOwnSide() {
        assertEquals(4, IntStream.range(0, 4).mapToObj(i -> Groundwork.borrowPit(FEET, 8, i)).distinct().count());
    }

    /**
     * Level by level, strip by strip: a spire in one strip holds back the strip beside it to two
     * under it, and leaves a strip further off to dig its own ground. Live, one spire at y=103
     * set the whole keep's layer, and a squad's every strip stood idle under it.
     */
    @Test
    void aSpireHoldsBackOnlyTheStripsBesideIt() {
        Box share = new Box(0, 64, 0, 15, 319, 11); // three strips across x: 0..4, 5..9, 10..15
        java.util.function.IntBinaryOperator top = (x, z) -> x < 0 || x > 15 || z < 0 || z > 11 ? Integer.MIN_VALUE
                : z == 0 && x == 4 ? 103 : 70; // the spire on A's edge, beside B
        Map<String, Order> orders = Tasking.keepDig(List.of(new Tasking.Hand("A", 0), new Tasking.Hand("B", 0), new Tasking.Hand("C", 0)),
                share, top, Set.of());

        assertEquals(101, ((Order.Dig) orders.get("A")).box().minY(), "the spire's own strip, top down");
        assertTrue(!orders.containsKey("B"), "beside it: nothing at or over 101 till the spire comes down - free for other work");
        assertEquals(68, ((Order.Dig) orders.get("C")).box().minY(), "further off: its own ground, two under its own top");
    }
}
