package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkAreaTest {

    private static final Position DUMP = new Position(3, 64, 2);

    /** A farm's ground is the chunk its point is in. */
    @Test
    void aFarmIsItsPointsChunk() {
        Objective farm = new Objective("Farm", new Position(10.5, 64, 2.5), "bread", new Objective.Task.Farm());
        WorkArea area = WorkArea.of(farm, Optional.of(DUMP)).orElseThrow();

        assertEquals(List.of(new Box(0, 56, 0, 15, 88, 15)), area.boxes());
        assertEquals("in 1 box over x 0..15, z 0..15, the dump at (3, 64, 2)", area.line());
        Objective west = new Objective("Farm", new Position(-0.5, 64, -17), "bread", new Objective.Task.Farm());
        assertEquals(List.of(new Box(-16, 56, -32, -1, 88, -17)), WorkArea.of(west, Optional.empty()).orElseThrow().boxes(), "on the grid west and north too");
    }

    /** A wood is the chunks its woodcutters' reach touches: four by four round the origin. */
    @Test
    void aWoodIsTheChunksItsReachTouches() {
        Objective wood = new Objective("Wood", new Position(0, 64, 0), "logs", new Objective.Task.Gather(Resource.WOOD));
        WorkArea area = WorkArea.of(wood, Optional.empty()).orElseThrow();
        assertEquals(16, area.boxes().size());
        assertTrue(area.boxes().stream().allMatch(b -> Math.floorMod(b.minX(), 16) == 0 && Math.floorMod(b.minZ(), 16) == 0
                && b.maxX() - b.minX() == 15 && b.maxZ() - b.minZ() == 15), "whole chunks on the grid");
        assertEquals("in 16 boxes over x -32..31, z -32..31, no dump yet", area.line());
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
}
