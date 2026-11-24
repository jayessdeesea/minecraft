package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodDutiesTest {

    private static final Objective WOOD = new Objective("Cut wood", new Position(100.5, 70, -40.5), "logs for the depot",
            new Objective.Task.Gather(Resource.WOOD));

    /** A squad of four: three woodcutters on strips of their own, and the fourth plants behind them. */
    @Test
    void threeCutOnStripsOfTheirOwnAndTheFourthPlants() {
        Map<String, WoodDuties.Duty> duties = WoodDuties.of(Optional.of(WOOD), List.of("Alice", "Bob", "Carl", "Dana"));

        assertEquals(WoodDuties.Kind.PLANTER, duties.get("Dana").kind());
        List<Box> strips = List.of(duties.get("Alice").area(), duties.get("Bob").area(), duties.get("Carl").area());
        strips.forEach(s -> assertTrue(duties.get("Dana").area().contains(s.minX(), s.minY(), s.minZ())
                && duties.get("Dana").area().contains(s.maxX(), s.maxY(), s.maxZ()), "the planter's ground holds each strip"));
        for (int a = 0; a < strips.size(); a++) {
            for (int b = a + 1; b < strips.size(); b++) {
                Box one = strips.get(a);
                Box two = strips.get(b);
                boolean apart = one.maxX() < two.minX() || two.maxX() < one.minX() || one.maxZ() < two.minZ() || two.maxZ() < one.minZ();
                assertTrue(apart, "no two woodcutters share ground: " + one + " " + two);
            }
        }
        assertEquals(Optional.of(new Order.Gather(Resource.WOOD, duties.get("Alice").area(), WoodDuties.LOAD)), duties.get("Alice").order());
        assertEquals(Optional.empty(), duties.get("Dana").order(), "the planter isn't sent for logs");
    }

    /** Alone, a soldier cuts the whole forest - nobody's left to plant. */
    @Test
    void oneSoldierCutsAlone() {
        Map<String, WoodDuties.Duty> duties = WoodDuties.of(Optional.of(WOOD), List.of("Alice"));
        assertEquals(WoodDuties.Kind.WOODCUTTER, duties.get("Alice").kind());
        assertEquals(1, duties.size());
    }

    /** Only an objective for wood makes wood duties. */
    @Test
    void noDutiesForOtherWork() {
        Objective farm = new Objective("Farm", new Position(0, 64, 0), "bread", new Objective.Task.Farm());
        assertEquals(Map.of(), WoodDuties.of(Optional.of(farm), List.of("Alice", "Bob")));
        assertEquals(Map.of(), WoodDuties.of(Optional.empty(), List.of("Alice", "Bob")));
    }

    /** A woodcutter's order is its strip, the planter's the whole wood, both to the dump. */
    @Test
    void woodDutiesAreOrdersToTheirGroundAndTheDump() {
        Map<String, WoodDuties.Duty> duties = WoodDuties.assign(List.of("Alice", "Bob", "Carl"), new Position(0, 64, 0));
        Map<String, DutyOrder> orders = WoodDuties.orders(duties, Optional.of(new Position(3, 64, 2)));

        assertEquals(DutyOrder.Duty.WOODCUTTER, orders.get("Alice").duty());
        assertEquals(Optional.of(duties.get("Alice").area()), orders.get("Alice").ground());
        assertEquals(DutyOrder.Duty.PLANTER, orders.get("Carl").duty());
        assertEquals(Optional.of(new Position(3, 64, 2)), orders.get("Carl").dump());
    }

    /** A strip found cut out is taken off the forest: at its end, and in its middle, leaving it in two. */
    /** A strip is cut in two between its trees, half each side, across its longer side; with trees on one line, not at all. */
    @Test
    void aStripIsCutInTwoBetweenItsTrees() {
        Box strip = new Box(-8, 56, -24, 7, 88, 24);
        List<Box> halves = WoodDuties.split(strip, List.of(new int[]{0, -20}, new int[]{3, -10}, new int[]{-2, 6}, new int[]{1, 18})).orElseThrow();
        assertEquals(List.of(new Box(-8, 56, -24, 7, 88, -2), new Box(-8, 56, -1, 7, 88, 24)), halves, "between z=-10 and z=6, two trees each side");
        assertEquals(Optional.empty(), WoodDuties.split(strip, List.of(new int[]{0, 5}, new int[]{4, 5})), "its trees on one line across it");
        assertEquals(Optional.empty(), WoodDuties.split(strip, List.of()));
    }

    /** A woodcutter with no strip stands by, with no order; the planter keeps the whole wood. */
    @Test
    void dutiesAreTheStripsAsTheyStandAndThePlanter() {
        Position forest = new Position(0, 64, 0);
        List<String> roster = List.of("Alice", "Bob", "Carl", "Dana");
        Map<String, Box> strips = new java.util.LinkedHashMap<>(WoodDuties.strips(roster, forest));
        assertEquals(List.of("Alice", "Bob", "Carl"), List.copyOf(strips.keySet()), "its woodcutters, the last its planter");
        strips.remove("Bob");
        Map<String, WoodDuties.Duty> duties = WoodDuties.duties(roster, forest, strips);
        assertEquals(java.util.Set.of("Alice", "Bob", "Carl", "Dana"), duties.keySet());
        assertEquals(WoodDuties.Kind.STANDBY, duties.get("Bob").kind());
        assertEquals(Optional.empty(), duties.get("Bob").order(), "no order to fell");
        assertEquals(java.util.Set.of("Alice", "Carl", "Dana"), WoodDuties.orders(duties, Optional.empty()).keySet(), "and no producer's order");
        assertEquals(WoodDuties.whole(forest), duties.get("Dana").area());
    }
}
