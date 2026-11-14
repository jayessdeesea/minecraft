package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelPlanTest {

    private static List<LevelPlan.Column> block(int x0, int z0, int x1, int z1) {
        List<LevelPlan.Column> columns = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                columns.add(new LevelPlan.Column(x, z));
            }
        }
        return columns;
    }

    private static LevelPlan.Builder builder(String name, double x, double z) {
        return new LevelPlan.Builder(name, new Position(x, 64, z));
    }

    /**
     * A level 16 by 8 and four builders: each a rectangle, none overlapping, every column in exactly
     * one, the shares near equal - the whole crew on the level, in lock step, nobody idle.
     */
    @Test
    void aWideLevelIsSharedOutInRectanglesThatDontOverlap() {
        List<LevelPlan.Column> level = block(0, 0, 15, 7);
        List<LevelPlan.Builder> crew = List.of(builder("Alice", 0, 0), builder("Bob", 15, 0), builder("Carol", 0, 7), builder("Dana", 15, 7));

        Map<String, List<Box>> plan = LevelPlan.plan(70, 90, level, crew);

        assertEquals(Set.of("Alice", "Bob", "Carol", "Dana"), plan.keySet());
        Set<LevelPlan.Column> covered = new HashSet<>();
        for (List<Box> boxes : plan.values()) {
            for (Box b : boxes) {
                assertEquals(70, b.minY());
                assertEquals(90, b.maxY(), "the sky over its rectangle is its own");
                assertEquals(32, (b.maxX() - b.minX() + 1) * (b.maxZ() - b.minZ() + 1), "an equal share");
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        assertTrue(covered.add(new LevelPlan.Column(x, z)), "two rectangles hold " + x + "," + z);
                    }
                }
            }
        }
        assertEquals(new HashSet<>(level), covered);
    }

    /** A spire one block across is one builder's - the nearest - and the rest get nothing at this level. */
    @Test
    void aSpireIsOneBuildersAndTheNearestOnes() {
        List<LevelPlan.Builder> crew = List.of(builder("Alice", -10, 0), builder("Bob", 4, 4), builder("Carol", 12, -3));

        Map<String, List<Box>> plan = LevelPlan.plan(103, 103, List.of(new LevelPlan.Column(3, 3)), crew);

        assertEquals(Map.of("Bob", List.of(new Box(3, 103, 3, 3, 103, 3))), plan);
    }

    /** Fewer blocks than four a builder: fewer builders are sent, the nearest to each share. */
    @Test
    void aLevelWithLittleLeftTakesOnlyTheBuildersItHasWorkFor() {
        List<LevelPlan.Column> level = new ArrayList<>(block(0, 0, 1, 1));
        level.addAll(block(10, 0, 11, 1));
        List<LevelPlan.Builder> crew = List.of(builder("Alice", 0, 0), builder("Bob", 5, 5), builder("Carol", 11, 0), builder("Dana", 20, 20));

        Map<String, List<Box>> plan = LevelPlan.plan(70, 70, level, crew);

        assertEquals(Map.of("Alice", List.of(new Box(0, 70, 0, 1, 70, 1)), "Carol", List.of(new Box(10, 70, 0, 11, 70, 1))), plan);
    }

    /** Nothing left at the level, or nobody on hand: no plan. */
    @Test
    void noWorkOrNoBuildersIsNoPlan() {
        assertEquals(Map.of(), LevelPlan.plan(70, 70, List.of(), List.of(builder("Alice", 0, 0))));
        assertEquals(Map.of(), LevelPlan.plan(70, 70, block(0, 0, 3, 3), List.of()));
    }

    /** A line of columns one wide still splits, along its length. */
    @Test
    void aRidgeOneWideIsSplitAlongItsLength() {
        Map<String, List<Box>> plan = LevelPlan.plan(70, 70, block(0, 5, 15, 5), List.of(builder("Alice", 0, 5), builder("Bob", 15, 5)));

        assertEquals(Map.of("Alice", List.of(new Box(0, 70, 5, 7, 70, 5)), "Bob", List.of(new Box(8, 70, 5, 15, 70, 5))), plan);
    }
}
