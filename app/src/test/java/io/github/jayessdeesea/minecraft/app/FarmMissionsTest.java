package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The operator's farm missions as orders: the latest for a chunk is the one worked. */
class FarmMissionsTest {

    @TempDir
    Path state;

    /**
     * A farm given for a chunk at another level relieves the open one there: live (field668), a mission given at y=67 for
     * what the operator asked at sea level had no other way to be put right.
     */
    @Test
    void theLatestFarmForAChunkIsTheOneWorked() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));

        missions.give(new FarmMission(1, 3, 67), 1_000);
        missions.give(FarmMission.atSeaLevel(1, 3), 2_000);

        assertEquals(FarmMission.atSeaLevel(1, 3), missions.current().orElseThrow().getKey(), "the one at sea level worked");
        List<ObjectiveRecord> wrong = registers.objectives().stream().filter(r -> r.mission().equals(new FarmMission(1, 3, 67).name())).toList();
        assertEquals(3, wrong.size());
        assertEquals(List.of(ObjectiveRecord.State.RELIEVED), wrong.stream().map(ObjectiveRecord::state).distinct().toList(), "its steps relieved: " + wrong);
        assertEquals(3, registers.objectives().stream().filter(r -> r.mission().equals(FarmMission.atSeaLevel(1, 3).name())
                && r.state() == ObjectiveRecord.State.PLANNED).count(), "the sea-level one's planned");
    }
}
