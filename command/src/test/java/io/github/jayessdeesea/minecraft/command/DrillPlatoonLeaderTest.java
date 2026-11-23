package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrillPlatoonLeaderTest {

    private static SquadStatus squad(String name, Position at) {
        return new SquadStatus(name, new Report.Idle(), List.of(new SoldierStatus(name + "-1", at, new Order.Idle(), new Report.Idle(), Optional.empty())),
                List.of(), Optional.empty(), false);
    }

    /**
     * By rules, no model (operator, 2026-11-23): each squad is given the standing work - what's built is tended, the
     * squads turn about between the wheat and the trees, at the camp - and the same statuses give the same orders.
     */
    @Test
    void theSquadsTendWhatsBuiltTurnAbout() {
        DrillPlatoonLeader leader = new DrillPlatoonLeader();
        Position camp = new Position(0.5, 64, 0.5);
        leader.receiveFoothold(new Foothold(camp, List.of()));
        List<SquadStatus> squads = List.of(squad("Alpha", new Position(70, 12, 50)), squad("Bravo", camp), squad("Charlie", camp), squad("Delta", camp));

        List<Objective> given = leader.decide(squads);

        assertEquals(List.of(new Objective.Task.Farm(), new Objective.Task.TreeFarm(), new Objective.Task.Farm(), new Objective.Task.TreeFarm()),
                given.stream().map(Objective::task).toList());
        assertEquals(List.of(camp, camp, camp, camp), given.stream().map(Objective::location).toList(), "at the camp, wherever a squad stands");
        assertEquals(given, leader.decide(squads), "decided the same again");
    }

    /** With no camp yet there's nothing to tend: a squad holds where it stands. */
    @Test
    void withNoCampASquadHoldsWhereItStands() {
        Position here = new Position(12.5, 70, -3.5);
        List<Objective> given = new DrillPlatoonLeader().decide(List.of(squad("Alpha", here)));

        assertEquals(List.of(new Objective("Hold where the squad stands", here, "No camp yet: nothing to tend")), given);
    }
}
