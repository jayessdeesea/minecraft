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

    /** A soil top: one carrying dirt fills its strip with dirt alone; one without is sent for a load. */
    @Test
    void aSoilTopIsFilledWithDirtAndOneShortFetchesIt() {
        Box top = new Box(0, 62, 0, 9, 62, 9), borrow = new Box(20, 63, 0, 29, 80, 9);
        java.util.Map<String, Order> orders = Tasking.soil(java.util.List.of(new Tasking.Hand("Alice", 64), new Tasking.Hand("Bob", 0)),
                java.util.Optional.of(top), borrow, java.util.Set.of(), java.util.Set.of());
        assertEquals(Order.Fill.Material.SOIL, ((Order.Fill) orders.get("Alice")).material());
        assertEquals(borrow, ((Order.Gather) orders.get("Bob")).box());
    }

    /** A dig narrower than the crew: a hand to each strip, the nearest - the rest no share, free for other work. */
    @Test
    void aDigNarrowerThanTheCrewHasAHandToAStripAndNoMore() {
        List<Tasking.Hand> hands = List.of(new Tasking.Hand("Dana", 0), new Tasking.Hand("Fran", 0), new Tasking.Hand("Hal", 0));
        Box oneBlock = new Box(-50, 85, 46, -50, 85, 46);

        assertEquals(Map.of("Dana", new Order.Dig(oneBlock, Set.of())),
                Tasking.allocate(hands, FEET, 7, Optional.of(oneBlock), Optional.empty(), Set.of(), Set.of()), "one block, one hand");
        assertEquals(Map.of("Hal", new Order.Dig(oneBlock, Set.of())), Tasking.allocate(hands, FEET, 7, Optional.of(oneBlock), Optional.empty(),
                Set.of(), Set.of(), (hand, box) -> hand.equals("Hal") ? 1 : 10), "the hand nearest it");
    }
    /** Water in a dig is one hand's to fill - the hand whose strip holds it - and the others dig on. */
    @Test
    void waterInADigIsFilledByTheHandWhoseStripHoldsIt() {
        java.util.Map<String, Order> digs = java.util.Map.of(
                "Alice", new Order.Dig(new Box(0, 64, 0, 3, 70, 7), java.util.Set.of()),
                "Bob", new Order.Dig(new Box(4, 64, 0, 7, 70, 7), java.util.Set.of()));
        java.util.List<Tasking.Hand> hands = java.util.List.of(new Tasking.Hand("Alice", 64), new Tasking.Hand("Bob", 64));
        Box water = new Box(5, 66, 2, 5, 66, 2), borrow = new Box(20, 60, 0, 24, 64, 4);

        assertEquals(java.util.Map.of("Bob", new Order.Fill(water, java.util.Set.of(), Order.Fill.Material.SOIL)),
                Tasking.water(digs, hands, java.util.Optional.of(water), borrow, java.util.Set.of(), java.util.Set.of()));
        assertEquals(java.util.Map.of("Bob", new Order.Gather(Resource.SOIL, borrow, Tasking.LOAD)),
                Tasking.water(digs, java.util.List.of(new Tasking.Hand("Alice", 64), new Tasking.Hand("Bob", 0)), java.util.Optional.of(water), borrow,
                        java.util.Set.of(), java.util.Set.of()), "short of dirt, it's sent for a load");
        assertEquals(java.util.Map.of("Alice", new Order.Fill(water, java.util.Set.of(), Order.Fill.Material.SOIL)),
                Tasking.water(java.util.Map.of(), hands, java.util.Optional.of(water), borrow, java.util.Set.of(), java.util.Set.of()), "no strip holds it: the first hand");
        assertEquals(java.util.Map.of(), Tasking.water(digs, hands, java.util.Optional.empty(), borrow, java.util.Set.of(), java.util.Set.of()));
    }
}
