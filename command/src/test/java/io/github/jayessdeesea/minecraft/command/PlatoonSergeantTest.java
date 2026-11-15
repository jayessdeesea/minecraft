package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatoonSergeantTest {

    private static List<PlatoonSergeant.Hand> crew(int each, String... names) {
        return java.util.Arrays.stream(names).map(n -> new PlatoonSergeant.Hand(n, each, 0)).toList();
    }

    private static final List<PlatoonSergeant.Hand> EIGHT = crew(0, "Alice", "Bob", "Carol", "Dana", "Eli", "Fran", "Gus", "Hal");
    private static final PlatoonSergeant.Wood SHORT = new PlatoonSergeant.Wood(0, true, false, false);

    @Test
    void shortOfLogsTwoCutAndNeverTheForeman() {
        PlatoonSergeant.WoodParty party = PlatoonSergeant.woodParty(EIGHT, "Hal", PlatoonSergeant.WoodParty.NONE, SHORT);

        assertEquals(Set.of("Gus", "Fran"), party.cutters(), "from the end of the roster, not the foreman");
        assertTrue(party.cutting());
    }

    @Test
    void aThirdOfTheCrewAtMost() {
        assertEquals(1, PlatoonSergeant.woodParty(crew(0, "Alice", "Bob", "Carol", "Dana", "Eli"), "Alice",
                PlatoonSergeant.WoodParty.NONE, SHORT).cutters().size());
        assertEquals(0, PlatoonSergeant.woodParty(crew(0, "Alice", "Bob"), "Alice",
                PlatoonSergeant.WoodParty.NONE, SHORT).cutters().size(), "two builders: both dig");
    }

    @Test
    void noTreesSeenNoParty() {
        PlatoonSergeant.WoodParty party = PlatoonSergeant.woodParty(EIGHT, "Alice", PlatoonSergeant.WoodParty.NONE,
                new PlatoonSergeant.Wood(0, false, true, false));

        assertTrue(party.cutters().isEmpty());
        assertTrue(party.planter().isEmpty(), "no trees and no sapling: nothing to plant either");
    }

    /** Out below half the logs wanted, back at all of them: not in and out with each chest made. */
    @Test
    void outBelowHalfBackAtAll() {
        List<PlatoonSergeant.Hand> twelve = crew(0, "Alice", "Bob", "Carol", "Dana", "Eli", "Fran");
        PlatoonSergeant.WoodParty stocked = PlatoonSergeant.woodParty(twelve, "Alice", PlatoonSergeant.WoodParty.NONE,
                new PlatoonSergeant.Wood(Keep.LOGS_WANTED - 2, true, false, false));
        assertFalse(stocked.cutting(), "below what's wanted, but not below half: no party");

        PlatoonSergeant.WoodParty out = new PlatoonSergeant.WoodParty(Set.of("Fran"), Optional.empty(), true);
        assertTrue(PlatoonSergeant.woodParty(twelve, "Alice", out, new PlatoonSergeant.Wood(Keep.LOGS_WANTED - 2, true, false, false))
                .cutting(), "out already: it stays out until all are in");
        assertFalse(PlatoonSergeant.woodParty(twelve, "Alice", out, new PlatoonSergeant.Wood(Keep.LOGS_WANTED, true, false, false))
                .cutting(), "and comes back at all of them");
    }

    /** A cutter part-loaded stays out until its load is in. */
    @Test
    void aCutterKeepsItsPlaceWhileItGathers() {
        List<PlatoonSergeant.Hand> crew = List.of(new PlatoonSergeant.Hand("Alice", 0, 0), new PlatoonSergeant.Hand("Bob", 0, 0),
                new PlatoonSergeant.Hand("Carol", 10, 0));
        PlatoonSergeant.WoodParty was = new PlatoonSergeant.WoodParty(Set.of("Carol"), Optional.empty(), true);

        assertEquals(Set.of("Carol"), PlatoonSergeant.woodParty(crew, "Alice", was, SHORT).cutters(),
                "Carol's 10 logs short of a load: she stays out");
    }

    @Test
    void aSpareWhileStockedPlantsTheGroveOneCarryingSaplingsFirst() {
        List<PlatoonSergeant.Hand> crew = List.of(new PlatoonSergeant.Hand("Alice", 0, 0), new PlatoonSergeant.Hand("Bob", 0, 0),
                new PlatoonSergeant.Hand("Carol", 0, 0), new PlatoonSergeant.Hand("Dana", 0, 3));

        PlatoonSergeant.WoodParty party = PlatoonSergeant.woodParty(crew, "Alice", PlatoonSergeant.WoodParty.NONE,
                new PlatoonSergeant.Wood(Keep.LOGS_WANTED, true, true, false));

        assertTrue(party.cutters().isEmpty());
        assertEquals(Optional.of("Dana"), party.planter());
        assertTrue(PlatoonSergeant.woodParty(crew, "Alice", party, new PlatoonSergeant.Wood(Keep.LOGS_WANTED, true, true, true))
                .planter().isEmpty(), "its planting waits: no planter");
    }

    @Test
    void shortOfLogsCuttingComesBeforePlanting() {
        List<PlatoonSergeant.Hand> crew = crew(0, "Alice", "Bob", "Carol");

        PlatoonSergeant.WoodParty party = PlatoonSergeant.woodParty(crew, "Alice", PlatoonSergeant.WoodParty.NONE,
                new PlatoonSergeant.Wood(0, true, true, false));

        assertEquals(Set.of("Carol"), party.cutters());
        assertTrue(party.planter().isEmpty(), "the one place goes to cutting");
    }
}
