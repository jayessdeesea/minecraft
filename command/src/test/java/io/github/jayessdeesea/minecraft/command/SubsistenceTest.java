package io.github.jayessdeesea.minecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SubsistenceTest {

    private static SoldierStatus soldier(String name, int food, int rations) {
        Kit kit = new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 0, rations);
        return new SoldierStatus(name, new Position(0, 64, 0), new Order.Idle(), new Report.InPosition(),
                Optional.of(new Condition(20, food, 0, Optional.of(kit))));
    }

    @Test
    void aHungrySoldierWithRationsEatsOneBelowHalfItsLoadRestocksAndOneBelowTenPutsFoodFirst() {
        assertEquals(Subsistence.Need.EAT, Subsistence.need(12, 3));
        assertEquals(Subsistence.Need.NONE, Subsistence.need(20, Subsistence.RESUPPLY), "half its basic load: enough to work on");
        assertEquals(Subsistence.Need.RESTOCK, Subsistence.need(20, Subsistence.RESUPPLY - 1));
        assertEquals(Subsistence.Need.FOOD_FIRST, Subsistence.need(20, Subsistence.FOOD_FIRST - 1));
        assertEquals(Subsistence.Need.FOOD_FIRST, Subsistence.need(10, 0), "hungry with nothing to eat: it has to find some");
        assertEquals(Subsistence.Need.NONE, Subsistence.need(20, Subsistence.BASIC_LOAD));
        assertEquals(0, Subsistence.surplus(Subsistence.BASIC_LOAD));
        assertEquals(6, Subsistence.surplus(Subsistence.BASIC_LOAD + 6));
    }

    @Test
    void theSquadIsGreenWhileEveryoneCarriesFood() {
        Subsistence.Status status = Subsistence.of(List.of(soldier("Alice", 20, 40), soldier("Bob", 15, 10)), Set.of());
        assertEquals(Subsistence.Rating.GREEN, status.rating());
    }

    @Test
    void shortButEachWithAMeansIsAmber() {
        Subsistence.Status status = Subsistence.of(List.of(soldier("Alice", 20, 0), soldier("Bob", 15, 12)), Set.of());
        assertEquals(Subsistence.Rating.AMBER, status.rating());
        assertEquals(List.of("Alice"), status.short_());
    }

    @Test
    void oneShortWithNoMeansMakesItRedAndTheLineIsARequest() {
        Subsistence.Status status = Subsistence.of(List.of(soldier("Alice", 3, 0), soldier("Bob", 0, 9)), Set.of("Alice", "Bob"));
        assertEquals(Subsistence.Rating.RED, status.rating());
        assertEquals(List.of("Alice", "Bob"), status.helpless());
        assertTrue(status.line().startsWith("Class I RED - request: Alice, Bob out of food"), status.line());
    }
}
