package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The registers said for the operator's chat: which operations are open, and the tasks and chunks filtered. */
class RegistersSaidTest {

    private static final FarmMission FARM = FarmMission.atSeaLevel(1, 3);

    private static List<ObjectiveRecord> farm(ObjectiveRecord.State... states) {
        List<ObjectiveRecord> rows = new ArrayList<>();
        for (FarmMission.Step s : FARM.steps()) {
            rows.add(new ObjectiveRecord(s.index() + 1, FARM.name(), s.index(), s.task(), 1, 3, Optional.empty(), states[s.index()], 0, Optional.empty()));
        }
        return rows;
    }

    /**
     * A farm mission is open while a phase isn't done - a phase's squad relieved, the phase stays open - and closed when every
     * phase is done or one is superseded (cancelled); a task of the platoon leader's own is open only while live.
     */
    @Test
    void whichOperationsAreOpen() {
        ObjectiveRecord.State done = ObjectiveRecord.State.DONE, relieved = ObjectiveRecord.State.RELIEVED, planned = ObjectiveRecord.State.PLANNED;
        assertTrue(RegistersSaid.operations(farm(done, relieved, planned)).getFirst().open());
        assertEquals(1, RegistersSaid.operations(farm(done, relieved, planned)).getFirst().current().orElseThrow().step(), "at the relieved phase");
        assertFalse(RegistersSaid.operations(farm(done, done, done)).getFirst().open());
        assertFalse(RegistersSaid.operations(farm(done, ObjectiveRecord.State.SUPERSEDED, ObjectiveRecord.State.SUPERSEDED)).getFirst().open());
        ObjectiveRecord own = new ObjectiveRecord(9, "Secure the camp", 0, "SECURE", 0, 0, Optional.of("Alpha"), relieved, 0, Optional.of("given Patrol"));
        assertFalse(RegistersSaid.operations(List.of(own)).getFirst().open(), "the platoon leader's own task, relieved, is closed");
    }

    @Test
    void tasksFilteredByStateAndMission() {
        List<ObjectiveRecord> rows = farm(ObjectiveRecord.State.DONE, ObjectiveRecord.State.UNDER_WAY, ObjectiveRecord.State.PLANNED);

        assertTrue(RegistersSaid.tasks(rows, Optional.empty(), Optional.of("done"), Optional.of("CHUNK (1, 3)"), 0)
                .startsWith("1 task(s) done of a mission naming \"CHUNK (1, 3)\":\n- task #1:"));
        assertTrue(RegistersSaid.tasks(rows, Optional.empty(), Optional.empty(), Optional.empty(), 0).startsWith("2 task(s) open:"), "open by default");
        assertEquals("No task open of a mission naming \"chunk (2, 2)\" is kept (3 task(s) kept in all).",
                RegistersSaid.tasks(rows, Optional.empty(), Optional.empty(), Optional.of("chunk (2, 2)"), 0));
        assertThrows(IllegalArgumentException.class, () -> RegistersSaid.tasks(rows, Optional.empty(), Optional.of("busy"), Optional.empty(), 0));
        assertEquals("No task #42 is kept: the numbers kept are [#1, #2, #3].", RegistersSaid.task(rows, 42, 0));
    }

    @Test
    void chunksFilteredByKind() {
        List<ChunkRecord> chunks = List.of(new ChunkRecord(1, 3, ChunkRecord.Kind.WHEAT, 63, Optional.of("SURVEYED"), 0),
                new ChunkRecord(-1, 0, ChunkRecord.Kind.TREES, 63, Optional.empty(), 0));

        assertEquals("1 WHEAT chunk(s) kept:\n- chunk (1, 3), x 16..31 z 48..63: WHEAT at y=63, SURVEYED, measured 1 min 05 s ago.",
                RegistersSaid.chunks(chunks, Optional.of("wheat"), 65_000));
        assertEquals("No FLOORED chunk is kept (2 chunk(s) kept in all).", RegistersSaid.chunks(chunks, Optional.of("FLOORED"), 0));
        assertThrows(IllegalArgumentException.class, () -> RegistersSaid.chunks(chunks, Optional.of("castle"), 0));
        assertTrue(RegistersSaid.chunkOf(chunks, List.of(), -1, -1, 0).startsWith("Block x=-1 z=-1 is in chunk (-1, -1).\nNothing is kept of chunk (-1, -1), x -16..-1 z -16..-1"));
    }
}
