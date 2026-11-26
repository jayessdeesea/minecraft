package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@link FlattenWork} answers over its own state and the values it's handed. */
class FlattenWorkTest {

    private static LevelPlan.Builder builder(String name) {
        return new LevelPlan.Builder(name, new Position(0, 64, 0));
    }

    @Test
    void theSumsReadTheSquadsCounts() {
        FlattenWork work = new FlattenWork();
        work.digLeftBySquad.put("Alpha", 5);
        work.digLeftBySquad.put("Bravo", 7);
        work.fillLeftBySquad.put("Alpha", -1);
        work.fillLeftBySquad.put("Bravo", -1);
        assertEquals(12, work.digLeft());
        assertFalse(work.fillKnown());
        work.fillLeftBySquad.put("Bravo", 30);
        assertTrue(work.fillKnown());
        assertEquals(30, work.fillLeft());
        work.oreAsideBySquad.put("Alpha", 2);
        work.oreAsideBySquad.put("Bravo", 3);
        assertEquals(3, work.oreAside());
        work.crews.put("Alpha", List.of(builder("A"), builder("B")));
        work.crews.put("Bravo", List.of(builder("C")));
        assertEquals(3, work.crewSize());
    }

    @Test
    void aFullChestMovesTheRowOnOnlyWhenItIsTheOneBeingFilled() {
        FlattenWork work = new FlattenWork();
        work.chests = List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(2, 64, 0));
        work.chestFull(new Position(1.5, 64, 0.5));
        assertEquals(0, work.chestAt);
        work.chestFull(new Position(0.5, 64, 0.5));
        assertEquals(1, work.chestAt);
        work.chestFull(new Position(0.5, 64, 0.5));
        assertEquals(1, work.chestAt);
        work.chestFull(new Position(1.5, 64, 0.5));
        assertEquals(2, work.chestAt);
        work.chestFull(new Position(2.5, 64, 0.5));
        assertEquals(2, work.chestAt);
    }

    @Test
    void theSmelterIsKeptWhileThereIsOreToSmeltOrToPutBack() {
        FlattenWork work = new FlattenWork();
        work.smelter = "Alice";
        List<LevelPlan.Builder> onHand = List.of(builder("Alice"), builder("Bob"));
        assertEquals("Alice", work.chooseSmelter(onHand, null, 3, Set.of(), n -> false));
        assertEquals("Alice", work.chooseSmelter(onHand, null, 0, Set.of("Alice"), n -> false));
        assertNull(work.chooseSmelter(onHand, null, 0, Set.of(), n -> false));
        assertNull(work.chooseSmelter(List.of(builder("Bob")), null, 3, Set.of(), n -> false));
    }

    @Test
    void noSmelterIsChosenWithNoDepot() {
        FlattenWork work = new FlattenWork();
        assertNull(work.chooseSmelter(List.of(builder("Bob")), null, FlattenWork.SMELT_BATCH + 10, Set.of(), n -> false));
    }

    @Test
    void theHaulRoadHasNoCutterWithNoDepot() {
        FlattenWork work = new FlattenWork();
        work.roadCutter = "Alice";
        work.haulRoad(List.of(builder("Alice")), 1_000, null, () -> null, n -> false);
        assertNull(work.roadCutter);
    }

    /** A pit is dug out once its delay is past and a fetcher near it has found nothing since it moved there. */
    @Test
    void aPitIsDugOutOnlyPastItsDelayWithAFetcherNearItFindingNothingSinceItMoved() {
        long moved = 1_000_000, now = moved + FlattenWork.PIT_MOVE_MILLIS;
        List<io.github.jayessdeesea.minecraft.command.SoldierStatus> near = List.of(status("Alice", 10, 0));
        Set<String> fetching = Set.of("Alice");
        Map<String, Long> foundNothing = Map.of("Alice", moved + 1);
        assertTrue(FlattenWork.pitDugOut(now, moved, near, fetching, foundNothing, 0, 0), "past its delay, a fetcher 10 off found nothing since");
        assertFalse(FlattenWork.pitDugOut(now - 1, moved, near, fetching, foundNothing, 0, 0), "a tick before its delay");
        assertFalse(FlattenWork.pitDugOut(now, moved, List.of(status("Alice", FlattenWork.PIT_NEAR + 1, 0)), fetching, foundNothing, 0, 0), "the fetcher too far");
        assertFalse(FlattenWork.pitDugOut(now, moved, near, Set.of(), foundNothing, 0, 0), "not fetching");
        assertFalse(FlattenWork.pitDugOut(now, moved, near, fetching, Map.of("Alice", moved), 0, 0), "found nothing before it moved there");
        assertFalse(FlattenWork.pitDugOut(now, moved, List.of(), fetching, foundNothing, 0, 0), "no hands");
    }

    /** The pit goes round the ground's sides in turn, each share its own order, always off the slope. */
    @Test
    void aPitGoesRoundTheSidesOffTheSlope() {
        io.github.jayessdeesea.minecraft.command.Flatten ground = new io.github.jayessdeesea.minecraft.command.Flatten(0, 0, 15, 31, 62);
        assertEquals(List.of(3, 0, 2, 3), java.util.stream.IntStream.range(0, 4).mapToObj(k -> FlattenWork.pitAt(ground, 0, k).side()).toList());
        assertEquals(List.of(0, 2, 3), java.util.stream.IntStream.range(0, 3).mapToObj(k -> FlattenWork.pitAt(ground, 1, k).side()).toList());
        for (int share = 0; share < 2; share++) {
            for (int k = 0; k < 3; k++) {
                FlattenWork.Pit pit = FlattenWork.pitAt(ground, share, k);
                int out = pit.side() <= 1 ? Math.abs(pit.x() - pit.midX()) : Math.abs(pit.z() - pit.midZ());
                assertTrue(out > pit.sideHalf() + io.github.jayessdeesea.minecraft.command.Flatten.BENCH, "off the slope: " + pit);
                assertEquals(pit.side() <= 1 ? pit.midZ() : pit.midX(), pit.side() <= 1 ? pit.z() : pit.x(), "on the ground's middle line: " + pit);
            }
        }
        assertEquals(8, FlattenWork.pitAt(ground, 0, 1).sideHalf(), "x half of a 16 across");
        assertEquals(16, FlattenWork.pitAt(ground, 0, 0).sideHalf(), "z half of a 32 deep");
    }

    private static io.github.jayessdeesea.minecraft.command.SoldierStatus status(String name, double x, double z) {
        return new io.github.jayessdeesea.minecraft.command.SoldierStatus(name, new Position(x, 64, z), new io.github.jayessdeesea.minecraft.command.Order.Idle(),
                new io.github.jayessdeesea.minecraft.command.Report.InPosition(), java.util.Optional.empty());
    }
}
