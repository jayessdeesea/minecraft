package io.github.jayessdeesea.minecraft.command;

import java.util.Optional;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A farm at a chunk broken into its steps, and a chunk's flatten with its edges. */
class FarmMissionTest {

    /** A farm at chunk (1, 3) at sea level: flatten to 62, floor in cobblestone at 62, the template at 63. */
    @Test
    void aFarmIsAFlattenAFloorAndTheTemplateOnIt() {
        FarmMission farm = FarmMission.atSeaLevel(1, 3);
        assertEquals("a farm at chunk (1, 3) at y=63", farm.name());
        assertEquals(List.of(FarmMission.Kind.FLATTEN, FarmMission.Kind.FLOOR, FarmMission.Kind.FARM_TEMPLATE),
                farm.steps().stream().map(FarmMission.Step::kind).toList());
        assertEquals(List.of(62, 62, 63), farm.steps().stream().map(FarmMission.Step::level).toList());
        assertEquals("flatten chunk (1, 3), x 16..31 z 48..63 to y=62", farm.steps().getFirst().task());
        assertEquals(List.of(69, 69, 70), new FarmMission(1, 3, 70).steps().stream().map(FarmMission.Step::level).toList(), "at any height");
    }

    /**
     * A tree farm at chunk (1, 4) at sea level (operator, 2026-11-23: all the templates have cobblestone one under the
     * target level, and at the level unless it can't be): flatten to 62, a slab there, 63 paved, then the tree template -
     * which puts the dirt under its trees.
     */
    @Test
    void aTreeFarmIsAFlattenASlabOneUnderItsLevelItsLevelPavedAndTheTreeTemplateOnIt() {
        FarmMission trees = FarmMission.parse("20 70", FarmMission.Crop.TREES).orElseThrow();
        assertEquals(new FarmMission(1, 4, 63, FarmMission.Crop.TREES), trees);
        assertEquals("a tree farm at chunk (1, 4) at y=63", trees.name());
        assertEquals(List.of(FarmMission.Kind.FLATTEN, FarmMission.Kind.FLOOR, FarmMission.Kind.FLOOR, FarmMission.Kind.TREE_TEMPLATE), trees.steps().stream().map(FarmMission.Step::kind).toList());
        assertEquals(List.of(62, 62, 63, 63), trees.steps().stream().map(FarmMission.Step::level).toList());
        FarmMission stores = new FarmMission(1, 4, 63, FarmMission.Crop.STORES);
        assertEquals(List.of(62, 62, 63, 63), stores.steps().stream().map(FarmMission.Step::level).toList(), "a supply point's ground the same: the slab, then its level paved");
        assertEquals("flatten chunk (1, 4), x 16..31 z 64..79 to y=62", trees.steps().getFirst().task());
        assertEquals(java.util.Optional.of(trees), FarmMission.named(trees.name()), "read back from its name, as the register keeps it");
        assertEquals(java.util.Optional.of(FarmMission.atSeaLevel(1, 3)), FarmMission.named("a farm at chunk (1, 3) at y=63"), "a wheat farm's name still a wheat farm");
    }

    /** A chunk in a hill five high: its ground dug to 62, the hill stepped down a block a block out on each side. */
    @Test
    void aChunkCutIntoAHillIsSteppedAtItsEdges() {
        FlattenPlan plan = FlattenPlan.of(1, 3, 62, Slopes.ALL, (x, z) -> 67, (x, z) -> false);

        assertEquals(256, plan.dig().stream().filter(c -> inside(c.x(), c.z())).count(), "every column of the chunk dug");
        assertTrue(plan.dig().stream().filter(c -> inside(c.x(), c.z())).allMatch(c -> c.to() == 62));
        // Out from the east edge (x 31): a block a block out, to the hill's own 67 five out.
        for (int out = 1; out <= 4; out++) {
            int x = 31 + out;
            assertEquals(62 + out, plan.dig().stream().filter(c -> c.x() == x && c.z() == 50).findFirst().orElseThrow().to(), out + " out east");
        }
        assertTrue(plan.dig().stream().noneMatch(c -> c.x() == 36 && c.z() == 50), "five out, the hill's own");
        assertTrue(plan.steps().isEmpty(), "every side sloped: no step");
        assertFalse(plan.flat());
    }

    /** Beside a template the slope's off that side: nothing past the edge touched, the step to it said. */
    @Test
    void besideATemplateNothingPastTheEdgeIsTouchedAndTheStepIsSaid() {
        Slopes slopes = Slopes.around(1, 3, (cx, cz) -> cx == 2 && cz == 3); // a farm east, at chunk (2, 3)
        assertEquals(new Slopes(true, false, true, true), slopes);
        assertEquals("slopes north, south, west", slopes.said());

        FlattenPlan plan = FlattenPlan.of(1, 3, 62, slopes, (x, z) -> x >= 32 ? 65 : 67, (x, z) -> false);

        assertTrue(plan.dig().stream().noneMatch(c -> c.x() > 31), "nothing dug past the east edge: " + plan.dig().stream().filter(c -> c.x() > 31).limit(3).toList());
        assertEquals(List.of("the east edge 3 below what's past it: no walk between"), plan.steps());
    }

    /** A cell the force has made something of - a neighbour's fence on a sloped side's corner - is never dug. */
    @Test
    void aTemplatesCellIsNeverDug() {
        FlattenPlan plan = FlattenPlan.of(1, 3, 62, Slopes.ALL, (x, z) -> 67, (x, z) -> x == 32 && z == 47);
        assertTrue(plan.dig().stream().noneMatch(c -> c.x() == 32 && c.z() == 47));
        assertTrue(plan.dig().stream().anyMatch(c -> c.x() == 33 && c.z() == 47), "its neighbours still");
    }

    /** A chunk in a hollow is filled up, the ground round it raised a block a block out - no drop to fall off. */
    @Test
    void aChunkInAHollowIsFilledAndItsEdgesRaised() {
        FlattenPlan plan = FlattenPlan.of(1, 3, 62, Slopes.ALL, (x, z) -> inside(x, z) ? 58 : 55, (x, z) -> false);
        assertEquals(256, plan.fill().stream().filter(c -> inside(c.x(), c.z())).count());
        assertEquals(61, plan.fill().stream().filter(c -> c.x() == 32 && c.z() == 50).findFirst().orElseThrow().to(), "a block out, a block below");
        assertTrue(plan.fill().stream().noneMatch(c -> c.x() == 39 && c.z() == 50), "past where the slope meets the ground");
    }

    /** "!farm x z [y]": a block's x and z, the chunk it's in, sea level unless a level is given; named again from its name. */
    @Test
    void aFarmIsGivenByAPointAndALevelAndNamedAgainFromItsName() {
        assertEquals(java.util.Optional.of(new FarmMission(2, -2, 63)), FarmMission.parse("40 -20"));
        assertEquals(java.util.Optional.of(new FarmMission(-1, 0, 70)), FarmMission.parse("-1, 5, 70"));
        assertTrue(FarmMission.parse("40").isEmpty());
        assertTrue(FarmMission.parse("40 -20 north").isEmpty());
        FarmMission farm = new FarmMission(-3, 4, 70);
        assertEquals(java.util.Optional.of(farm), FarmMission.named(farm.name()));
        assertTrue(FarmMission.named("Keep the camp's farm").isEmpty());
        assertEquals(farm.name() + ": " + farm.steps().get(1).task(), farm.said(farm.steps().get(1)));
        assertEquals(new Box(-48, 69, 64, -33, 69, 79), farm.layer(69));
    }

    /** The flatten's columns: the chunk at its level, each ring out a block wider; a side unsloped, none past it; a kept column none. */
    @Test
    void theFlattensReachIsTheChunkAndItsSlopes() {
        List<FlattenPlan.Column> reach = FlattenPlan.reach(1, 3, 62, new Slopes(true, false, true, true), (x, z) -> x == 10 && z == 50);
        assertTrue(reach.stream().filter(c -> inside(c.x(), c.z())).allMatch(c -> c.from() == 62 && c.to() == 62));
        assertEquals(256, reach.stream().filter(c -> inside(c.x(), c.z())).count());
        assertEquals(new FlattenPlan.Column(13, 50, 59, 65), reach.stream().filter(c -> c.x() == 13 && c.z() == 50).findFirst().orElseThrow(), "three out west");
        assertTrue(reach.stream().noneMatch(c -> c.x() > 31), "nothing east, its slope off");
        assertTrue(reach.stream().noneMatch(c -> c.x() == 10 && c.z() == 50), "nor the kept column");
        assertEquals(16 - FlattenPlan.SLOPE_MOST, reach.stream().mapToInt(FlattenPlan.Column::x).min().orElseThrow(), "as far out as a flatten's bench");
        // Cut: nothing over the level; a fill under it - the floor's - doesn't hold it.
        assertTrue(FlattenPlan.of(1, 3, 62, Slopes.ALL, (x, z) -> inside(x, z) ? 60 : 62, (x, z) -> false).cut());
        assertFalse(FlattenPlan.of(1, 3, 62, Slopes.ALL, (x, z) -> 67, (x, z) -> false).cut());
    }

    /** A floor is one layer. */
    @Test
    void aFloorIsOneLayer() {
        assertEquals(62, new Objective.Task.Floor(new Box(16, 62, 48, 31, 62, 63)).layer().minY());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Objective.Task.Floor(new Box(16, 62, 48, 31, 63, 63)));
    }

    private static boolean inside(int x, int z) {
        return x >= 16 && x <= 31 && z >= 48 && z <= 63;
    }

    /** An animal farm is a mission like the others: named by what's kept, found again by its name, flattened, floored and laid out. */
    @Test
    void anAnimalFarmIsNamedFoundAgainAndWorkedInThreeSteps() {
        for (FarmMission.Crop crop : List.of(FarmMission.Crop.SHEEP, FarmMission.Crop.COW, FarmMission.Crop.PIG, FarmMission.Crop.CHICKEN)) {
            FarmMission farm = new FarmMission(1, 2, 63, crop);
            assertEquals("a " + crop.name().toLowerCase(java.util.Locale.ROOT) + " farm at chunk (1, 2) at y=63", farm.name());
            assertEquals(Optional.of(farm), FarmMission.named(farm.name()), "found again by its name");
            assertEquals(List.of(FarmMission.Kind.FLATTEN, FarmMission.Kind.FLOOR, FarmMission.Kind.PASTURE_TEMPLATE), farm.steps().stream().map(FarmMission.Step::kind).toList());
            assertEquals(List.of(62, 62, 63), farm.steps().stream().map(FarmMission.Step::level).toList(), "its slab one under its level");
        }
        FarmMission held = new FarmMission(0, 1, 63, FarmMission.Crop.PLACEHOLDER);
        assertEquals("a placeholder at chunk (0, 1) at y=63", held.name());
        assertEquals(Optional.of(held), FarmMission.named(held.name()), "found again by its name");
        assertEquals(List.of(FarmMission.Kind.FLATTEN, FarmMission.Kind.FLOOR, FarmMission.Kind.FLOOR, FarmMission.Kind.PLACEHOLDER_TEMPLATE),
                held.steps().stream().map(FarmMission.Step::kind).toList());
        assertEquals(List.of(62, 62, 63, 63), held.steps().stream().map(FarmMission.Step::level).toList(), "cobblestone one under its level, and at it");
        // And the names that were, read as they were.
        assertEquals(FarmMission.Crop.WHEAT, FarmMission.named("a farm at chunk (1, 3) at y=63").orElseThrow().crop());
        assertEquals(FarmMission.Crop.TREES, FarmMission.named("a tree farm at chunk (2, 3) at y=63").orElseThrow().crop());
        assertEquals(FarmMission.Crop.STORES, FarmMission.named("a supply point at chunk (1, 2) at y=63").orElseThrow().crop());
        assertEquals(FarmMission.Crop.MINE, FarmMission.named("a mineshaft at chunk (0, 2) at y=63").orElseThrow().crop());
    }
}
