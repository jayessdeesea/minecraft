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
                Optional.of(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 5)))));
        SoldierStatus lean = new SoldierStatus("Carol", new Position(1, 64, 0), SLOT, new Report.Idle(), Optional.of(new Condition(20, 20, 0,
                Optional.of(new Kit(Optional.of("wooden_sword"), 0, 0, 0, 0, 0, 3)))));

        assertEquals(List.of(new CrossLevel.Transfer("Bob", "Alice", CrossLevel.Item.RATIONS, CrossLevel.RATIONS_GIVEN)),
                CrossLevel.pair(List.of(hungry, hunter, lean), Map.of(), Map.of("Alice", CrossLevel.RATIONS_GIVEN)),
                "Carol keeps her two and has one to spare: not enough");
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
