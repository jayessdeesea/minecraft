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
}
