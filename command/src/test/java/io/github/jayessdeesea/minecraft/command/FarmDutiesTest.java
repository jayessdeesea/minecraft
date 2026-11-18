package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
}
