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
    void oneTorchLightsTheWholeFloorAndNoTorchLightsNone() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 9); // a 3x3 floor
        Set<Position> built = new HashSet<>(plan.cells());
        java.util.function.Predicate<Position> solid = cell -> cell.y() < 64 || built.contains(cell);

        assertEquals(plan.floor(), plan.unlit(solid, List.of()), "dark: every floor cell is spawn ground");
        assertEquals(List.of(), plan.unlit(solid, List.of(new Position(-1, 64, -1))), "a torch in a corner lights it all");
    }

    @Test
    void lightDoesntPassThroughWalls() {
        PatrolBase.Plan plan = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(plan.cells());
        java.util.function.Predicate<Position> solid = cell -> cell.y() < 64 || built.contains(cell);

        // A torch outside the walls lights nothing inside.
        assertEquals(plan.floor(), plan.unlit(solid, List.of(new Position(5, 64, 0))));
    }

    @Test
    void aSoldierInASealedHutIsShutInItsRoomAndCanCutItsWayOut() {
        PatrolBase.Plan hut = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(hut.cells());
        java.util.function.Predicate<Position> solid = cell -> cell.y() < 64 || built.contains(cell);

        PatrolBase.Plan room = PatrolBase.enclosing(new Position(1.3, 64, 0.7), solid).orElseThrow();

        assertEquals(hut.floor(), room.floor(), "the room is the hut's floor, found from the world");
        assertEquals(List.of(), room.cells(), "nothing to build");
        assertEquals(List.of(new Position(2, 64, 0), new Position(2, 65, 0)), room.doorway(new Position(50, 64, 0.5), outside -> true));
    }

    /**
     * As live: a sealed hut with a one-block pit in its floor, a soldier standing in it. Read as a
     * way down and out, the room was never found, and three soldiers stood shut in it all morning.
     */
    @Test
    void aHutWithAPitInItsFloorStillShutsItsSoldiersIn() {
        PatrolBase.Plan hut = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(hut.cells());
        Position pit = new Position(1, 63, 1);
        java.util.function.Predicate<Position> solid = cell -> (cell.y() < 64 && !cell.equals(pit)) || built.contains(cell);

        PatrolBase.Plan fromTheFloor = PatrolBase.enclosing(new Position(0.5, 64, 0.5), solid).orElseThrow();
        PatrolBase.Plan fromThePit = PatrolBase.enclosing(new Position(1.5, 63, 1.5), solid).orElseThrow();

        assertEquals(fromTheFloor.floor(), fromThePit.floor(), "one room, from wherever in it");
        assertTrue(fromTheFloor.floor().contains(pit), "the pit is the room's: " + fromTheFloor.floor());
        // Its way out is cut through the wall, not the air over the pit - live, a room dug into a
        // hill, the pit mid-row: the cell over it, with the room's floor beyond, was taken for a
        // doorway already open, and nobody cut one.
        java.util.Set<Position> air = new HashSet<>();
        for (int x = 0; x <= 2; x++) {
            air.add(new Position(x, 64, 0));
            air.add(new Position(x, 65, 0));
        }
        air.add(new Position(1, 63, 0));
        java.util.function.Predicate<Position> dug = cell -> cell.y() < 70 && !air.contains(cell);
        PatrolBase.Plan corridor = PatrolBase.enclosing(new Position(0.5, 64, 0.5), dug).orElseThrow();
        java.util.function.Predicate<Position> walkable = c -> !dug.test(c) && !dug.test(new Position(c.x(), c.y() + 1, c.z()))
                && dug.test(new Position(c.x(), c.y() - 1, c.z()));
        for (Position toward : List.of(new Position(50, 64, 0.5), new Position(-50, 64, 0.5), new Position(1.5, 64, 50), new Position(1.5, 64, -50))) {
            List<Position> door = corridor.doorway(toward, walkable);
            assertTrue(door.stream().allMatch(dug), "a doorway toward " + toward + " through the wall: " + door);
        }
        // And a pit that's a way out - a shaft down to open ground below - still isn't shut in.
        java.util.function.Predicate<Position> shaft = cell -> (cell.y() < 64 && !(cell.x() == 1 && cell.z() == 1 && cell.y() >= 50)
                && !(cell.y() >= 50 && cell.y() <= 51 && Math.abs(cell.x()) < 10 && Math.abs(cell.z()) < 10)) || built.contains(cell);
        assertTrue(PatrolBase.enclosing(new Position(0.5, 64, 0.5), shaft).isEmpty(), "a shaft down to a cave");
    }

    @Test
    void aSoldierInTheOpenOrInAHutWithAWayOutIsntShutIn() {
        PatrolBase.Plan hut = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(hut.cells());
        java.util.function.Predicate<Position> solid = cell -> cell.y() < 64 || built.contains(cell);

        assertTrue(PatrolBase.enclosing(new Position(10.5, 64, 10.5), solid).isEmpty(), "open ground");
        built.remove(new Position(2, 65, 1));
        assertTrue(PatrolBase.enclosing(new Position(0.5, 64, 0.5), solid).isPresent(), "a hole one high lets nobody out");
        built.remove(new Position(2, 64, 1));
        assertTrue(PatrolBase.enclosing(new Position(0.5, 64, 0.5), solid).isEmpty(), "a doorway does");
    }

    @Test
    void aHalfBuiltHutWithNoRoofStillShutsItsSoldiersInUnlessTheyCanStepOut() {
        PatrolBase.Plan hut = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(hut.cells().stream().filter(c -> c.y() < 66).toList());
        built.remove(new Position(-1, 64, 0));
        built.remove(new Position(0, 64, 2));
        java.util.function.Predicate<Position> solid = cell -> cell.y() < 64 || built.contains(cell);

        assertEquals(hut.floor(), PatrolBase.enclosing(new Position(0.5, 64, 0.5), solid).orElseThrow().floor(),
                "gaps one high under the wall top");
        built.remove(new Position(1, 65, -1));
        assertTrue(PatrolBase.enclosing(new Position(0.5, 64, 0.5), solid).isEmpty(), "a wall one high is a step out");
    }

    @Test
    void groundNobodyKnowsShutsNobodyIn() {
        // Only the hut's own walls are known: everything else - even below the floor - reads as open.
        PatrolBase.Plan hut = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(hut.cells());

        assertTrue(PatrolBase.enclosing(new Position(0.5, 64, 0.5), built::contains).isEmpty());
    }

    @Test
    void anOldHutWithItsDoorwayCutCountsAsNearlyAllStanding() {
        PatrolBase.Plan hut = PatrolBase.hasty(new Position(0, 64, 0), 4);
        Set<Position> built = new HashSet<>(hut.cells());
        hut.doorway(new Position(10, 64, 0), cell -> true).forEach(built::remove);

        assertEquals(hut.cells().size() - 2, hut.standing(built::contains));
        assertEquals(0, PatrolBase.hasty(new Position(0, 67, 0), 4).standing(built::contains), "its roof is no hut");
    }

    @Test
    void moreThanNineDontFit() {
        assertThrows(IllegalArgumentException.class, () -> PatrolBase.hasty(new Position(0, 64, 0), 10));
    }
}
