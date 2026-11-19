package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkAreaTest {

    private static final Position DUMP = new Position(3, 64, 2);

    /** A farm is worked 16 each way round its point: 33 a side, in nine boxes of 11. */
    @Test
    void aFarmIsNineBoxesRoundItsPoint() {
        Objective farm = new Objective("Farm", new Position(10.5, 64, 2.5), "bread", new Objective.Task.Farm());
        WorkArea area = WorkArea.of(farm, Optional.of(DUMP)).orElseThrow();

        assertEquals(9, area.boxes().size());
        assertTrue(area.boxes().stream().allMatch(b -> b.maxX() - b.minX() + 1 <= WorkArea.SIDE && b.maxZ() - b.minZ() + 1 <= WorkArea.SIDE));
        assertEquals(33L * 33, area.boxes().stream().mapToLong(b -> (long) (b.maxX() - b.minX() + 1) * (b.maxZ() - b.minZ() + 1)).sum(),
                "the boxes tile the square, no cell twice, none left out");
        assertEquals("in 9 boxes over x -6..26, z -14..18, the dump at (3, 64, 2)", area.line());
    }

    /** A wood is worked its woodcutters' reach round its point: 49 a side, in sixteen boxes. */
    @Test
    void aWoodIsSixteenBoxes() {
        Objective wood = new Objective("Wood", new Position(0, 64, 0), "logs", new Objective.Task.Gather(Resource.WOOD));
        assertEquals(16, WorkArea.of(wood, Optional.empty()).orElseThrow().boxes().size());
        assertEquals("in 16 boxes over x -24..24, z -24..24, no dump yet", WorkArea.of(wood, Optional.empty()).orElseThrow().line());
    }

    /** A mine, a wall or the keep moves its point with the work: no area. */
    @Test
    void workNotDoneOverGroundHasNoArea() {
        Objective mine = new Objective("Mine", new Position(0, 12, 0), "ore", new Objective.Task.Mine());
        assertEquals(Optional.empty(), WorkArea.of(mine, Optional.of(DUMP)));
        assertSame(mine, WorkArea.attach(mine, Optional.of(DUMP)));
    }

    /** Attached once, it's the same objective attached again: a standing order isn't re-given for it. */
    @Test
    void attachingTwiceIsAttachingOnce() {
        Objective farm = WorkArea.attach(new Objective("Farm", new Position(0, 64, 0), "bread", new Objective.Task.Farm()), Optional.of(DUMP));
        assertSame(farm, WorkArea.attach(farm, Optional.of(DUMP)));
    }

    /** A run is cut into the fewest parts of at most 16, as near equal as they come. */
    @Test
    void runsAreTheFewestOfAtMostSixteen() {
        assertEquals(List.of("0..10", "11..21", "22..32"), WorkArea.runs(0, 32).stream().map(r -> r[0] + ".." + r[1]).toList());
        assertEquals(List.of("0..15"), WorkArea.runs(0, 15).stream().map(r -> r[0] + ".." + r[1]).toList());
    }
}
