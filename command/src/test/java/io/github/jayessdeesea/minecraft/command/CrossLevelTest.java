package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossLevelTest {

    private static final Order SLOT = new Order.MoveTo(0, 64, 0, 3, 3, Risk.LOW);

    private static SoldierStatus soldier(String name, double x, int logs, Report report) {
        Kit kit = new Kit(Optional.of("wooden_sword"), 0, 0, 0, logs, 0);
        return new SoldierStatus(name, new Position(x, 64, 0), SLOT, report, Optional.of(new Condition(20, 20, 0, Optional.of(kit))));
    }

    @Test
    void oneStarvingAtDeathsDoorGetsAMealFromASquadmateWithOnlyItsKeep() {
        Kit none = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 0, false);
        Kit one = new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 1, false);
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Hal", new Position(0, 19, 0), SLOT, new Report.InPosition(), Optional.of(new Condition(0.33, 0, 0, Optional.of(none)))),
                new SoldierStatus("Fran", new Position(1, 19, 0), SLOT, new Report.InPosition(), Optional.of(new Condition(20, 20, 0, Optional.of(one)))));

        assertEquals(List.of(new CrossLevel.Transfer("Fran", "Hal", CrossLevel.Item.RATIONS, 1)),
                CrossLevel.pair(squad, Map.of(), Map.of("Hal", CrossLevel.RATIONS_GIVEN)));
        List<SoldierStatus> hungryOnly = List.of(
                new SoldierStatus("Hal", new Position(0, 19, 0), SLOT, new Report.InPosition(), Optional.of(new Condition(12, 0, 0, Optional.of(none)))),
                squad.get(1));
        assertEquals(List.of(), CrossLevel.pair(hungryOnly, Map.of(), Map.of("Hal", CrossLevel.RATIONS_GIVEN)),
                "hungry but not at death's door: only from a squadmate with rations beyond its keep");
    }

    @Test
    void aBareHandedSoldierGetsASquadmatesSpareWeaponFirst() {
        Condition bare = new Condition(20, 20, 1, Optional.of(new Kit(Optional.empty(), 0, 0)));
        Condition spare = new Condition(20, 20, 0, Optional.of(new Kit(Optional.of("stone_sword"), 0, 0, 0, 8, 0, 0, false, false, 0, false, 1)));
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Alice", new Position(0, 64, 0), SLOT, new Report.Idle(), Optional.of(bare)),
                soldier("Bob", 2, 8, new Report.Idle()),
                new SoldierStatus("Carol", new Position(9, 64, 0), SLOT, new Report.Idle(), Optional.of(spare)));

        assertEquals(List.of(new CrossLevel.Transfer("Carol", "Alice", CrossLevel.Item.WEAPON, 1)),
                CrossLevel.pair(squad, Map.of("Alice", 3), Map.of(), Map.of("Alice", 1)),
                "Bob, nearer, has no spare; the weapon before the logs");
    }

    @Test
    void aReplacementStillLinkingUpGetsNothingYet() {
        Condition bare = new Condition(20, 20, 1, Optional.of(new Kit(Optional.empty(), 0, 0)));
        Condition spare = new Condition(20, 20, 0, Optional.of(new Kit(Optional.of("stone_sword"), 0, 0, 0, 8, 0, 0, false, false, 0, false, 1)));
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Alice", new Position(60, 100, 0), SLOT, new Report.LinkingUp(), Optional.of(bare)),
                new SoldierStatus("Carol", new Position(9, 64, 0), SLOT, new Report.Idle(), Optional.of(spare)));

        assertEquals(List.of(), CrossLevel.pair(squad, Map.of(), Map.of(), Map.of("Alice", 1)));
    }

    @Test
    void theNearestSquadmateWithLogsToSpareGivesThem() {
        List<SoldierStatus> squad = List.of(
                soldier("Alice", 0, 0, new Report.Cantco("can't make a wooden pickaxe: no tree within 16 blocks")),
                soldier("Bob", 20, 16, new Report.Gathering(Resource.STONE)),
                soldier("Carol", 5, 16, new Report.Gathering(Resource.STONE)));

        assertEquals(List.of(new CrossLevel.Transfer("Carol", "Alice", CrossLevel.Item.LOGS, 3)), CrossLevel.pair(squad, Map.of("Alice", 3)));
    }

    @Test
    void aGiverKeepsEnoughForItsOwnPickaxe() {
        List<SoldierStatus> squad = List.of(
                soldier("Alice", 0, 0, new Report.Idle()),
                soldier("Bob", 2, 5, new Report.Idle()),
                soldier("Carol", 9, 6, new Report.Idle()));

        // Bob has 2 to spare, Carol 3.
        assertEquals(List.of(new CrossLevel.Transfer("Carol", "Alice", CrossLevel.Item.LOGS, 3)), CrossLevel.pair(squad, Map.of("Alice", 3)));
    }

    @Test
    void aGiverServesOneSoldierAtATime() {
        List<SoldierStatus> squad = List.of(
                soldier("Alice", 0, 0, new Report.Idle()),
                soldier("Bob", 1, 0, new Report.Idle()),
                soldier("Carol", 2, 16, new Report.Idle()));

        assertEquals(List.of(new CrossLevel.Transfer("Carol", "Alice", CrossLevel.Item.LOGS, 3)),
                CrossLevel.pair(squad, Map.of("Alice", 3, "Bob", 3)));
    }

    @Test
    void aSoldierWithNoFoodGetsRationsFromOneWithFoodToSpare() {
        SoldierStatus hungry = soldier("Alice", 0, 0, new Report.Idle());
        SoldierStatus hunter = new SoldierStatus("Bob", new Position(4, 64, 0), SLOT, new Report.Idle(), Optional.of(new Condition(20, 20, 0,
                Optional.of(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 20)))));
        SoldierStatus lean = new SoldierStatus("Carol", new Position(1, 64, 0), SLOT, new Report.Idle(), Optional.of(new Condition(20, 20, 0,
                Optional.of(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 15)))));

        assertEquals(List.of(new CrossLevel.Transfer("Bob", "Alice", CrossLevel.Item.RATIONS, CrossLevel.RATIONS_GIVEN)),
                CrossLevel.pair(List.of(hungry, hunter, lean), Map.of(), Map.of("Alice", CrossLevel.RATIONS_GIVEN)),
                "Carol, nearer, carries only the fifteen she keeps");
    }

    private static SoldierStatus carrying(String name, double x, int rations) {
        return new SoldierStatus(name, new Position(x, 64, 0), SLOT, new Report.Idle(), Optional.of(new Condition(20, 20, 0,
                Optional.of(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, rations)))));
    }

    @Test
    void aSoldierNoSquadmateFeedsGetsARationFromTheNearestWithSomeToSpareOfAnotherSquad() {
        List<SoldierStatus> short_ = List.of(carrying("Bob", 0, 3));
        List<SoldierStatus> others = List.of(carrying("Eli", 2, 15), carrying("Fran", 9, 40), carrying("Hal", 4, 16),
                carrying("Gus", 40, 64));

        assertEquals(List.of(new CrossLevel.Transfer("Hal", "Bob", CrossLevel.Item.RATIONS, 1)), CrossLevel.across(short_, others),
                "Eli, nearest, carries only his keep; Hal, next, has one to spare; Gus is beyond reach anyway");
        assertEquals(List.of(), CrossLevel.across(short_, List.of(carrying("Gus", 40, 64))), "Gus is 40 blocks off");
    }

    @Test
    void oneRunningLowItselfStillGivesToOneWithFewerButTwoAlikeDont() {
        Map<String, Integer> wanted = Map.of("Alice", CrossLevel.RATIONS_GIVEN, "Bob", CrossLevel.RATIONS_GIVEN, "Carol", CrossLevel.RATIONS_GIVEN);

        assertEquals(List.of(new CrossLevel.Transfer("Bob", "Alice", CrossLevel.Item.RATIONS, CrossLevel.RATIONS_GIVEN)),
                CrossLevel.pair(List.of(carrying("Alice", 0, 5), carrying("Bob", 1, 24)), Map.of(), wanted),
                "Bob, below his resupply mark, has 9 beyond his keep and is left with more than he gives Alice");
        assertEquals(List.of(), CrossLevel.pair(List.of(carrying("Alice", 0, 23), carrying("Bob", 1, 24)), Map.of(), wanted),
                "Bob would be left with fewer than Alice: they'd hand the same rations back and forth");
        assertEquals(List.of(new CrossLevel.Transfer("Bob", "Carol", CrossLevel.Item.RATIONS, CrossLevel.RATIONS_GIVEN)),
                CrossLevel.pair(List.of(carrying("Alice", 0, 12), carrying("Bob", 1, 40), carrying("Carol", 2, 3)), Map.of(), wanted),
                "the one carrying fewest is served first, though Alice is nearer");
    }

    @Test
    void nobodyCrossLevelsInContactOrFromASoldierLinkingUp() {
        List<SoldierStatus> fighting = List.of(
                soldier("Alice", 0, 0, new Report.Idle()),
                soldier("Bob", 2, 16, new Report.InContact(1)));
        List<SoldierStatus> rejoining = List.of(
                soldier("Alice", 0, 0, new Report.Idle()),
                soldier("Bob", 2, 16, new Report.LinkingUp()));

        assertEquals(List.of(), CrossLevel.pair(fighting, Map.of("Alice", 3)));
        assertEquals(List.of(), CrossLevel.pair(rejoining, Map.of("Alice", 3)));
    }
}
