package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        missions.measure(hill, null, new CampFarm(new WallStore(state, "farm"), 1000), null, 1_000);

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

    /**
     * A tree farm given beside a wheat farm at its floor is worked alongside it, a squad each (operator, 2026-11-22: a
     * command for the templated tree chunk right next to the wheat farm) - but a second mission's flatten waits for the
     * first's, since the squads flatten one ground.
     */
    @Test
    void aTreeFarmIsWorkedAlongsideAWheatFarmAndOneFlattenAtATime() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission wheat = FarmMission.atSeaLevel(1, 3), trees = new FarmMission(1, 4, 63, FarmMission.Crop.TREES), later = FarmMission.atSeaLevel(2, 3);
        for (FarmMission.Step step : wheat.steps()) { // the wheat farm at its floor, as live
            registers.add(new ObjectiveRecord(0, wheat.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(),
                    step.index() == 0 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
        }
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));

        missions.give(trees, 1_000);
        missions.give(later, 1_000);

        assertEquals(List.of(FarmMission.Kind.FLOOR, FarmMission.Kind.FLATTEN), missions.worked().stream().map(e -> e.getValue().kind()).toList(),
                "the wheat's floor and the trees' flatten; the later farm's flatten waits");
        assertEquals(List.of(wheat, trees), missions.worked().stream().map(java.util.Map.Entry::getKey).toList());
        assertEquals(new io.github.jayessdeesea.minecraft.command.Flatten(16, 64, 31, 79, 63), missions.flatten().orElseThrow(), "the ground to flatten is the trees' chunk, to its level");
        List<io.github.jayessdeesea.minecraft.command.Objective> steps = missions.objectives();
        assertEquals(List.of("a farm at chunk (1, 3) at y=63: floor chunk (1, 3), x 16..31 z 48..63 in cobblestone at y=62",
                "a tree farm at chunk (1, 4) at y=63: flatten chunk (1, 4), x 16..31 z 64..79 to y=63"),
                steps.stream().map(io.github.jayessdeesea.minecraft.command.Objective::mission).toList());

        // A squad to each: Alpha already on the floor keeps it, and another is put on the trees' flatten.
        io.github.jayessdeesea.minecraft.command.Objective idle = new io.github.jayessdeesea.minecraft.command.Objective("Hold the camp",
                new io.github.jayessdeesea.minecraft.command.Position(24, 64, 88), "nothing else to do");
        List<io.github.jayessdeesea.minecraft.command.SquadStatus> squads = List.of(squad("Alpha"), squad("Bravo"), squad("Charlie"));
        List<io.github.jayessdeesea.minecraft.command.Objective> day = missions.guarded(List.of(steps.getFirst(), idle, idle), squads, java.util.Optional.empty());
        assertEquals(steps.getFirst(), day.getFirst(), "Alpha keeps the floor");
        assertEquals(1, day.stream().filter(steps.get(1)::equals).count(), "one squad on the trees' flatten: " + day);
        assertEquals(1, day.stream().filter(idle::equals).count(), "the third left to its own order");
    }

    /**
     * Carried on from the register at its template, a farm isn't sent back to its flatten: the template's own ground stands
     * over the flatten's level. Live (field693), restarted with 17 cells of the template's dirt laid, the wheat farm's flatten
     * was "measured again - not cut", the squad set to dig the ground up, and the tree farm's flatten held behind it.
     */
    @Test
    void reloadedAtItsTemplateAFarmIsNotSentBackToItsFlatten() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission wheat = FarmMission.atSeaLevel(1, 3);
        for (FarmMission.Step step : wheat.steps()) {
            registers.add(new ObjectiveRecord(0, wheat.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(),
                    step.index() < 2 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
        }
        FarmMissions restarted = new FarmMissions(cause -> { });
        restarted.keptIn(new ObjectiveBook(() -> registers));

        // The floor at 62, and the template's dirt laid over it at 63 in the chunk: over the flatten's level.
        restarted.measure(new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 62 || y == 63 && x >= 16 && x <= 31 && z >= 48 && z <= 63;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return List.of();
            }
        }, null, new CampFarm(new WallStore(state, "farm"), 1000), null, 5_000);

        assertEquals(List.of(FarmMission.Kind.FARM_TEMPLATE), restarted.worked().stream().map(e -> e.getValue().kind()).toList(), "still at its template");
        assertEquals(List.of(ObjectiveRecord.State.DONE, ObjectiveRecord.State.DONE), registers.objectives().stream().filter(r -> r.step() < 2)
                .map(ObjectiveRecord::state).toList(), "its flatten and floor still done");
    }

    /** A tree chunk a mission made is the camp's tree chunk after a restart too: read back from its template's row, done. */
    @Test
    void theTreeChunkAMissionMadeIsTheCampsAfterARestart() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission trees = new FarmMission(1, 4, 63, FarmMission.Crop.TREES);
        for (FarmMission.Step step : trees.steps()) {
            registers.add(new ObjectiveRecord(0, trees.name(), step.index(), step.task(), 1, 4, java.util.Optional.empty(),
                    ObjectiveRecord.State.DONE, 0, java.util.Optional.empty()));
        }
        FarmMissions restarted = new FarmMissions(cause -> { });
        restarted.keptIn(new ObjectiveBook(() -> registers));
        CampTrees campTrees = new CampTrees();

        boolean[] standing = {true}; // its chest and its torches, as built
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63;
            }

            @Override
            public boolean isChest(int x, int y, int z) {
                return standing[0];
            }

            @Override
            public boolean isTorch(int x, int y, int z) {
                return standing[0];
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return List.of();
            }
        };
        restarted.measure(world, null, null, campTrees, 5_000);

        assertEquals(java.util.Optional.of(new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(1, 4, 63)), campTrees.chunk());
        assertEquals(List.of(), restarted.worked(), "the mission itself done: nothing worked");
        assertEquals(java.util.Set.of(trees), restarted.built(), "and read back as built, for the review");

        // The review (operator, 2026-11-22: once built, the construction might drift - who reviews and fixes it?): its chest and
        // torches gone, looked at again - not before the review's due - its template is opened again, and kept so.
        standing[0] = false;
        restarted.measure(world, null, null, campTrees, 5_000 + FarmMissions.REVIEW_MILLIS - 1);
        assertEquals(List.of(), restarted.worked(), "not looked over again before the review is due");
        restarted.measure(world, null, null, campTrees, 5_000 + FarmMissions.REVIEW_MILLIS);
        assertEquals(List.of(FarmMission.Kind.TREE_TEMPLATE), restarted.worked().stream().map(e -> e.getValue().kind()).toList(), "its template worked again");
        ObjectiveRecord template = registers.objectives().stream().filter(r -> r.step() == 1).findFirst().orElseThrow();
        assertEquals(ObjectiveRecord.State.PLANNED, template.state(), "open again in the register: " + template);
        assertTrue(template.why().orElse("").startsWith("reviewed: its chest is gone; 9 of its 9 torches gone"), "with what was found: " + template);
    }


    private static io.github.jayessdeesea.minecraft.command.SquadStatus squad(String name) {
        return new io.github.jayessdeesea.minecraft.command.SquadStatus(name, new io.github.jayessdeesea.minecraft.command.Report.InPosition(), List.of(), List.of(), java.util.Optional.empty(), false);
    }

    private record Row(ObjectiveRecord.State state, java.util.Optional<String> why) {
    }

    /**
     * A mineshaft's mission: its head, then its shaft - a MINE at the chunk's middle, its y the depth to cut to - done when
     * whoever works it says the mine there is dry (operator, 2026-11-22: a squad goes down one and mines it till dry).
     */
    @Test
    void aMineshaftsShaftIsDoneWhenItsMineIsDry() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission shaft = new FarmMission(2, 4, 63, FarmMission.Crop.MINE);
        assertEquals("a mineshaft at chunk (2, 4) at y=63", shaft.name());
        assertEquals(java.util.Optional.of(shaft), FarmMission.named(shaft.name()));
        for (FarmMission.Step step : shaft.steps()) {
            registers.add(new ObjectiveRecord(0, shaft.name(), step.index(), step.task(), 2, 4, java.util.Optional.empty(),
                    step.index() == 0 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
        }
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return List.of();
            }
        };

        missions.measure(world, null, null, null, 5_000);
        io.github.jayessdeesea.minecraft.command.Objective mine = missions.objectives().getFirst();
        assertTrue(mine.task() instanceof io.github.jayessdeesea.minecraft.command.Objective.Task.Mine, "its squad's task a MINE: " + mine);
        assertEquals(new io.github.jayessdeesea.minecraft.command.Position(40.5, ChainOfCommand.MINE_DEPTH, 72.5), mine.location(), "at the chunk's middle, its y the depth");

        missions.mineDry(40, 72);
        missions.measure(world, null, null, null, 6_000);
        assertEquals(List.of(), missions.worked(), "dry: the mission done");
        assertEquals(ObjectiveRecord.State.DONE, registers.objectives().stream().filter(r -> r.step() == 1).findFirst().orElseThrow().state());
    }
}
