package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The platoon leader's registers, each test its own database file. */
class RegistersTest {

    @TempDir
    Path state;

    /** A chunk kept by x and z, replaced by its next measure, and still there after a restart. */
    @Test
    void aChunkIsKeptByItsXAndZAcrossARestart() {
        Registers registers = new Registers(state.resolve("registers"));
        registers.put(new ChunkRecord(1, 3, ChunkRecord.Kind.FLATTENED, 62, Optional.of("95 blocks to dig"), 1_000));
        registers.put(new ChunkRecord(1, 3, ChunkRecord.Kind.FLOORED, 62, Optional.empty(), 2_000));
        registers.put(new ChunkRecord(5, 5, ChunkRecord.Kind.TREES, 63, Optional.of("PLANTED"), 2_000));

        Registers again = new Registers(state.resolve("registers"));

        assertEquals(Optional.of(new ChunkRecord(1, 3, ChunkRecord.Kind.FLOORED, 62, Optional.empty(), 2_000)), again.chunk(1, 3));
        assertEquals(List.of(1, 5), again.chunks().stream().map(ChunkRecord::chunkX).toList());
        assertEquals(Optional.empty(), again.chunk(2, 3));
    }

    /** A farm at a chunk is three steps on it, each kept, and how each stands updated in place. */
    @Test
    void aMissionsStepsAreKeptOnTheirChunk() {
        Registers registers = new Registers(state.resolve("registers"));
        String mission = "a farm at chunk (1, 3)";
        ObjectiveRecord flatten = registers.add(new ObjectiveRecord(0, mission, 0, "flatten to y=62", 1, 3, Optional.empty(),
                ObjectiveRecord.State.PLANNED, 0, Optional.empty()));
        registers.add(new ObjectiveRecord(0, mission, 1, "floor in cobblestone at y=62", 1, 3, Optional.empty(), ObjectiveRecord.State.PLANNED, 0, Optional.empty()));
        registers.add(new ObjectiveRecord(0, mission, 2, "the farm template at y=63", 1, 3, Optional.empty(), ObjectiveRecord.State.PLANNED, 0, Optional.empty()));

        registers.update(flatten.now(ObjectiveRecord.State.DONE, Optional.of("Bravo"), Optional.of("no block left to dig"), 5_000));

        List<ObjectiveRecord> steps = new Registers(state.resolve("registers")).objectivesOn(1, 3);
        assertEquals(List.of(0, 1, 2), steps.stream().map(ObjectiveRecord::step).toList());
        assertEquals(ObjectiveRecord.State.DONE, steps.getFirst().state());
        assertEquals(Optional.of("Bravo"), steps.getFirst().squad());
        assertEquals(List.of(), registers.objectivesOn(2, 3));
    }

    /** The same objective given again - a later day - is a row of its own; an update to one never kept fails loud. */
    @Test
    void anObjectiveGivenAgainIsARowOfItsOwnAndAnUpdateToNoneFailsLoud() {
        Registers registers = new Registers(state.resolve("registers"));
        ObjectiveRecord step = new ObjectiveRecord(0, "Flatten x=8..40 z=72..104 to y=63", 0, "KEEP", 1, 5, Optional.of("Charlie"),
                ObjectiveRecord.State.UNDER_WAY, 0, Optional.empty());
        ObjectiveRecord first = registers.add(step);
        registers.update(first.now(ObjectiveRecord.State.RELIEVED, first.squad(), Optional.empty(), 10));
        ObjectiveRecord again = registers.add(step);
        assertEquals(2, registers.objectives().size());
        assertEquals(Optional.of(again), registers.open("Charlie"), "the open one the last given");
        assertThrows(IllegalStateException.class, () -> registers.update(new ObjectiveRecord(99, "none", 0, "none", 0, 0, Optional.empty(),
                ObjectiveRecord.State.DONE, 0, Optional.empty())));
    }

    /**
     * A mission in a model's words is kept however long it runs. Live (field700), one of 257 characters wasn't taken by a
     * column of 256, and the platoon leader's whole decision went with it each cycle: the squads on the operator's
     * missions held the night's mine through the day.
     */
    @Test
    void aMissionSaidAtLengthIsKept() {
        Registers registers = new Registers(state.resolve("registers"));
        String mission = "Mine stone from the exposed deposits near x=64, y=12, z=48. ".repeat(20);
        ObjectiveRecord kept = registers.add(new ObjectiveRecord(0, mission, 0, "MINE", 3, 4, Optional.of("Alpha"), ObjectiveRecord.State.UNDER_WAY, 1_000, Optional.empty()));
        assertEquals(mission, registers.objectives().stream().filter(r -> r.id() == kept.id()).findFirst().orElseThrow().mission());
    }
}
