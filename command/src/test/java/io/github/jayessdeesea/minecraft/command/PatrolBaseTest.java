package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatrolBaseTest {

    @Test
    void threeSoldiersGetATwoByTwoHutWithWallsTwoHighAndARoof() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(10.7, 64, -3.2), 3);

        assertEquals(List.of(new Position(10.5, 64, -3.5), new Position(11.5, 64, -3.5), new Position(10.5, 64, -2.5)), plan.stands());
        assertEquals(4, plan.floor().size());
        // A 4x4 ring of 12 walls on two levels, the ring again as the roof's rim, then the 2x2 roof.
        assertEquals(12 * 2 + 12 + 4, plan.cells().size());
        assertEquals(plan.cells().size(), new HashSet<>(plan.cells()).size());
        assertTrue(plan.cells().stream().noneMatch(cell -> plan.floor().contains(cell)
                || plan.floor().contains(new Position(cell.x(), cell.y() - 1, cell.z()))), "nothing built in the room");
    }

    @Test
    void everyBlockHasOneBuiltBeforeItOrTheGroundToGoAgainst() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 6);
        Set<Position> built = new HashSet<>();

        for (Position cell : plan.cells()) {
            boolean supported = cell.y() == 64
                    || List.of(new int[]{0, -1, 0}, new int[]{1, 0, 0}, new int[]{-1, 0, 0}, new int[]{0, 0, 1}, new int[]{0, 0, -1}).stream()
                    .anyMatch(d -> built.contains(new Position(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2])));
            assertTrue(supported, "nothing to place " + cell + " against");
            built.add(cell);
        }
    }

    @Test
    void insideMeansOnTheFloorWithinTheWalls() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 3);

        assertTrue(plan.inside(new Position(1.9, 64, 1.1)));
        assertFalse(plan.inside(new Position(2.1, 64, 1.1)));
        assertFalse(plan.inside(new Position(0.5, 66, 0.5)));
    }

    @Test
    void theDoorwayOpensTowardWhereTheSquadGoesNext() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 4); // floor x 0..1, z 0..1

        List<Position> east = plan.doorway(new Position(50, 64, 0.5), outside -> true);

        assertEquals(List.of(new Position(2, 64, 0), new Position(2, 65, 0)), east);
        assertTrue(plan.cells().containsAll(east), "a doorway is cut through the wall");
        assertEquals(new Position(1.5, 64, 0.5), plan.insideOf(east));
        assertEquals(new Position(0, 64, -1), plan.doorway(new Position(0.5, 64, -50), outside -> true).getFirst());
    }

    @Test
    void aDoorwayOntoAHillsideGivesWayToOneOntoOpenGround() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 4); // floor x 0..1, z 0..1
        // A hill presses on the east wall: nothing to step onto at x=3.
        List<Position> door = plan.doorway(new Position(50, 64, 0.5), outside -> outside.x() < 3);

        assertTrue(door.getFirst().x() != 2, "not through the east wall: " + door);
        assertEquals(new Position(2, 64, 0), plan.doorway(new Position(50, 64, 0.5), outside -> false).getFirst(),
                "walled in on every side: the nearest anyway");
    }

    @Test
    void aFinishedHutIsSealedAndAnyHoleIsABreach() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(plan.cells());
        // Ground everywhere below the floor level.
        java.util.function.Predicate<Position> solid = cell -> cell.y() < 64 || built.contains(cell);

        assertEquals(List.of(), plan.breaches(solid), "sealed");

        built.remove(new Position(2, 65, 1)); // a wall block knocked out
        assertEquals(List.of(new Position(2, 65, 1)), plan.breaches(solid));

        built.add(new Position(2, 65, 1));
        built.remove(new Position(0, 66, 0)); // a hole in the roof
        assertEquals(List.of(new Position(0, 66, 0)), plan.breaches(solid));
    }

    @Test
    void aDipUnderTheFloorIsABreachToo() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(plan.cells());
        java.util.function.Predicate<Position> solid = cell -> (cell.y() < 64 && !cell.equals(new Position(1, 63, 1)))
                || built.contains(cell);

        assertEquals(List.of(new Position(1, 63, 1)), plan.breaches(solid), "a spider fits through a one-block hole");
    }

    @Test
    void moreThanNineDontFit() {
        assertThrows(IllegalArgumentException.class, () -> PatrolBase.hasty(new Position(0, 64, 0), 10));
    }
}
