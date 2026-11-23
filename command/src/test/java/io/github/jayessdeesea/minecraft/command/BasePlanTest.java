package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasePlanTest {

    private static final BasePlan BASE = BasePlan.ROUND_THE_ORIGIN;

    private static Set<Long> chunks(int... xz) {
        Set<Long> keys = new HashSet<>();
        for (int i = 0; i < xz.length; i += 2) {
            keys.add(BasePlan.key(xz[i], xz[i + 1]));
        }
        return keys;
    }

    /**
     * The base (operator, 2026-11-23): x and z -64..63 - 64 chunks, the camp's own left as it is - in clusters two
     * chunks by two: a mineshaft where chunk x and z are even, a supply point beside it, a wheat farm and a tree farm.
     */
    @Test
    void theBaseIsSixteenClustersOfAMineshaftASupplyPointAWheatFarmAndATreeFarm() {
        Map<FarmMission.Crop, Long> kinds = BASE.missions().stream().collect(Collectors.groupingBy(FarmMission::crop, Collectors.counting()));

        assertEquals(63, BASE.missions().size());
        assertEquals(Optional.empty(), BASE.template(0, 0), "the camp's chunk");
        assertEquals(Optional.empty(), BASE.template(4, 0), "outside it");
        assertEquals(Map.of(FarmMission.Crop.MINE, 15L, FarmMission.Crop.STORES, 16L, FarmMission.Crop.WHEAT, 16L, FarmMission.Crop.TREES, 16L), kinds);
        assertEquals(List.of(FarmMission.Crop.MINE, FarmMission.Crop.STORES, FarmMission.Crop.WHEAT, FarmMission.Crop.TREES),
                BASE.cluster(2, 2).stream().map(FarmMission::crop).toList(), "a cluster in the order it's built");
        assertEquals(List.of(new FarmMission(2, 2, 63, FarmMission.Crop.MINE), new FarmMission(3, 2, 63, FarmMission.Crop.STORES),
                new FarmMission(3, 3, 63, FarmMission.Crop.WHEAT), new FarmMission(2, 3, 63, FarmMission.Crop.TREES)), BASE.cluster(2, 2));
        assertEquals(Optional.of(FarmMission.Crop.WHEAT), BASE.template(1, 3), "the wheat farm that stands");
        assertEquals(Optional.of(FarmMission.Crop.TREES), BASE.template(2, 3), "the tree farm beside it");
        assertEquals(Optional.of(FarmMission.Crop.STORES), BASE.template(-3, -4), "the same on the far side of the origin");
        assertTrue(BASE.missions().stream().allMatch(m -> m.level() == 63), "at sea level");
    }

    /**
     * Never over-extended (operator, 2026-11-23: continue building where we are building; a mineshaft, then a supply
     * dump next to it, a wheat farm and a tree farm nearby, then another mineshaft two chunks away - the centre of mass
     * shifting to the origin a step at a time). From what stands live - a wheat farm at chunk (1, 3), tree farms at
     * (2, 3) and (3, 3) - the clusters begun are finished, the one nearer the origin first, each from its mineshaft; then
     * the cluster beside them nearest the origin.
     */
    @Test
    void aClusterBegunIsFinishedThenTheNextTowardTheOrigin() {
        Set<Long> taken = chunks(1, 3, 2, 3, 3, 3);
        List<FarmMission> given = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            FarmMission next = BASE.next(taken).orElseThrow();
            assertEquals(Optional.of(next), BASE.next(taken), "decided the same again");
            given.add(next);
            taken.add(BasePlan.key(next.chunkX(), next.chunkZ()));
        }

        assertEquals(List.of(
                new FarmMission(0, 2, 63, FarmMission.Crop.MINE), new FarmMission(1, 2, 63, FarmMission.Crop.STORES), new FarmMission(0, 3, 63, FarmMission.Crop.TREES),
                new FarmMission(2, 2, 63, FarmMission.Crop.MINE), new FarmMission(3, 2, 63, FarmMission.Crop.STORES),
                new FarmMission(1, 0, 63, FarmMission.Crop.STORES), new FarmMission(1, 1, 63, FarmMission.Crop.WHEAT), new FarmMission(0, 1, 63, FarmMission.Crop.TREES)),
                given, "the wheat farm's cluster, the tree farms', then the camp's own - beside them and nearest the origin");
    }

    /** With nothing built, the cluster nearest the origin, its mineshaft first; and every chunk taken, none. */
    @Test
    void withNothingBuiltTheClusterNearestTheOriginIsBegunFromItsMineshaft() {
        assertEquals(Optional.of(new FarmMission(-2, -2, 63, FarmMission.Crop.MINE)), BASE.next(Set.of()));
        assertEquals(Optional.of(new FarmMission(-1, -2, 63, FarmMission.Crop.STORES)), BASE.next(chunks(-2, -2)));
        assertEquals(Optional.empty(), BASE.next(BASE.missions().stream().map(m -> BasePlan.key(m.chunkX(), m.chunkZ())).collect(Collectors.toSet())));
    }

    /**
     * Food and wood first (operator, 2026-11-23: start with something easy, a reliable food source and a reliable wood
     * source, and bootstrap from it - careful not to over-extend): with neither had, a wheat farm, then a tree farm; with
     * both begun and not both built, nothing more; with both built, the clusters.
     */
    @Test
    void aWheatFarmAndATreeFarmAreBuiltBeforeAnythingElseIsBegun() {
        Set<FarmMission.Crop> none = Set.of(), wheat = Set.of(FarmMission.Crop.WHEAT), both = Set.of(FarmMission.Crop.WHEAT, FarmMission.Crop.TREES);

        assertEquals(Optional.of(new FarmMission(-1, -1, 63, FarmMission.Crop.WHEAT)), BASE.next(Set.of(), none, none), "food first, in the cluster nearest the origin");
        assertEquals(Optional.of(new FarmMission(-2, -1, 63, FarmMission.Crop.TREES)), BASE.next(chunks(-1, -1), wheat, none), "then wood, in the cluster begun");
        assertEquals(Optional.empty(), BASE.next(chunks(-1, -1, -2, -1), both, none), "both begun, neither built: nothing more");
        assertEquals(Optional.empty(), BASE.next(chunks(-1, -1, -2, -1), both, wheat), "nor with one built");
        assertEquals(Optional.of(new FarmMission(-2, -2, 63, FarmMission.Crop.MINE)), BASE.next(chunks(-1, -1, -2, -1), both, both), "both built: their cluster's mineshaft");
    }
}
