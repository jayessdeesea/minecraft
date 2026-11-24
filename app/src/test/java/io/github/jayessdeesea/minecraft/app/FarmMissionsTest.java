package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        assertEquals(new io.github.jayessdeesea.minecraft.command.Flatten(16, 64, 31, 79, 62), missions.flatten().orElseThrow(), "the ground to flatten is the trees' chunk, to one under its level: its slab's");
        List<io.github.jayessdeesea.minecraft.command.Objective> steps = missions.objectives();
        // A step as its squad holds it - its place put on the ground the squad stands on - is still the mission's: worked by night.
        io.github.jayessdeesea.minecraft.command.Objective held = steps.getLast();
        assertTrue(missions.on(java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Objective(held.mission(),
                new io.github.jayessdeesea.minecraft.command.Position(held.location().x(), held.location().y() + 4, held.location().z()), held.intent(), held.task()))), "known by its mission");
        assertEquals(List.of("a farm at chunk (1, 3) at y=63: floor chunk (1, 3), x 16..31 z 48..63 in cobblestone at y=62",
                "a tree farm at chunk (1, 4) at y=63: flatten chunk (1, 4), x 16..31 z 64..79 to y=62"),
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
        ObjectiveRecord template = registers.objectives().stream().filter(r -> r.step() == 3).findFirst().orElseThrow();
        assertEquals(ObjectiveRecord.State.PLANNED, template.state(), "open again in the register: " + template);
        assertTrue(template.why().orElse("").startsWith("reviewed: its chest is gone; 9 of its 9 torches gone"), "with what was found: " + template);
    }


    private static io.github.jayessdeesea.minecraft.command.SquadStatus squad(String name) {
        return new io.github.jayessdeesea.minecraft.command.SquadStatus(name, new io.github.jayessdeesea.minecraft.command.Report.InPosition(), List.of(), List.of(), java.util.Optional.empty(), false);
    }

    private record Row(ObjectiveRecord.State state, java.util.Optional<String> why) {
    }

    /**
     * A mineshaft's mission: its ground as every template's - flattened, a slab one under its level, its level paved
     * (operator, 2026-11-23) - then its head, then its shaft - a MINE at the chunk's middle, its y the depth to cut to -
     * done when whoever works it says the mine there is dry (operator, 2026-11-22: a squad goes down one and mines it
     * till dry).
     */
    @Test
    void aMineshaftsShaftIsDoneWhenItsMineIsDry() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission shaft = new FarmMission(2, 4, 63, FarmMission.Crop.MINE);
        assertEquals("a mineshaft at chunk (2, 4) at y=63", shaft.name());
        assertEquals(java.util.Optional.of(shaft), FarmMission.named(shaft.name()));
        assertEquals(List.of(FarmMission.Kind.FLATTEN, FarmMission.Kind.FLOOR, FarmMission.Kind.FLOOR, FarmMission.Kind.MINE_HEAD, FarmMission.Kind.MINE),
                shaft.steps().stream().map(FarmMission.Step::kind).toList());
        assertEquals(List.of(62, 62, 63, 63, 63), shaft.steps().stream().map(FarmMission.Step::level).toList());
        for (FarmMission.Step step : shaft.steps()) {
            registers.add(new ObjectiveRecord(0, shaft.name(), step.index(), step.task(), 2, 4, java.util.Optional.empty(),
                    step.index() < 4 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
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
        assertEquals(ObjectiveRecord.State.DONE, registers.objectives().stream().filter(r -> r.step() == 4).findFirst().orElseThrow().state());
    }

    /**
     * A flatten whose chunk is cut and whose slopes hold columns nobody cuts is done with them left, and says which
     * (field697-698: a tree farm's chunk flat for five hours, nine columns ten blocks out up a hill handed from one soldier
     * to another every two minutes). Not at once: only when their count has stood a quarter of an hour.
     */
    @Test
    void aFlattenIsDoneWithSlopeColumnsNobodyReachesLeftAndSaid() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        FarmMission trees = new FarmMission(2, 3, 63, FarmMission.Crop.TREES);
        missions.give(trees, 0);
        // Flat at its slab's level but for one column ten out south-east of the chunk, a block over its slope's line there.
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 62 || x == 57 && z == 73 && y <= 73;
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

        missions.measure(world, null, null, null, 1_000);
        missions.measure(world, null, null, null, 1_000 + FarmMissions.SLOPE_PATIENCE_MILLIS - 1);
        assertEquals(List.of(FarmMission.Kind.FLATTEN), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "its slope's column still wanted");
        assertEquals(java.util.Set.of(), missions.stalled(), "not yet said as stalled");

        missions.measure(world, null, null, null, 1_000 + FarmMissions.SLOPE_PATIENCE_MILLIS);
        assertEquals(List.of(FarmMission.Kind.FLOOR), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "left, and its slab begun");
        ObjectiveRecord flatten = registers.objectives().stream().filter(r -> r.step() == 0).findFirst().orElseThrow();
        assertEquals(ObjectiveRecord.State.DONE, flatten.state());
        assertTrue(flatten.why().orElse("").contains("1 column(s) out on its slopes left as they are") && flatten.why().orElse("").contains("57,73 at y=73"), "said, with which: " + flatten);
    }

    /**
     * A mission whose measure hasn't changed in twenty minutes is said as stalled, once, and the platoon leader woken
     * (operator, 2026-11-23: there should be an alarm when we're not making forward progress): a wheat farm at its
     * template with ground still to lay and nobody laying it.
     */
    @Test
    void aMissionMakingNoProgressIsAlarmed() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission wheat = FarmMission.atSeaLevel(1, 3);
        for (FarmMission.Step step : wheat.steps()) {
            registers.add(new ObjectiveRecord(0, wheat.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(),
                    step.index() < 2 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
        }
        List<String> woken = new java.util.ArrayList<>();
        FarmMissions missions = new FarmMissions(woken::add);
        missions.keptIn(new ObjectiveBook(() -> registers));
        // Its floor at 62 and nothing laid over it: every cell of its ground still to fill.
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 62;
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
        CampFarm farms = new CampFarm(new WallStore(state, "farm"), 1000);

        missions.measure(world, null, farms, null, 1_000);
        missions.measure(world, null, farms, null, 1_000 + FarmMissions.STALL_MILLIS - 1);
        assertEquals(java.util.Set.of(), missions.stalled());
        woken.clear();
        missions.measure(world, null, farms, null, 1_000 + FarmMissions.STALL_MILLIS);
        assertEquals(java.util.Set.of(wheat), missions.stalled(), "said as stalled");
        assertEquals(List.of("a mission making no progress"), woken, "and the platoon leader woken, once");
        missions.measure(world, null, farms, null, 1_000 + 2 * FarmMissions.STALL_MILLIS);
        assertEquals(1, woken.size(), "not said again while nothing moves");
    }

    /**
     * A tree farm the register holds as it was - a flatten at its level and a template - is given the steps it has now
     * (operator, 2026-11-23: all the templates have cobblestone one under the target level, and at the level unless it
     * can't be): a flatten one lower, a slab, its level paved, the template - worked from the flatten. And its template
     * isn't done while its trees' cells are paving: each is dug out and laid in dirt.
     */
    @Test
    void aTreeFarmFromBeforeItsSlabIsWorkedAgainFromItsFlattenAndItsTreesGivenDirt() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission trees = new FarmMission(2, 3, 63, FarmMission.Crop.TREES);
        registers.add(new ObjectiveRecord(0, trees.name(), 0, "flatten chunk (2, 3), x 32..47 z 48..63 to y=63", 2, 3, java.util.Optional.of("Charlie"),
                ObjectiveRecord.State.DONE, 0, java.util.Optional.empty()));
        registers.add(new ObjectiveRecord(0, trees.name(), 1, "the tree farm template on chunk (2, 3), x 32..47 z 48..63 at y=63", 2, 3, java.util.Optional.of("Charlie"),
                ObjectiveRecord.State.UNDER_WAY, 0, java.util.Optional.empty()));
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        io.github.jayessdeesea.minecraft.bot.farm.TreeChunk chunk = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(2, 3, 63);
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> soil = java.util.Set.copyOf(chunk.soil());

        // The ground as it lies (0); cut, its slab in (1); its level paved (2); its trees' cells dug out (3); dirt in them (4).
        int[] stage = {0};
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            private boolean tree(int x, int y, int z) {
                return soil.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
            }

            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 62 || y == 63 && (stage[0] == 0 || stage[0] == 2 || stage[0] == 3 && !tree(x, y, z) || stage[0] == 4);
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return stage[0] >= 1 && (y == 62 || y == 63 && stage[0] >= 2 && !(stage[0] >= 3 && tree(x, y, z)));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return stage[0] == 4 && tree(x, y, z);
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
        missions.measure(world, null, null, null, 500);
        assertEquals(List.of(FarmMission.Kind.FLATTEN), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "from its flatten");
        assertEquals(trees.steps().stream().map(FarmMission.Step::task).toList(), registers.objectives().stream().map(ObjectiveRecord::task).toList(), "its rows the steps it has now");
        assertEquals(java.util.Collections.nCopies(4, ObjectiveRecord.State.PLANNED), registers.objectives().stream().map(ObjectiveRecord::state).toList());

        stage[0] = 1;
        for (int i2 = 1; i2 <= 3; i2++) {
            missions.measure(world, null, null, null, i2 * 1_000L);
        }
        assertEquals(List.of(63), missions.worked().stream().map(e -> e.getValue().level()).toList(), "its flatten and slab done as measured, its level to pave");
        stage[0] = 2;
        missions.measure(world, null, null, null, 4_000);
        missions.measure(world, null, null, null, 5_000);
        assertEquals(List.of(FarmMission.Kind.TREE_TEMPLATE), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "and paved, its template");
        assertEquals(java.util.Optional.of(chunk), missions.trees(missions.objectives().stream().findFirst()), "its squad's to give its trees dirt");
        assertEquals(List.of(16, 0), List.of(chunk.soilToDig(world).size(), chunk.soilToLay(world).size()), "every tree's cell paving, to dig out");
        stage[0] = 3;
        assertEquals(List.of(0, 16), List.of(chunk.soilToDig(world).size(), chunk.soilToLay(world).size()), "dug out, to lay in dirt");
        stage[0] = 4;
        assertEquals(List.of(0, 0), List.of(chunk.soilToDig(world).size(), chunk.soilToLay(world).size()), "dirt under each tree");
    }

    /**
     * The base's missions are given by rule, as squads come free (operator, 2026-11-23: a strategy of expanding influence,
     * never over-extended): never more open than squads to work them, the farm squad left to tend what's built; the
     * cluster begun finished from its mineshaft; and a chunk with a mission already is left as it is.
     */
    @Test
    void theBasesNextMissionIsGivenWhenASquadIsFreeForIt() {
        Registers registers = new Registers(state.resolve("registers"));
        // The wheat farm and the tree farm that stand, built: food and wood, before anything else is begun.
        for (FarmMission standing : List.of(FarmMission.atSeaLevel(1, 3), new FarmMission(2, 3, 63, FarmMission.Crop.TREES))) {
            for (FarmMission.Step step : standing.steps()) {
                registers.add(new ObjectiveRecord(0, standing.name(), step.index(), step.task(), standing.chunkX(), standing.chunkZ(), java.util.Optional.empty(),
                        ObjectiveRecord.State.DONE, 0, java.util.Optional.empty()));
            }
        }
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        List<io.github.jayessdeesea.minecraft.command.SquadStatus> squads = List.of(squad("Alpha"), squad("Bravo"), squad("Charlie"));
        List<io.github.jayessdeesea.minecraft.command.Objective> day = squads.stream().map(s -> new io.github.jayessdeesea.minecraft.command.Objective("Keep the wheat farms",
                new io.github.jayessdeesea.minecraft.command.Position(0.5, 64, 0.5), "Food for the force", new io.github.jayessdeesea.minecraft.command.Objective.Task.Farm())).toList();

        missions.guarded(day, squads, java.util.Optional.of("Alpha"));
        assertEquals(List.of(), missions.worked(), "no plan: nothing given unasked");

        missions.works(io.github.jayessdeesea.minecraft.command.BasePlan.ROUND_THE_ORIGIN);
        missions.guarded(day, squads, java.util.Optional.of("Alpha"));
        missions.guarded(day, squads, java.util.Optional.of("Alpha"));
        missions.guarded(day, squads, java.util.Optional.of("Alpha"));
        List<String> open = registers.objectives().stream().filter(r -> r.state() != ObjectiveRecord.State.DONE).map(ObjectiveRecord::mission).distinct().toList();
        assertEquals(java.util.Set.of("a mineshaft at chunk (0, 2) at y=63", "a supply point at chunk (1, 2) at y=63"), java.util.Set.copyOf(open),
                "the wheat farm's cluster: its mineshaft, then the supply point beside it; two open for the two squads but the farm's, and no third");
        List<io.github.jayessdeesea.minecraft.command.Objective> given = missions.guarded(day, squads, java.util.Optional.of("Alpha"));
        assertEquals("Keep the wheat farm at chunk (1, 3)", given.getFirst().mission(), "the farm squad tends what's built, by name");
        assertTrue(given.subList(1, 3).stream().anyMatch(o -> o.mission().startsWith("a mineshaft at chunk (0, 2)")), "a squad on the plan's mission: " + given);
    }

    /**
     * By the plan a squad with no mission's step keeps a farm that's built, at that farm - not the camp - and with none of
     * its kind built it joins the step under way (operator, 2026-11-23: a reliable food source and a reliable wood source
     * first). Live (field701), three squads stood at the camp with "no ground to farm by water", one on the tree farm's
     * flatten. And a step two squads work is one row in the register.
     */
    @Test
    void aSquadWithNoStepKeepsABuiltFarmOrJoinsTheStepUnderWay() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission wheat = FarmMission.atSeaLevel(1, 3), trees = new FarmMission(2, 3, 63, FarmMission.Crop.TREES);
        for (FarmMission.Step step : wheat.steps()) {
            registers.add(new ObjectiveRecord(0, wheat.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(), ObjectiveRecord.State.DONE, 0, java.util.Optional.empty()));
        }
        ObjectiveBook book = new ObjectiveBook(() -> registers);
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(book);
        missions.give(trees, 1_000);
        missions.works(io.github.jayessdeesea.minecraft.command.BasePlan.ROUND_THE_ORIGIN);
        List<io.github.jayessdeesea.minecraft.command.SquadStatus> squads = List.of(squad("Alpha"), squad("Bravo"), squad("Charlie"));
        io.github.jayessdeesea.minecraft.command.Position camp = new io.github.jayessdeesea.minecraft.command.Position(24.5, 67, 88.5);
        List<io.github.jayessdeesea.minecraft.command.Objective> day = List.of(
                new io.github.jayessdeesea.minecraft.command.Objective("Keep the wheat farms", camp, "Food for the force", new io.github.jayessdeesea.minecraft.command.Objective.Task.Farm()),
                new io.github.jayessdeesea.minecraft.command.Objective("Keep the tree farms", camp, "Wood that grows back", new io.github.jayessdeesea.minecraft.command.Objective.Task.TreeFarm()),
                new io.github.jayessdeesea.minecraft.command.Objective("Keep the wheat farms", camp, "Food for the force", new io.github.jayessdeesea.minecraft.command.Objective.Task.Farm()));

        List<io.github.jayessdeesea.minecraft.command.Objective> given = missions.guarded(day, squads, java.util.Optional.empty());

        String flatten = "a tree farm at chunk (2, 3) at y=63: flatten chunk (2, 3), x 32..47 z 48..63 to y=62";
        assertEquals(List.of("Keep the wheat farm at chunk (1, 3)", flatten, flatten), given.stream().map(io.github.jayessdeesea.minecraft.command.Objective::mission).toList(),
                "the wheat farm kept by one squad; the others on the tree farm's flatten - no tree farm stands to keep, and one wheat farm wants one squad");
        assertEquals(wheat.centre(wheat.steps().getLast()), given.getFirst().location(), "at the farm's chunk, not the camp");

        // With a farm squad, the wheat is its - whatever standing work it was dealt - and the others join the step.
        List<io.github.jayessdeesea.minecraft.command.Objective> withFarmSquad = missions.guarded(day, squads, java.util.Optional.of("Bravo"));
        assertEquals(List.of(flatten, "Keep the wheat farm at chunk (1, 3)", flatten), withFarmSquad.stream().map(io.github.jayessdeesea.minecraft.command.Objective::mission).toList());
        // Its task the wheat's too: live (field708), dealt "keep the tree farms", it kept the wheat farm by name with a tree
        // farm's task - "Keep the wheat farm at chunk (1, 3) (TREE_FARM)" - and the farm's fourteen posts were never put up.
        assertInstanceOf(io.github.jayessdeesea.minecraft.command.Objective.Task.Farm.class, withFarmSquad.get(1).task(), "the wheat farm's task is a farm's");
        assertEquals("Food for the force", withFarmSquad.get(1).intent(), "and its intent the wheat's, not the tree farms' it was dealt");

        book.given("Bravo", given.get(1), 2_000);
        book.given("Charlie", given.get(2), 2_000);
        assertEquals(1, registers.objectives().stream().filter(r -> r.mission().equals(trees.name()) && r.step() == 0).count(), "one row for the step both work");
        assertEquals(trees.steps().size(), registers.objectives().stream().filter(r -> !r.mission().equals(wheat.name())).count(), "and no row but the mission's own");
    }

    /**
     * A tree farm's paving leaves its sixteen trees' cells out - dirt, the template's to lay; its slab a block under takes
     * the whole chunk. Live (field704-707) a planted tree farm, open again at its paving, had thirteen trees' dirt paved over.
     */
    @Test
    void aTreeFarmsPavingLeavesItsTreesCellsOut() {
        FarmMissions missions = new FarmMissions(cause -> { });
        FarmMission trees = new FarmMission(5, 5, 63, FarmMission.Crop.TREES);
        missions.give(trees, 1_000);
        io.github.jayessdeesea.minecraft.bot.farm.FarmPlan none = new io.github.jayessdeesea.minecraft.bot.farm.FarmPlan(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(0, 63, 0), List.of());
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> soil = new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(5, 5, 63).soil();

        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> paved = missions.floorCells(trees.layer(63), none);
        assertEquals(256 - 16, paved.size(), "its level, less its trees' cells");
        assertTrue(paved.stream().noneMatch(soil::contains), "none of them a tree's");
        assertEquals(256, missions.floorCells(trees.layer(62), none).size(), "its slab, the whole chunk");
    }

    /**
     * A flatten cuts no slope into a chunk with a mission of its own open: that chunk is to be cut to its own level, and
     * a slope into it is the same ground cut twice. Live (field708-709) a supply point's flatten at chunk (1, 4) stood at
     * "up to 48 column(s)" for an hour, its crew on boxes x=32..38 - the mineshaft's chunk (2, 4), waiting behind it.
     */
    @Test
    void aFlattenCutsNoSlopeIntoAChunkWithAnOpenMission() {
        FarmMissions missions = new FarmMissions(cause -> { });
        FarmMission stores = new FarmMission(1, 4, 63, FarmMission.Crop.STORES), mine = new FarmMission(2, 4, 63, FarmMission.Crop.MINE);
        missions.give(stores, 0);
        missions.give(mine, 0);
        io.github.jayessdeesea.minecraft.command.Flatten ground = missions.flatten().orElseThrow();
        assertEquals(16, ground.minX(), "the supply point's, the first given: " + ground);

        List<io.github.jayessdeesea.minecraft.command.FlattenPlan.Column> reach = missions.reach(ground, null).orElseThrow();

        assertTrue(reach.stream().noneMatch(c -> c.x() > 31), "nothing east of it, the mineshaft's chunk: " + reach.stream().filter(c -> c.x() > 31).limit(4).toList());
        assertTrue(reach.stream().anyMatch(c -> c.x() < 16), "its west side sloped as before");
    }

    /**
     * A mission's place past its flatten is its own level's, kept as given; its flatten's is the ground's, found where the
     * squad stands. Live (field708-711) a tree farm's template at y=64 was put on its trees' canopy, y=69.
     */
    @Test
    void aMissionsPlacePastItsFlattenKeepsItsLevel() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        FarmMission trees = new FarmMission(2, 3, 63, FarmMission.Crop.TREES);
        missions.give(trees, 0);
        io.github.jayessdeesea.minecraft.command.Objective flatten = missions.objectives().getFirst();
        assertEquals(false, missions.atItsLevel(flatten), "its flatten: the ground is whatever stands there");

        io.github.jayessdeesea.minecraft.bot.world.WorldView flat = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 62;
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return y == 62;
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
        missions.measure(flat, null, null, null, 1_000);
        missions.measure(flat, null, null, null, 2_000);
        io.github.jayessdeesea.minecraft.command.Objective next = missions.objectives().getFirst();
        assertTrue(!next.mission().equals(flatten.mission()), "past its flatten: " + next.mission());
        assertTrue(missions.atItsLevel(next), "its place kept at its level: " + next.location());
        assertEquals(false, missions.atItsLevel(new io.github.jayessdeesea.minecraft.command.Objective("Hold the camp",
                new io.github.jayessdeesea.minecraft.command.Position(24.5, 67, 88.5), "A foothold", new io.github.jayessdeesea.minecraft.command.Objective.Task.None())), "any other objective is grounded as before");
    }
}
