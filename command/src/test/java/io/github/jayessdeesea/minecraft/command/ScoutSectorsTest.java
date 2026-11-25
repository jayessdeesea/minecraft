package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A squad's ground to scout round a place: one rectangle a soldier, none overlapping, the square covered. */
class ScoutSectorsTest {

    private static final Position PLACE = new Position(100.5, 64, -20.5);

    @Test
    void everyPointOfTheSquareIsOneSoldiersAndOnlyOnes() {
        for (int soldiers = 1; soldiers <= 6; soldiers++) {
            List<ScoutSectors.Sector> sectors = ScoutSectors.of(PLACE, 32, soldiers);
            assertEquals(soldiers, sectors.size());
            for (double x = PLACE.x() - 32; x < PLACE.x() + 32; x += 3.7) {
                for (double z = PLACE.z() - 32; z < PLACE.z() + 32; z += 3.7) {
                    double px = x, pz = z;
                    assertEquals(1, sectors.stream().filter(s -> s.holds(px, pz)).count(), soldiers + " soldiers, at " + x + ", " + z);
                }
            }
        }
    }

    @Test
    void fourSoldiersTakeTheFourQuarters() {
        List<ScoutSectors.Sector> sectors = ScoutSectors.of(PLACE, 32, 4);

        assertEquals(List.of(new ScoutSectors.Sector(68.5, -52.5, 100.5, -20.5), new ScoutSectors.Sector(68.5, -20.5, 100.5, 11.5),
                new ScoutSectors.Sector(100.5, -52.5, 132.5, -20.5), new ScoutSectors.Sector(100.5, -20.5, 132.5, 11.5)), sectors);
    }

    @Test
    void aSectorsLegsAreItsQuartersMiddlesTheNearestThePlaceFirst() {
        ScoutSectors.Sector northWest = ScoutSectors.of(PLACE, 32, 4).getFirst();

        List<Position> legs = northWest.legs(PLACE);

        assertEquals(List.of(new Position(92.5, 64, -28.5), new Position(76.5, 64, -28.5), new Position(76.5, 64, -44.5), new Position(92.5, 64, -44.5)), legs,
                "from the quarter by the place, round");
        assertTrue(legs.stream().allMatch(leg -> northWest.holds(leg.x(), leg.z())));
    }

    @Test
    void noPointOfASectorIsOutOfSightOfALegsEnd() {
        for (int soldiers = 1; soldiers <= 6; soldiers++) {
            for (ScoutSectors.Sector sector : ScoutSectors.of(PLACE, 32, soldiers)) {
                List<Position> legs = sector.legs(PLACE);
                for (double x = sector.minX(); x <= sector.maxX(); x += 4) {
                    for (double z = sector.minZ(); z <= sector.maxZ(); z += 4) {
                        double px = x, pz = z;
                        assertTrue(legs.stream().anyMatch(leg -> Math.hypot(leg.x() - px, leg.z() - pz) <= 32), soldiers + " soldiers, at " + x + ", " + z);
                    }
                }
            }
        }
    }

    @Test
    void noSoldiersNoSectors() {
        assertEquals(List.of(), ScoutSectors.of(PLACE, 32, 0));
    }
}
