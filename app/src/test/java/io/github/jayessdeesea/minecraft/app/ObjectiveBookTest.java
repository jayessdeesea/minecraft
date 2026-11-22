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

    private record Row(ObjectiveRecord.State state, Optional<String> why) {
    }

    private static Row row(Registers registers, String squad) {
        ObjectiveRecord r = registers.open(squad).orElseThrow(() -> new AssertionError(squad + " has no open objective: " + registers.objectives()));
        return new Row(r.state(), r.why());
    }
}
