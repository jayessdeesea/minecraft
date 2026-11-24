package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrillSquadLeaderTest {

    /**
     * A mine's objective is a depth: of two reachable spots over it, the one down the stairs, not the
     * hilltop beside them. Live, nearest by x and z alone sent a squad 66 blocks up a hill over its mine.
     */
    @Test
    void theNearestSpotCountsHeightSoAMineIsGoneDownTo() {
        Objective mine = new Objective("Cut the mine", new Position(0.5, 16, 0.5), "iron", new Objective.Task.Mine());
        TerrainSurvey terrain = new TerrainSurvey(0, 0, 8, 32, List.of(
                new TerrainSurvey.Sample(1, 0, TerrainSurvey.Ground.LAND, 82, true, 0),
                new TerrainSurvey.Sample(0, 2, TerrainSurvey.Ground.LAND, 40, true, 0)));

        assertEquals(Optional.of(new Position(0.5, 40, 2.5)), DrillSquadLeader.point(mine, terrain));
    }

    /** A tree farm's squad goes to its ground, not to the top of the canopy the survey finds over it. */
    @Test
    void aTreeFarmsPointIsItsGroundNotItsCanopy() {
        Objective farm = new Objective("Keep the tree farm at chunk (2, 3)", new Position(40.5, 64, 56.5), "Wood that grows back", new Objective.Task.TreeFarm());
        TerrainSurvey terrain = new TerrainSurvey(40, 56, 8, 32, List.of(
                new TerrainSurvey.Sample(41, 57, TerrainSurvey.Ground.LAND, 69, true, 0)));

        assertEquals(Optional.of(new Position(40.5, 64, 56.5)), DrillSquadLeader.point(farm, terrain));
    }

    /**
     * On a mission's ground work every soldier detached on a role comes back to the squad (operator, 2026-11-22; field696:
     * a tree farm's flatten with two of four, one a miner detached under an older order). On other work a detachment stands.
     */
    @Test
    void onAMissionsGroundWorkEverySoldierDetachedComesBack() {
        DrillSquadLeader bravo = new DrillSquadLeader("Bravo");
        Position chunk = new Position(40.5, 64, 56.5);
        List<SoldierStatus> squad = List.of(
                new SoldierStatus("Dana", chunk, new Order.Idle(), new Report.Idle(), Optional.empty()),
                new SoldierStatus("Eli", new Position(-37, 55, 50), new Order.Idle(), new Report.Idle(), Optional.empty()));
        bravo.receivePlans(java.util.Map.of("Eli", new Plan(Role.MINER, Optional.of(new Position(-30, 16, 40)), List.of())), java.util.Map.of());
        TerrainSurvey terrain = new TerrainSurvey(40, 56, 8, 32, List.of(new TerrainSurvey.Sample(40, 56, TerrainSurvey.Ground.LAND, 64, true, 0)));

        bravo.receiveObjective(new Objective("a tree farm at chunk (2, 3) at y=63: flatten", chunk, "the operator's tree farm", new Objective.Task.Keep()));
        assertEquals(List.of(Plan.Change.role("Eli", Role.RIFLEMAN, chunk)), bravo.decide(squad, terrain).orElseThrow().changes(), "the miner called in to the flatten");

        bravo.receiveObjective(new Objective("Secure the camp", chunk, "light", new Objective.Task.Secure()));
        assertEquals(List.of(), bravo.decide(squad, terrain).orElseThrow().changes(), "on other work the detachment stands");
    }
}
