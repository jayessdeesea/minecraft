package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmDutiesTest {

    /** Four on the farm: three harvest a share each, the fourth bakes. */
    @Test
    void threeHarvestAndTheFourthBakes() {
        Map<String, FarmDuties.Duty> duties = FarmDuties.of(List.of("Alice", "Bob", "Carl", "Dana"));
        assertEquals(FarmDuties.Kind.BAKER, duties.get("Dana").kind());
        assertEquals(new FarmDuties.Duty("Bob", FarmDuties.Kind.HARVESTER, 1, 3), duties.get("Bob"));
    }

    /** Two on the farm both harvest: a baker would leave half the plot to one. */
    @Test
    void twoBothHarvest() {
        Map<String, FarmDuties.Duty> duties = FarmDuties.of(List.of("Alice", "Bob"));
        assertEquals(FarmDuties.Kind.HARVESTER, duties.get("Bob").kind());
        assertEquals(2, duties.get("Bob").shares());
    }

    /** The shares cover the plot, each cell in one of them. */
    @Test
    void theSharesCoverThePlotOnce() {
        List<Integer> plot = IntStream.range(0, 80).boxed().toList();
        Map<String, FarmDuties.Duty> duties = FarmDuties.of(List.of("Alice", "Bob", "Carl", "Dana"));
        List<Integer> covered = new ArrayList<>();
        for (String h : List.of("Alice", "Bob", "Carl")) {
            covered.addAll(FarmDuties.share(plot, duties.get(h)));
        }
        assertEquals(plot, covered);
    }

    /** Each harvester's ground is a run of whole columns of the plot - no cell in two - and the baker's is the dump. */
    @Test
    void harvestersAreOrderedOntoColumnsAndTheBakerToTheDump() {
        List<Position> plot = new ArrayList<>();
        for (int x = 6; x <= 14; x++) {
            for (int z = -4; z <= 4; z++) {
                plot.add(new Position(x, 63, z));
            }
        }
        Optional<Position> dump = Optional.of(new Position(3, 64, 2));
        Map<String, WorkOrder> orders = FarmDuties.orders(List.of("Alice", "Bob", "Carl", "Dana"), plot, dump);

        assertEquals("harvester over x 6..8, z -4..4, the dump at (3, 64, 2)", orders.get("Alice").line());
        assertEquals("harvester over x 9..11, z -4..4, the dump at (3, 64, 2)", orders.get("Bob").line());
        assertEquals("harvester over x 12..14, z -4..4, the dump at (3, 64, 2)", orders.get("Carl").line());
        assertEquals("baker, the dump at (3, 64, 2)", orders.get("Dana").line());
        for (Position cell : plot) {
            assertEquals(1, orders.values().stream().filter(o -> o.ground().filter(g -> g.contains((int) cell.x(), (int) cell.y(), (int) cell.z()))
                    .isPresent()).count(), "cell " + cell + " in one harvester's ground");
        }
    }
}
