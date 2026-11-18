package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
}
