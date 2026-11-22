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
        assertEquals(List.of(ObjectiveRecord.State.SUPERSEDED), wrong.stream().map(ObjectiveRecord::state).distinct().toList(), "its steps relieved: " + wrong);
        assertEquals(3, registers.objectives().stream().filter(r -> r.mission().equals(FarmMission.atSeaLevel(1, 3).name())
                && r.state() == ObjectiveRecord.State.PLANNED).count(), "the sea-level one's planned");
    }

    /**
     * Carried on from the register after a restart, as field672 was: a mission superseded isn't open again, and a flatten
     * the register had DONE but the world shows uncut is gone back to. Live, the y=67 mission came back and was worked
     * first, and the y=63 one came back at its floor with its chunk 640 columns uncut.
     */
    @Test
    void reloadedOnlyTheMissionNotSupersededIsOpenAndAnUncutFlattenIsWorkedAgain() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission wrong = new FarmMission(1, 3, 67), right = FarmMission.atSeaLevel(1, 3);
        for (FarmMission.Step step : wrong.steps()) { // as kept before SUPERSEDED was its own state
            registers.add(new ObjectiveRecord(0, wrong.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(),
                    ObjectiveRecord.State.RELIEVED, 0, java.util.Optional.of("superseded by " + right.name())));
        }
        for (FarmMission.Step step : right.steps()) { // its flatten closed by a squad's "done" before that couldn't happen
            registers.add(new ObjectiveRecord(0, right.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(),
                    step.index() == 0 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
        }
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        io.github.jayessdeesea.minecraft.bot.world.WorldView hill = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 66; // the ground still at 66: nothing cut
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public java.util.List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return java.util.List.of();
            }
        };

        missions.measure(hill, null, new CampFarm(new WallStore(state, "farm"), 1000), 1_000);

        assertEquals(java.util.Map.entry(right, right.steps().getFirst()), missions.current().orElseThrow(), "the sea-level one, back at its flatten");
        assertEquals(ObjectiveRecord.State.PLANNED, registers.objectives().stream().filter(r -> r.mission().equals(right.name()) && r.step() == 0)
                .findFirst().orElseThrow().state(), "its flatten's row open again");
    }

    /**
     * The operator's cancel: the mission on the chunk taken off - the next given worked in its place, the platoon leader
     * woken - its phases SUPERSEDED with why, none deleted; a chunk with none open cancels nothing.
     */
    @Test
    void aCancelledMissionIsClosedAndTheNextWorked() {
        Registers registers = new Registers(state.resolve("registers"));
        List<String> woken = new java.util.ArrayList<>();
        FarmMissions missions = new FarmMissions(woken::add);
        missions.keptIn(new ObjectiveBook(() -> registers));
        missions.give(FarmMission.atSeaLevel(1, 3), 1_000);
        missions.give(FarmMission.atSeaLevel(2, 3), 1_000);

        assertEquals(java.util.Optional.empty(), missions.cancel(9, 9, "nothing there", 2_000));
        assertEquals(java.util.Optional.of(FarmMission.atSeaLevel(1, 3)), missions.cancel(1, 3, "cancelled by Operator: not needed", 2_000));

        assertEquals(FarmMission.atSeaLevel(2, 3), missions.current().orElseThrow().getKey(), "the next one worked");
        assertEquals(6, registers.objectives().size(), "none deleted");
        assertEquals(List.of(new Row(ObjectiveRecord.State.SUPERSEDED, java.util.Optional.of("cancelled by Operator: not needed"))),
                registers.objectives().stream().filter(r -> r.chunkX() == 1).map(r -> new Row(r.state(), r.why())).distinct().toList());
        assertEquals("the operator's farm cancelled", woken.getLast());
    }

    private record Row(ObjectiveRecord.State state, java.util.Optional<String> why) {
    }
}
