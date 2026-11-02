package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The hasty patrol base (docs/military-doctrine.md §14): a hut just big enough for the squad, walls
 * two high and a roof, built around its soldiers where they stand. Pure geometry, like
 * {@link Formations}: the general decides where; this decides which blocks.
 *
 * <p>Cells are block corners - integer coordinates. The roof covers the walls as well as the floor,
 * so every roof block has one to be placed against: wall tops first, then inward.
 */
public final class PatrolBase {

    /**
     * What to build, and where each soldier stands while it's built.
     *
     * @param stands  one floor cell's centre per soldier, in squad order
     * @param floor   the floor inside the walls
     * @param cells   blocks to place, bottom up: walls, then the roof's rim, then the roof
     */
    public record Plan(List<Position> stands, List<Position> floor, List<Position> cells) {

        /**
         * The way out toward {@code toward}: the two wall blocks, floor level and above, of a wall cell
         * beside the floor - never a corner, which no floor cell touches. Of the walls whose outside
         * cell a soldier can step onto ({@code walkable}, which knows the ground), the one nearest
         * {@code toward}; if none, the nearest anyway - a doorway onto a hillside, which the
         * soldiers' route-finding then reports.
         */
        public List<Position> doorway(Position toward, Predicate<Position> walkable) {
            Position best = null;
            double bestScore = Double.MAX_VALUE;
            for (Position cell : floor) {
                for (int[] side : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Position wall = new Position(cell.x() + side[0], cell.y(), cell.z() + side[1]);
                    if (floor.contains(wall)) {
                        continue;
                    }
                    Position outside = new Position(wall.x() + side[0], wall.y(), wall.z() + side[1]);
                    double score = Math.hypot(wall.x() + 0.5 - toward.x(), wall.z() + 0.5 - toward.z())
                            + (walkable.test(outside) ? 0 : BLOCKED_PENALTY);
                    if (score < bestScore) {
                        best = wall;
                        bestScore = score;
                    }
                }
            }
            return List.of(best, new Position(best.x(), best.y() + 1, best.z()));
        }

        /** Enough to rank any doorway onto open ground ahead of every one onto a hillside. */
        private static final double BLOCKED_PENALTY = 1e6;

        /** The floor cell's centre beside {@code doorway}'s lower block - where the soldier opening it stands. */
        public Position insideOf(List<Position> doorway) {
            Position wall = doorway.getFirst();
            return floor.stream()
                    .filter(cell -> Math.abs(cell.x() - wall.x()) + Math.abs(cell.z() - wall.z()) == 1)
                    .findFirst().map(cell -> new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5)).orElseThrow();
        }

        /** Whether {@code soldier} stands on the floor inside the walls. */
        public boolean inside(Position soldier) {
            return floor.stream().anyMatch(cell -> cell.x() == Math.floor(soldier.x()) && cell.z() == Math.floor(soldier.z())
                    && Math.abs(cell.y() - soldier.y()) < 1);
        }
    }

    private PatrolBase() {
    }

    /** The hut for {@code soldiers} soldiers with its floor at {@code location}. */
    public static Plan hasty(Position location, int soldiers) {
        if (soldiers < 1 || soldiers > 9) {
            throw new IllegalArgumentException("a hasty patrol base shelters 1 to 9 soldiers, not " + soldiers);
        }
        int side = soldiers <= 4 ? 2 : 3;
        int x0 = (int) Math.floor(location.x()) - (side - 1) / 2;
        int z0 = (int) Math.floor(location.z()) - (side - 1) / 2;
        int y = (int) Math.floor(location.y());

        List<Position> floor = new ArrayList<>();
        for (int z = z0; z < z0 + side; z++) {
            for (int x = x0; x < x0 + side; x++) {
                floor.add(new Position(x, y, z));
            }
        }
        List<Position> stands = floor.subList(0, soldiers).stream()
                .map(cell -> new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5)).toList();
        List<Position> ring = new ArrayList<>();
        for (int x = x0 - 1; x <= x0 + side; x++) {
            for (int z = z0 - 1; z <= z0 + side; z++) {
                if (x == x0 - 1 || x == x0 + side || z == z0 - 1 || z == z0 + side) {
                    ring.add(new Position(x, 0, z));
                }
            }
        }
        List<Position> cells = new ArrayList<>();
        for (int level = 0; level < 2; level++) {
            for (Position wall : ring) {
                cells.add(new Position(wall.x(), y + level, wall.z()));
            }
        }
        for (Position rim : ring) {
            cells.add(new Position(rim.x(), y + 2, rim.z()));
        }
        for (Position cell : floor) {
            cells.add(new Position(cell.x(), y + 2, cell.z()));
        }
        return new Plan(stands, List.copyOf(floor), List.copyOf(cells));
    }
}
