package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The objective register's eval (operator, 2026-11-21: the squad leaders push updates to the platoon leader): a squad
 * driven through an objective's life - given, under way with its progress, CANTCO with why, back at it, done, and given
 * another - and its row following each push; then a restart, the open row found again.
 */
class ObjectiveBookTest {

    @TempDir
    Path state;

    private static final Objective FLATTEN = new Objective("Flatten x=8..40 z=72..104 to y=63", new Position(24, 63, 88), "Flat ground",
            new Objective.Task.Keep());
    private static final Objective FARM = new Objective("Work the camp's farm", new Position(-3, 65, 94), "Food", new Objective.Task.Farm());

    @Test
    void aSquadsObjectiveRowFollowsItsReports() {
        Registers registers = new Registers(state.resolve("registers"));
        ObjectiveBook book = new ObjectiveBook(() -> registers);

        book.given("Charlie", FLATTEN, 1_000);
        book.sitrep("Charlie", new Report.Working("dig the keep", 15), Optional.of(new Progress(Progress.State.UNDER_WAY, "8880 blocks to dig")), 2_000);
        assertEquals(new Row(ObjectiveRecord.State.UNDER_WAY, Optional.of("8880 blocks to dig")), row(registers, "Charlie"));

        book.sitrep("Charlie", new Report.Cantco("no route to (30, 72, 90)"), Optional.of(new Progress(Progress.State.UNDER_WAY, "8870 blocks to dig")), 3_000);
        assertEquals(new Row(ObjectiveRecord.State.CANTCO, Optional.of("no route to (30, 72, 90)")), row(registers, "Charlie"), "CANTCO with why");

        book.sitrep("Charlie", new Report.Working("dig the keep", 15), Optional.of(new Progress(Progress.State.STALLED, "nobody on it")), 4_000);
        assertEquals(new Row(ObjectiveRecord.State.UNDER_WAY, Optional.of("stalled: nobody on it")), row(registers, "Charlie"), "back at it, stalled said");

        book.sitrep("Charlie", new Report.InPosition(), Optional.of(new Progress(Progress.State.DONE, "no block left to dig")), 5_000);
        List<ObjectiveRecord> rows = registers.objectives();
        assertEquals(ObjectiveRecord.State.DONE, rows.getFirst().state(), "done, with what was measured");
        assertEquals(Optional.of("no block left to dig"), rows.getFirst().why());
        assertEquals(Optional.empty(), registers.open("Charlie"), "nothing open once it's done");

        book.given("Charlie", FARM, 6_000);
        book.given("Charlie", FLATTEN, 7_000);
        List<ObjectiveRecord> after = registers.objectives().stream().filter(r -> r.squad().equals(Optional.of("Charlie"))).toList();
        assertEquals(3, after.size(), "the flatten done, the farm relieved, the flatten given again: " + after);
        assertEquals(1, after.stream().filter(r -> r.state() == ObjectiveRecord.State.RELIEVED).count());

        ObjectiveBook restarted = new ObjectiveBook(() -> new Registers(state.resolve("registers")));
        restarted.held("Charlie", FLATTEN, 8_000);
        assertEquals(3, registers.objectives().stream().filter(r -> r.squad().equals(Optional.of("Charlie"))).count(),
                "held after a restart: the open row found, none written again");
        restarted.sitrep("Charlie", new Report.Working("dig the keep", 15), Optional.of(new Progress(Progress.State.UNDER_WAY, "100 blocks to dig")), 9_000);
        assertEquals(new Row(ObjectiveRecord.State.UNDER_WAY, Optional.of("100 blocks to dig")), row(registers, "Charlie"));
    }

    /**
     * A farm mission's steps: PLANNED when it's given, each taken up by the squad given it - its own row, not a new one -
     * relieved and taken up again, DONE only when measured; open after a restart at its first step not done.
     */
    @Test
    void aMissionsStepsArePlannedTakenUpAndDoneInTheirOwnRows() {
        Registers registers = new Registers(state.resolve("registers"));
        ObjectiveBook book = new ObjectiveBook(() -> registers);
        io.github.jayessdeesea.minecraft.command.FarmMission farm = io.github.jayessdeesea.minecraft.command.FarmMission.atSeaLevel(1, 3);
        book.planned(farm, 1_000);
        book.planned(farm, 1_500);
        assertEquals(List.of(ObjectiveRecord.State.PLANNED, ObjectiveRecord.State.PLANNED, ObjectiveRecord.State.PLANNED),
                registers.objectives().stream().map(ObjectiveRecord::state).toList(), "three rows, once");

        Objective flatten = new Objective(farm.said(farm.steps().getFirst()), new Position(24, 63, 56), "step 1", new Objective.Task.Keep());
        book.given("Alpha", flatten, 2_000);
        book.given("Alpha", FARM, 3_000);
        book.given("Alpha", flatten, 4_000);
        assertEquals(3, registers.objectives().stream().filter(r -> r.mission().equals(farm.name())).count(), "the step's own row, relieved and taken up again");
        assertEquals(new Row(ObjectiveRecord.State.UNDER_WAY, Optional.empty()), row(registers, "Alpha"));
        assertEquals(java.util.Map.of(farm.name(), 0), book.openMissions());

        book.done(farm, farm.steps().getFirst(), "nothing over y=62", 5_000);
        assertEquals(Optional.empty(), registers.open("Alpha"), "its squad's row closed");
        assertEquals(java.util.Map.of(farm.name(), 1), new ObjectiveBook(() -> registers).openMissions(), "after a restart, at its second step");
    }

    /**
     * A step two squads worked, done, stays done when the squad that joined it is given the next: live (field703-707) a
     * tree farm built and planted - "DONE, its 16 saplings in" - was open again after a restart at its paving, and the
     * pavers laid cobblestone over its trees' dirt. The squad that had joined the paving, given the template, relieved
     * the row it held a copy of - the paving's, another squad's, done that second.
     */
    @Test
    void aStepDoneStaysDoneWhenASquadThatJoinedItIsGivenTheNext() {
        Registers registers = new Registers(state.resolve("registers"));
        ObjectiveBook book = new ObjectiveBook(() -> registers);
        io.github.jayessdeesea.minecraft.command.FarmMission farm = io.github.jayessdeesea.minecraft.command.FarmMission.atSeaLevel(1, 3);
        book.planned(farm, 1_000);
        Objective first = new Objective(farm.said(farm.steps().getFirst()), new Position(24, 63, 56), "step 1", new Objective.Task.Keep());
        Objective second = new Objective(farm.said(farm.steps().get(1)), new Position(24, 63, 56), "step 2", new Objective.Task.Keep());
        book.given("Charlie", first, 2_000);
        book.given("Alpha", first, 2_500);
        book.given("Delta", first, 2_600);

        book.done(farm, farm.steps().getFirst(), "nothing over y=62", 3_000);
        book.given("Alpha", second, 3_000);
        book.sitrep("Delta", new Report.Working("pave", 3), Optional.of(new Progress(Progress.State.STALLED, "nobody on it")), 3_500); // its copy of the row is stale

        assertEquals(ObjectiveRecord.State.DONE, registers.objectives().stream().filter(r -> r.mission().equals(farm.name()) && r.step() == 0)
                .findFirst().orElseThrow().state(), "the first step's row: " + registers.objectives());
        assertEquals(java.util.Map.of(farm.name(), 1), new ObjectiveBook(() -> registers).openMissions(), "after a restart, at its second step");

        // And one that joins a step and is given other work leaves the step its squad's: under way, not relieved.
        book.given("Bravo", second, 4_000);
        book.given("Bravo", FARM, 5_000);
        assertEquals(ObjectiveRecord.State.UNDER_WAY, registers.objectives().stream().filter(r -> r.mission().equals(farm.name()) && r.step() == 1)
                .findFirst().orElseThrow().state(), "the second step still Alpha's, under way: " + registers.objectives());
    }

    private record Row(ObjectiveRecord.State state, Optional<String> why) {
    }

    private static Row row(Registers registers, String squad) {
        ObjectiveRecord r = registers.open(squad).orElseThrow(() -> new AssertionError(squad + " has no open objective: " + registers.objectives()));
        return new Row(r.state(), r.why());
    }

    /** A mission's step isn't closed by a squad's SITREP - only by the mission's own measure (field671: a night mine's "done"). */
    @Test
    void aMissionsStepIsNotDoneByASquadsSitrep() {
        Registers registers = new Registers(state.resolve("registers"));
        ObjectiveBook book = new ObjectiveBook(() -> registers);
        io.github.jayessdeesea.minecraft.command.FarmMission farm = io.github.jayessdeesea.minecraft.command.FarmMission.atSeaLevel(1, 3);
        book.planned(farm, 0);
        io.github.jayessdeesea.minecraft.command.FarmMission.Step flatten = farm.steps().getFirst();
        book.given("Charlie", new Objective(farm.name() + ": " + flatten.task(), new Position(24, 62, 56), "A farm", new Objective.Task.Keep()), 1_000);
        book.sitrep("Charlie", new Report.InPosition(), Optional.of(new Progress(Progress.State.DONE, "the stairs are cut down to y=12")), 2_000);
        assertEquals(ObjectiveRecord.State.UNDER_WAY, registers.objectives().stream().filter(r -> r.step() == 0).findFirst().orElseThrow().state());
        book.done(farm, flatten, "nothing over y=62", 3_000);
        assertEquals(ObjectiveRecord.State.DONE, registers.objectives().stream().filter(r -> r.step() == 0).findFirst().orElseThrow().state());
    }
}
