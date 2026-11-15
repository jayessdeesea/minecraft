package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatoonSergeantTest {

    private static List<PlatoonSergeant.Hand> crew(int each, String... names) {
        return java.util.Arrays.stream(names).map(n -> new PlatoonSergeant.Hand(n, each, 0)).toList();
    }

    private static final List<PlatoonSergeant.Hand> EIGHT = crew(0, "Alice", "Bob", "Carol", "Dana", "Eli", "Fran", "Gus", "Hal");
    private static final PlatoonSergeant.Wood SHORT = new PlatoonSergeant.Wood(0, true, false, false);

    /**
     * Short of logs between the builders and the stockpile, the keep's Class IV is a request to the
     * general, naming what can't be made; with enough, nothing is said. Live, the builders worked a
     * day with no tree within reach, no chest and no pickaxe, and nobody was asked for logs.
     */
    @Test
    void theKeepsBuildersAskForLogsWhenTheyAndTheStockpileAreShort() {
        String line = PlatoonSergeant.classFour(1, 0, false).orElseThrow();
        assertTrue(line.startsWith("Class IV RED - request:"), line);
        assertTrue(line.contains("1 log between them") && line.contains("no chest at the keep's depot") && line.contains("WOOD"), line);
        assertTrue(!PlatoonSergeant.classFour(3, 0, true).orElseThrow().contains("chest"), "the chest stands: the pickaxes and torches are the want");
        assertTrue(PlatoonSergeant.classFour(6, 6, false).isPresent(), "a dozen logs isn't a depot's: chests, a table, handles");
        assertEquals(Optional.empty(), PlatoonSergeant.classFour(8, 8, false), "enough between them and the stockpile");
        assertEquals(Optional.empty(), PlatoonSergeant.classFour(Keep.LOGS_WANTED, 0, false));
    }

    private static SoldierStatus hungry(String name) {
        Kit kit = new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 0, 0);
        return new SoldierStatus(name, new Position(0, 64, 0), new Order.Idle(), new Report.InPosition(),
                Optional.of(new Condition(20, 3, 0, Optional.of(kit))));
    }

    /**
     * Class I: a hungry soldier with no means of food is RED, unless a squadmate is sharing some or
     * wheat grows in the camp's farm - its bread is on the way, and the general can't answer better.
     */
    @Test
    void noMeansOfFoodIsRedUnlessShareOrWheatIsComing() {
        List<SoldierStatus> squad = List.of(hungry("Alice"), hungry("Bob"));

        assertEquals(Subsistence.Rating.RED, PlatoonSergeant.classOne(squad, Set.of("Alice", "Bob"), Set.of(), false).rating());
        assertEquals(Subsistence.Rating.RED, PlatoonSergeant.classOne(squad, Set.of("Alice", "Bob"), Set.of("Alice"), false).rating(),
                "Bob still has no means");
        assertNotEquals(Subsistence.Rating.RED, PlatoonSergeant.classOne(squad, Set.of("Alice", "Bob"), Set.of("Alice", "Bob"), false).rating(),
                "both being shared some");
        assertNotEquals(Subsistence.Rating.RED, PlatoonSergeant.classOne(squad, Set.of("Alice", "Bob"), Set.of(), true).rating(),
                "wheat growing in the farm");
    }

    private static SoldierStatus carrying(String name, int rations, Optional<String> weapon, Report report) {
        Kit kit = new Kit(weapon, 0, 0, 0, 0, 0, rations);
        return new SoldierStatus(name, new Position(0, 64, 0), new Order.Idle(), report, Optional.of(new Condition(20, 20, 0, Optional.of(kit))));
    }

    /** Rations to one running low from one with plenty; none to or from one the recovery drill has. */
    @Test
    void rationsGoToOneRunningLowButNeverToOrFromOneRecovering() {
        List<SoldierStatus> squad = List.of(carrying("Alice", 64, Optional.of("stone_sword"), new Report.InPosition()),
                carrying("Bob", 1, Optional.of("stone_sword"), new Report.InPosition()));

        assertTrue(PlatoonSergeant.crossLevel(squad, java.util.Map.of(), n -> false).stream()
                .anyMatch(t -> t.item() == CrossLevel.Item.RATIONS && t.giver().equals("Alice") && t.receiver().equals("Bob")));
        assertTrue(PlatoonSergeant.crossLevel(squad, java.util.Map.of(), "Bob"::equals).stream()
                .noneMatch(t -> t.item() == CrossLevel.Item.RATIONS), "Bob recovering: none walked to him");
        assertTrue(PlatoonSergeant.crossLevel(squad, java.util.Map.of(), "Alice"::equals).stream()
                .noneMatch(t -> t.item() == CrossLevel.Item.RATIONS), "Alice recovering: none from her");
    }

    /** A spare weapon to one bare-handed - not to one making its kit, whose weapon may be on a cursor. */
    @Test
    void aSpareWeaponGoesToOneBareHandedButNotToOneRearming() {
        SoldierStatus alice = new SoldierStatus("Alice", new Position(0, 64, 0), new Order.Idle(), new Report.InPosition(),
                Optional.of(new Condition(20, 20, 0, Optional.of(new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 0, 64, false, false,
                        0, false, 1, 0, 0)))));
        List<SoldierStatus> bare = List.of(alice, carrying("Bob", 64, Optional.empty(), new Report.InPosition()));
        List<SoldierStatus> rearming = List.of(alice, carrying("Bob", 64, Optional.empty(), new Report.Rearming()));

        assertTrue(PlatoonSergeant.crossLevel(bare, java.util.Map.of(), n -> false).stream()
                .anyMatch(t -> t.item() == CrossLevel.Item.WEAPON && t.receiver().equals("Bob")), "Bob bare-handed: Alice's spare");
        assertTrue(PlatoonSergeant.crossLevel(rearming, java.util.Map.of(), n -> false).stream()
                .noneMatch(t -> t.item() == CrossLevel.Item.WEAPON), "Bob making his kit: none");
    }

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
