package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where the camp's mine goes when it's spent: on the camp's ground, past its wall, clear of the flatten and the templates. */
class CampMineSitesTest {

    private static final Position CAMP = new Position(100, 64, 200);
    /** A flatten far from the camp: none of its ground. */
    private static final Flatten FAR = new Flatten(1000, 1000, 1010, 1010, 64);
    private static final BiPredicate<Integer, Integer> NONE = (x, z) -> false;

    @Test
    void theFirstSiteIsTodaysHeadOffTheCampsCentre() {
        assertEquals(new Position(100 + ChainOfCommand.MINE_HEAD_DX, ChainOfCommand.MINE_DEPTH, 200 + ChainOfCommand.MINE_HEAD_DZ),
                new CampMineSites().site(CAMP));
    }

    @Test
    void spentItMovesToTheNearestChunkCentrePastTheWallOnTheCampsGround() {
        CampMineSites mines = new CampMineSites();
        Position next = mines.spent(CAMP, FAR, NONE).orElseThrow();
        assertEquals(next, mines.site(CAMP));
        assertEquals(8, Math.floorMod((int) next.x(), 16), "a chunk's centre: " + next);
        assertEquals(8, Math.floorMod((int) next.z(), 16), "a chunk's centre: " + next);
        assertEquals(ChainOfCommand.MINE_DEPTH, (int) next.y());
        int chunkX = Math.floorDiv((int) next.x(), 16), chunkZ = Math.floorDiv((int) next.z(), 16);
        boolean crossesWall = chunkX * 16 + 15 >= 100 - SoldierRunner.WALL_RADIUS && chunkX * 16 <= 100 + SoldierRunner.WALL_RADIUS
                && chunkZ * 16 + 15 >= 200 - SoldierRunner.WALL_RADIUS && chunkZ * 16 <= 200 + SoldierRunner.WALL_RADIUS;
        assertFalse(crossesWall, "its chunk wholly past the wall line: " + next);
        // The nearest: no other chunk centre on the camp's ground and past the wall is nearer.
        double d = Math.hypot(next.x() - CAMP.x(), next.z() - CAMP.z());
        assertTrue(d < 2 * 16, "near the camp: " + next + ", " + d + " off");
    }

    @Test
    void everySiteIsTriedOnceAndAllAreOnTheCampsGround() {
        CampMineSites mines = new CampMineSites();
        Set<Position> seen = new HashSet<>(List.of(mines.site(CAMP)));
        double last = 0;
        for (Optional<Position> next = mines.spent(CAMP, FAR, NONE); next.isPresent(); next = mines.spent(CAMP, FAR, NONE)) {
            Position p = next.get();
            assertTrue(seen.add(p), "tried again: " + p);
            assertTrue(Math.max(Math.abs(p.x() - CAMP.x()), Math.abs(p.z() - CAMP.z())) <= CampMineSites.REACH, "on the camp's ground: " + p);
            double d = Math.hypot(p.x() - CAMP.x(), p.z() - CAMP.z());
            assertTrue(d >= last, "nearest first: " + p);
            last = d;
        }
        assertTrue(seen.size() > 8, "sites enough for a week of nights: " + seen.size());
        Position stays = mines.site(CAMP);
        assertEquals(Optional.empty(), mines.spent(CAMP, FAR, NONE), "every site tried");
        assertEquals(stays, mines.site(CAMP), "the mine left where it is");
    }

    @Test
    void noSiteCutsIntoTheFlattenOrItsBench() {
        // The ground east of the camp to flatten.
        Flatten east = new Flatten(112, 160, 150, 240, 64);
        CampMineSites mines = new CampMineSites();
        for (Optional<Position> next = mines.spent(CAMP, east, NONE); next.isPresent(); next = mines.spent(CAMP, east, NONE)) {
            Position p = next.get();
            assertFalse(DayWorkRules.cutsInto(east, (int) p.x(), (int) p.z()), "cuts into the flatten: " + p);
        }
    }

    @Test
    void noSiteIsInAChunkTheRegisterHoldsATemplateIn() {
        Position plain = new CampMineSites().spent(CAMP, FAR, NONE).orElseThrow();
        int chunkX = Math.floorDiv((int) plain.x(), 16), chunkZ = Math.floorDiv((int) plain.z(), 16);
        BiPredicate<Integer, Integer> wheat = (x, z) -> x == chunkX && z == chunkZ;
        Position next = new CampMineSites().spent(CAMP, FAR, wheat).orElseThrow();
        assertNotEquals(plain, next, "the wheat chunk passed over");
        assertFalse(wheat.test(Math.floorDiv((int) next.x(), 16), Math.floorDiv((int) next.z(), 16)));
    }

    @Test
    void theSiteAndTheTriedAreKeptAcrossARestart() {
        CampMineSites before = new CampMineSites();
        before.spent(CAMP, FAR, NONE);
        Position at = before.spent(CAMP, FAR, NONE).orElseThrow();
        CampMineSites after = new CampMineSites();
        after.restore(Optional.of(at), before.tried());
        assertEquals(at, after.site(CAMP));
        Position next = after.spent(CAMP, FAR, NONE).orElseThrow();
        assertFalse(before.tried().contains(next), "never back to a site tried before the restart: " + next);
    }

    @Test
    void aSiteOffAnotherCampsGroundIsntTheNewCamps() {
        CampMineSites mines = new CampMineSites();
        mines.spent(CAMP, FAR, NONE);
        Position moved = new Position(500, 64, 500);
        assertEquals(CampMineSites.first(moved), mines.site(moved), "the new camp's mine starts at its own first head");
    }
}
