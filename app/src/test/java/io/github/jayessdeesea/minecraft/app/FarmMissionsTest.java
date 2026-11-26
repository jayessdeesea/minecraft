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
        List<io.github.jayessdeesea.minecraft.command.Objective> day = missions.guarded(List.of(steps.getFirst(), idle, idle), squads, java.util.Optional.empty(), java.util.Set.of(), false);
        assertEquals(steps.getFirst(), day.getFirst(), "Alpha keeps the floor");
        assertEquals(1, day.stream().filter(steps.get(1)::equals).count(), "one squad on the trees' flatten: " + day);
        assertEquals(1, day.stream().filter(idle::equals).count(), "the third left to its own order");

        // After a restart no squad's standing order says which step it had: the register does, and that squad goes back on it.
        new ObjectiveBook(() -> registers).given("Alpha", steps.get(1), 2_000);
        List<io.github.jayessdeesea.minecraft.command.Objective> restarted = missions.guarded(List.of(idle, idle, idle), squads, java.util.Optional.empty(), java.util.Set.of(), false);
        assertEquals(steps.get(1), restarted.get(0), "Alpha, on the trees' flatten in the register, keeps it - not the last squad free: " + restarted);
    }

    /** The ground a flatten step's objective names is its mission's chunk, to the step's level; a floor's names none, nor does no objective. */
    @Test
    void aFlattenStepsObjectiveNamesItsMissionsGround() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission wheat = FarmMission.atSeaLevel(1, 3), trees = new FarmMission(1, 4, 63, FarmMission.Crop.TREES);
        for (FarmMission.Step step : wheat.steps()) { // the wheat farm at its floor, as live
            registers.add(new ObjectiveRecord(0, wheat.name(), step.index(), step.task(), 1, 3, java.util.Optional.empty(),
                    step.index() == 0 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.PLANNED, 0, java.util.Optional.empty()));
        }
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        missions.give(trees, 1_000);

        List<io.github.jayessdeesea.minecraft.command.Objective> steps = missions.objectives();
        assertEquals(java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.Flatten(16, 64, 31, 79, 62)), missions.flatten(java.util.Optional.of(steps.get(1))),
                "the trees' flatten step names the trees' chunk");
        assertEquals(java.util.Optional.empty(), missions.flatten(java.util.Optional.of(steps.getFirst())), "a floor step names no ground");
        assertEquals(java.util.Optional.empty(), missions.flatten(java.util.Optional.empty()), "no objective names none");
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
        io.github.jayessdeesea.minecraft.command.Objective hold = new io.github.jayessdeesea.minecraft.command.Objective("Hold", new io.github.jayessdeesea.minecraft.command.Position(0, 64, 0), "");
        assertEquals(List.of(hold), restarted.guarded(List.of(hold), List.of(squadAt("Bravo", 40, 70)), java.util.Optional.empty(), java.util.Set.of(), false),
                "no review dealt before the first measure: nothing's known yet of when");
        restarted.measure(world, null, null, campTrees, 5_000);

        assertEquals(java.util.Optional.of(new io.github.jayessdeesea.minecraft.bot.farm.TreeChunk(1, 4, 63)), campTrees.chunk());
        assertEquals(List.of(), restarted.worked(), "the mission itself done: nothing worked");
        assertEquals(java.util.Set.of(trees), restarted.built(), "and read back as built, for the review");

        // The review (operator, 2026-11-22: once built, the construction might drift - who reviews and fixes it?;
        // 2026-11-26: anti-entropy is ordered by the platoon leader): its chest and torches gone. In sight, nobody ordered
        // to it, it isn't looked over; built before the restart it's due within the first day - the chunks built spread over
        // it - and the squad near it is ordered there.
        standing[0] = false;
        restarted.measure(world, null, null, campTrees, 6_000);
        assertEquals(List.of(), restarted.worked(), "in sight, but no review ordered: not looked over");
        io.github.jayessdeesea.minecraft.command.Objective idle = new io.github.jayessdeesea.minecraft.command.Objective("Hold", new io.github.jayessdeesea.minecraft.command.Position(0, 64, 0), "");
        assertEquals(List.of(idle, idle), restarted.guarded(List.of(idle, idle), List.of(squadAt("Alpha", 300, 300), squadAt("Bravo", 40, 70)),
                java.util.Optional.empty(), java.util.Set.of("Bravo"), false), "a squad on the operator's scouting isn't sent");
        long dayOn = 6_000 + io.github.jayessdeesea.minecraft.command.ReviewOrders.DAY_MILLIS;
        restarted.measure(world, null, null, campTrees, dayOn);
        List<io.github.jayessdeesea.minecraft.command.Objective> day = restarted.guarded(List.of(idle, idle), List.of(squadAt("Alpha", 300, 300), squadAt("Bravo", 40, 70)),
                java.util.Optional.empty(), java.util.Set.of(), false);
        assertEquals(BuiltReviews.objective(trees), day.get(1), "Bravo, near it, ordered to review it - Alpha out of reach: " + day);
        assertEquals(idle, day.get(0));
        restarted.measure(world, null, null, campTrees, java.util.Map.of("Bravo", List.of(new io.github.jayessdeesea.minecraft.command.Position(40, 64, 140))), dayOn + 1_000);
        assertEquals(List.of(), restarted.worked(), "not looked over before its squad's at it");
        restarted.measure(world, null, null, campTrees, java.util.Map.of("Bravo", List.of(new io.github.jayessdeesea.minecraft.command.Position(20.5, 64, 70.5))), dayOn + 2_000);
        assertEquals(List.of(FarmMission.Kind.TREE_TEMPLATE), restarted.worked().stream().map(e -> e.getValue().kind()).toList(), "its template worked again");
        ObjectiveRecord template = registers.objectives().stream().filter(r -> r.step() == 3).findFirst().orElseThrow();
        assertEquals(ObjectiveRecord.State.PLANNED, template.state(), "open again in the register: " + template);
        List<io.github.jayessdeesea.minecraft.command.Objective> repair = restarted.guarded(List.of(idle, idle), List.of(squadAt("Alpha", 300, 300), squadAt("Bravo", 20, 70)),
                java.util.Optional.empty(), java.util.Set.of(), false);
        assertEquals(restarted.objectives().getFirst(), repair.get(1), "the repair the task of the squad whose review found it, not the last squad free: " + repair);
        assertTrue(template.why().orElse("").startsWith("reviewed: its chest is gone; 9 of its 9 torches gone"), "with what was found: " + template);
    }


    /** A squad of one soldier standing at block ({@code x}, {@code z}). */
    private static io.github.jayessdeesea.minecraft.command.SquadStatus squadAt(String name, double x, double z) {
        return new io.github.jayessdeesea.minecraft.command.SquadStatus(name, new io.github.jayessdeesea.minecraft.command.Report.InPosition(),
                List.of(new io.github.jayessdeesea.minecraft.command.SoldierStatus(name + "-1", new io.github.jayessdeesea.minecraft.command.Position(x, 64, z),
                        new io.github.jayessdeesea.minecraft.command.Order.Idle(), new io.github.jayessdeesea.minecraft.command.Report.InPosition(), java.util.Optional.empty())),
                List.of(), java.util.Optional.empty(), false);
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
        // Its head standing, each fixture in its cell - but one chest, at first.
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> head = new java.util.HashSet<>();
        io.github.jayessdeesea.minecraft.bot.farm.MineHead layout = new io.github.jayessdeesea.minecraft.bot.farm.MineHead(2, 4, 63);
        layout.fixtures().values().forEach(head::addAll);
        head.removeAll(layout.torches()); // a torch is no block: it stands in its cell, solid to nothing
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos chest = layout.chests().getFirst();
        head.remove(chest);
        io.github.jayessdeesea.minecraft.bot.world.WorldView world = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || head.contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
            }

            @Override
            public boolean isTorch(int x, int y, int z) {
                return layout.torches().contains(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, y, z));
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

        // A head short of its pattern is put up again before the mining goes on (operator, 2026-11-24: its chests in a
        // pattern, its mouth ringed): the head's step opened, and the shaft's after it once the head stands.
        missions.measure(world, null, null, null, 5_000);
        assertEquals(List.of(FarmMission.Kind.MINE_HEAD), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "a chest short: its head worked again");
        head.add(chest);
        missions.measure(world, null, null, null, 5_000 + FarmMissions.REVIEW_MILLIS);
        assertEquals(List.of(FarmMission.Kind.MINE), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "its head standing: the shaft again");
        io.github.jayessdeesea.minecraft.command.Objective mine = missions.objectives().getFirst();
        assertTrue(mine.task() instanceof io.github.jayessdeesea.minecraft.command.Objective.Task.Mine, "its squad's task a MINE: " + mine);
        assertEquals(new io.github.jayessdeesea.minecraft.command.Position(40.5, io.github.jayessdeesea.minecraft.bot.supply.Staircase.DEEPEST, 72.5), mine.location(), "at the chunk's middle, its y the depth: the bottom");

        missions.mineDry(40, 72);
        missions.measure(world, null, null, null, 5_000 + 2 * FarmMissions.REVIEW_MILLIS);
        assertEquals(List.of(), missions.worked(), "dry: the mission done");
        assertEquals(ObjectiveRecord.State.DONE, registers.objectives().stream().filter(r -> r.step() == 4).findFirst().orElseThrow().state());
    }

    /**
     * A mineshaft whose stairs are stopped by water that won't block is PAUSED (operator, 2026-11-25): no squad on it, not
     * done, not superseded - kept so across a restart - and taken up again when resumed.
     */
    @Test
    void aMineshaftPausedIsHeldAcrossARestartTillResumed() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMission shaft = new FarmMission(2, 4, 63, FarmMission.Crop.MINE);
        for (FarmMission.Step step : shaft.steps()) {
            registers.add(new ObjectiveRecord(0, shaft.name(), step.index(), step.task(), 2, 4, java.util.Optional.of("Charlie"),
                    step.index() < 4 ? ObjectiveRecord.State.DONE : ObjectiveRecord.State.UNDER_WAY, 0, java.util.Optional.empty()));
        }
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        assertEquals(List.of(FarmMission.Kind.MINE), missions.worked().stream().map(e -> e.getValue().kind()).toList(), "its shaft worked");

        assertTrue(missions.minePaused(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(40, 64, 72), "water or lava beside step 15 at (40, 48, 72)", 1_000));

        assertEquals(List.of(), missions.worked(), "paused: no squad put on it");
        ObjectiveRecord row = registers.objectives().stream().filter(r -> r.step() == 4).findFirst().orElseThrow();
        assertEquals(ObjectiveRecord.State.PAUSED, row.state());
        assertEquals(java.util.Optional.of("water or lava beside step 15 at (40, 48, 72)"), row.why());
        FarmMissions restarted = new FarmMissions(cause -> { });
        restarted.keptIn(new ObjectiveBook(() -> registers));
        assertEquals(List.of(), restarted.worked(), "still paused after a restart");
        assertTrue(new ObjectiveBook(() -> registers).openMissions().containsKey(shaft.name()), "and still open: not done, not superseded");

        assertEquals(java.util.Optional.of(shaft), restarted.resume(2, 4, 2_000));
        assertEquals(List.of(FarmMission.Kind.MINE), restarted.worked().stream().map(e -> e.getValue().kind()).toList(), "resumed: its shaft worked again");
        assertEquals(java.util.Optional.empty(), restarted.resume(2, 4, 3_000), "nothing paused to resume");
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

        missions.guarded(day, squads, java.util.Optional.of("Alpha"), java.util.Set.of(), false);
        assertEquals(List.of(), missions.worked(), "no plan: nothing given unasked");

        missions.works(io.github.jayessdeesea.minecraft.command.BasePlan.ROUND_THE_ORIGIN);
        missions.guarded(day, squads, java.util.Optional.of("Alpha"), java.util.Set.of(), false);
        missions.guarded(day, squads, java.util.Optional.of("Alpha"), java.util.Set.of(), false);
        missions.guarded(day, squads, java.util.Optional.of("Alpha"), java.util.Set.of(), false);
        List<String> open = registers.objectives().stream().filter(r -> r.state() != ObjectiveRecord.State.DONE).map(ObjectiveRecord::mission).distinct().toList();
        assertEquals(java.util.Set.of("a mineshaft at chunk (0, 2) at y=63", "a supply point at chunk (1, 2) at y=63"), java.util.Set.copyOf(open),
                "the wheat farm's cluster: its mineshaft, then the supply point beside it; two open for the two squads but the farm's, and no third");
        List<io.github.jayessdeesea.minecraft.command.Objective> given = missions.guarded(day, squads, java.util.Optional.of("Alpha"), java.util.Set.of(), false);
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

        List<io.github.jayessdeesea.minecraft.command.Objective> given = missions.guarded(day, squads, java.util.Optional.empty(), java.util.Set.of(), false);

        String flatten = "a tree farm at chunk (2, 3) at y=63: flatten chunk (2, 3), x 32..47 z 48..63 to y=62";
        assertEquals(List.of("Keep the wheat farm at chunk (1, 3)", flatten, flatten), given.stream().map(io.github.jayessdeesea.minecraft.command.Objective::mission).toList(),
                "the wheat farm kept by one squad; the others on the tree farm's flatten - no tree farm stands to keep, and one wheat farm wants one squad");
        assertEquals(wheat.centre(wheat.steps().getLast()), given.getFirst().location(), "at the farm's chunk, not the camp");

        // With a farm squad, the wheat is its - whatever standing work it was dealt - and the others join the step.
        List<io.github.jayessdeesea.minecraft.command.Objective> withFarmSquad = missions.guarded(day, squads, java.util.Optional.of("Bravo"), java.util.Set.of(), false);
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

    /** A squad on a mission's wheat farm, building or built, keeps that chunk farm - known by its mission, wherever water lies. */
    @Test
    void aMissionsWheatFarmIsKnownByItsObjectiveBuildingOrBuilt() {
        Registers registers = new Registers(state.resolve("registers"));
        FarmMissions missions = new FarmMissions(cause -> { });
        missions.keptIn(new ObjectiveBook(() -> registers));
        FarmMission wheat = FarmMission.atSeaLevel(1, 3);
        missions.give(wheat, 0);
        assertEquals(java.util.Optional.empty(), missions.wheatFarm(java.util.Optional.of(missions.objectives().getFirst())), "its flatten: no farm yet");
        io.github.jayessdeesea.minecraft.command.Objective kept = new io.github.jayessdeesea.minecraft.command.Objective("Keep the wheat farm at chunk (1, 3)",
                wheat.centre(wheat.steps().getLast()), "Food for the force", new io.github.jayessdeesea.minecraft.command.Objective.Task.Farm());
        assertEquals(java.util.Optional.empty(), missions.wheatFarm(java.util.Optional.of(kept)), "nor one kept before it's built");
        io.github.jayessdeesea.minecraft.command.Objective elsewhere = new io.github.jayessdeesea.minecraft.command.Objective("Keep the wheat farms",
                new io.github.jayessdeesea.minecraft.command.Position(24.5, 67, 88.5), "Food for the force", new io.github.jayessdeesea.minecraft.command.Objective.Task.Farm());
        assertEquals(java.util.Optional.empty(), missions.wheatFarm(java.util.Optional.of(elsewhere)));
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

    /**
     * A mission's paving and its flatten leave out the columns another template stands on - its chunk and the fence line
     * past it, x0..x0+16, so the line a mission shares with the template west or north of it is the template's - and never
     * its own chunk's for being a template's itself. A template east of it shares the line in its own chunk, not the mission's.
     */
    @Test
    void aMissionsPavingAndFlattenLeaveOutTheTemplateBesideItItsSharedFenceLineIncluded() {
        FarmMissions missions = new FarmMissions(cause -> { });
        FarmMission mission = new FarmMission(5, 5, 63, FarmMission.Crop.STORES); // x 80..95, z 80..95
        missions.give(mission, 1_000);
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos water = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(0, 63, 0);
        java.util.function.BiFunction<Integer, Integer, io.github.jayessdeesea.minecraft.bot.farm.FarmPlan> ownAnd = (cx, cz) ->
                new io.github.jayessdeesea.minecraft.bot.farm.FarmPlan(water, List.of(), List.of(), List.of(
                        new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(5, 5, 63), new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(cx, cz, 63)));
        io.github.jayessdeesea.minecraft.bot.farm.FarmPlan own = ownAnd.apply(5, 5), west = ownAnd.apply(4, 5), east = ownAnd.apply(6, 5), north = ownAnd.apply(5, 4);

        assertEquals(256, missions.floorCells(mission.layer(63), own).size(), "its own chunk a template's: all of it paved");

        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> paved = missions.floorCells(mission.layer(63), west);
        assertEquals(256 - 16, paved.size(), "less the line it shares with the template west of it");
        assertTrue(paved.stream().noneMatch(c -> c.x() == 80), "none on x=80");
        List<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> southOfOne = missions.floorCells(mission.layer(63), north);
        assertEquals(256 - 16, southOfOne.size(), "and the line it shares with one north of it");
        assertTrue(southOfOne.stream().noneMatch(c -> c.z() == 80), "none on z=80");
        assertEquals(256, missions.floorCells(mission.layer(63), east).size(), "a template east of it: the shared line is x=96, none of its own");

        io.github.jayessdeesea.minecraft.command.Flatten ground = missions.flatten().orElseThrow();
        List<io.github.jayessdeesea.minecraft.command.FlattenPlan.Column> reach = missions.reach(ground, west).orElseThrow();
        assertTrue(reach.stream().noneMatch(c -> c.x() < 80), "the flatten: nothing west of the line, the template's side unsloped");
        assertTrue(reach.stream().noneMatch(c -> c.x() == 80 && c.z() >= 80 && c.z() <= 96), "nor on the shared line, the template's box z 80..96");
        assertTrue(reach.stream().anyMatch(c -> c.x() == 80 && c.z() < 80), "the line run on north past the template's box is its north slope's");
        assertEquals(15 * 16, reach.stream().filter(c -> c.x() >= 81 && c.x() <= 95 && c.z() >= 80 && c.z() <= 95).count(), "its chunk's other columns all worked");
        assertTrue(reach.stream().anyMatch(c -> c.x() > 95), "its east side sloped as before");
        List<io.github.jayessdeesea.minecraft.command.FlattenPlan.Column> westOfOne = missions.reach(ground, east).orElseThrow();
        assertTrue(westOfOne.stream().noneMatch(c -> c.x() >= 96), "nothing on the ground of the template east of it");
        assertEquals(256, westOfOne.stream().filter(c -> c.x() >= 80 && c.x() <= 95 && c.z() >= 80 && c.z() <= 95).count(), "its own chunk whole");
    }

    private static final io.github.jayessdeesea.minecraft.bot.farm.FarmPlan NO_FARM = new io.github.jayessdeesea.minecraft.bot.farm.FarmPlan(
            new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(0, 63, 0), List.of());

    private static io.github.jayessdeesea.minecraft.command.SoldierStatus paver(String name, double x, double y, double z,
                                                                                 io.github.jayessdeesea.minecraft.command.Report report) {
        return new io.github.jayessdeesea.minecraft.command.SoldierStatus(name, new io.github.jayessdeesea.minecraft.command.Position(x, y, z),
                new io.github.jayessdeesea.minecraft.command.Order.Idle(), report, java.util.Optional.empty());
    }

    /** The cells of chunk (0, 0)'s layer at y=63 in columns x {@code from}..{@code to}. */
    private static java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> columns(int from, int to) {
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> cells = new java.util.HashSet<>();
        for (int x = from; x <= to; x++) {
            for (int z = 0; z <= 15; z++) {
                cells.add(new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(x, 63, z));
            }
        }
        return cells;
    }

    /** With none of a squad in the recovery drill, a floor is dealt a quarter of the chunk each, west to east in the squad's order, none another's. */
    @Test
    void aFloorIsDealtAStripEachInTheSquadsOrder() {
        FarmMissions missions = new FarmMissions(cause -> { });
        FarmMission floor = FarmMission.atSeaLevel(0, 0);
        missions.give(floor, 1_000);
        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> squad = List.of(
                paver("Ann", 1.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle()),
                paver("Bob", 5.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Moving()),
                paver("Cat", 9.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Cantco("no cobblestone")),
                paver("Dan", 40.5, 70, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle()));

        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> crew = new SquadState().crew(squad);
        for (int k = 0; k < squad.size(); k++) {
            assertEquals(columns(4 * k, 4 * k + 3), java.util.Set.copyOf(missions.share(floor.layer(63), NO_FARM, crew, squad.get(k).name()).orElseThrow()),
                    squad.get(k).name() + "'s strip");
        }
    }

    /**
     * A paver the recovery drill has - stranded in a cave off the chunk, its stair out its work - is dealt none of the
     * floor, and the three others are dealt the whole layer: a strip held by one that can't come is cells nobody paves,
     * and a floor that never finishes. Out of the drill it's dealt in again, each a quarter as before.
     */
    @Test
    void aPaverInTheRecoveryDrillIsLeftOutAndTheRestAreDealtTheWholeFloor() {
        FarmMissions missions = new FarmMissions(cause -> { });
        FarmMission floor = FarmMission.atSeaLevel(0, 0);
        missions.give(floor, 1_000);
        io.github.jayessdeesea.minecraft.command.SoldierStatus bob = paver("Bob", 5.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle());
        io.github.jayessdeesea.minecraft.command.SoldierStatus cat = paver("Cat", 9.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle());
        io.github.jayessdeesea.minecraft.command.SoldierStatus dan = paver("Dan", 13.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle());
        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> stranded = List.of(
                paver("Ann", -24.5, 61, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Cantco("no route from (-25, 61, 8) to (2, 64, 8)")), bob, cat, dan);
        List<io.github.jayessdeesea.minecraft.command.Order> idle = java.util.Collections.nCopies(4, new io.github.jayessdeesea.minecraft.command.Order.Idle());
        SquadState state = new SquadState();
        for (int report = 0; report < SoldierRunner.STRANDED_REPORTS; report++) {
            state.recovery.orders(stranded, idle, s -> java.util.Optional.empty());
        }
        assertTrue(state.recovery.recovering("Ann"), "the drill has her");

        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> crew = state.crew(stranded);
        assertEquals(java.util.Optional.empty(), missions.share(floor.layer(63), NO_FARM, crew, "Ann"), "none hers, and she's no paver, while the drill has her");
        assertEquals(columns(0, 4), java.util.Set.copyOf(missions.share(floor.layer(63), NO_FARM, crew, "Bob").orElseThrow()), "the cells left in her columns, x 0..3, are Bob's");
        assertEquals(columns(5, 9), java.util.Set.copyOf(missions.share(floor.layer(63), NO_FARM, crew, "Cat").orElseThrow()));
        assertEquals(columns(10, 15), java.util.Set.copyOf(missions.share(floor.layer(63), NO_FARM, crew, "Dan").orElseThrow()));

        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> back = List.of(
                paver("Ann", 1.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle()), bob, cat, dan);
        state.recovery.orders(back, idle, s -> java.util.Optional.empty());
        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> whole = state.crew(back);
        for (int k = 0; k < back.size(); k++) {
            assertEquals(columns(4 * k, 4 * k + 3), java.util.Set.copyOf(missions.share(floor.layer(63), NO_FARM, whole, back.get(k).name()).orElseThrow()),
                    back.get(k).name() + "'s strip, all four dealt");
        }
    }

    /** One of a crew past the floor's last strip - more pavers than it has columns - is a paver with no cells, as it was. */
    @Test
    void aPaverPastTheFloorsLastStripHasNoCells() {
        FarmMissions missions = new FarmMissions(cause -> { });
        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> seventeen = java.util.stream.IntStream.range(0, 17)
                .mapToObj(k -> paver("S" + k, 1.5, 64, 8.5, new io.github.jayessdeesea.minecraft.command.Report.Idle())).toList();
        assertEquals(columns(15, 15), java.util.Set.copyOf(missions.share(FarmMission.atSeaLevel(0, 0).layer(63), NO_FARM, seventeen, "S15").orElseThrow()));
        assertEquals(java.util.Optional.of(List.of()), missions.share(FarmMission.atSeaLevel(0, 0).layer(63), NO_FARM, seventeen, "S16"));
    }
}
