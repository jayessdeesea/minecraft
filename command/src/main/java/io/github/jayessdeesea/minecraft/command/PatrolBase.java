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

        /**
         * Every way into the room: the cells just outside it that a flood fill from the floor, through
         * anything not {@code solid}, reaches - a wall or roof block missing, a dip under a wall, a
         * hole in the floor, whatever later broke one. Empty when the room is sealed. Checked again
         * and again, not once: anything can break a wall after it's built.
         */
        public List<Position> breaches(Predicate<Position> solid) {
            double minX = floor.stream().mapToDouble(Position::x).min().orElseThrow();
            double maxX = floor.stream().mapToDouble(Position::x).max().orElseThrow();
            double minZ = floor.stream().mapToDouble(Position::z).min().orElseThrow();
            double maxZ = floor.stream().mapToDouble(Position::z).max().orElseThrow();
            double y = floor.getFirst().y();
            java.util.Set<Position> seen = new java.util.HashSet<>();
            java.util.Set<Position> breaches = new java.util.TreeSet<>(
                    java.util.Comparator.comparingDouble(Position::y).thenComparingDouble(Position::x).thenComparingDouble(Position::z));
            java.util.ArrayDeque<Position> frontier = new java.util.ArrayDeque<>();
            for (Position cell : floor) {
                for (double level : new double[]{y, y + 1}) {
                    Position inside = new Position(cell.x(), level, cell.z());
                    if (!solid.test(inside) && seen.add(inside)) {
                        frontier.add(inside);
                    }
                }
            }
            while (!frontier.isEmpty()) {
                Position cell = frontier.poll();
                for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    Position next = new Position(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2]);
                    if (solid.test(next) || !seen.add(next)) {
                        continue;
                    }
                    boolean inRoom = next.x() >= minX && next.x() <= maxX && next.z() >= minZ && next.z() <= maxZ
                            && next.y() >= y && next.y() <= y + 1;
                    if (inRoom) {
                        frontier.add(next);
                    } else {
                        breaches.add(next);
                    }
                }
            }
            return List.copyOf(breaches);
        }

        /** Block light a torch gives its own cell; it falls by one a block, through open cells. */
        public static final int TORCH_LIGHT = 14;

        /**
         * The floor cells no torch lights - each one, with block light 0, is ground a monster can
         * spawn on once no soldier is within 24 blocks. Light spreads from each torch in {@code torches}
         * through cells that aren't {@code solid}, losing one per block.
         */
        public List<Position> unlit(Predicate<Position> solid, List<Position> torches) {
            java.util.Map<Position, Integer> light = new java.util.HashMap<>();
            java.util.ArrayDeque<Position> frontier = new java.util.ArrayDeque<>();
            for (Position torch : torches) {
                light.put(torch, TORCH_LIGHT);
                frontier.add(torch);
            }
            while (!frontier.isEmpty()) {
                Position cell = frontier.poll();
                int level = light.get(cell) - 1;
                if (level <= 0) {
                    continue;
                }
                for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                    Position next = new Position(cell.x() + d[0], cell.y() + d[1], cell.z() + d[2]);
                    if (!solid.test(next) && light.getOrDefault(next, 0) < level) {
                        light.put(next, level);
                        frontier.add(next);
                    }
                }
            }
            return floor.stream().filter(cell -> light.getOrDefault(cell, 0) == 0).toList();
        }

        /**
         * How many of the blocks to build already stand: a hut the squad built before a restart,
         * a doorway cut in it, is nearly all there. Whatever is {@code solid} counts, the ground's
         * own blocks too - a bank one wall can lean on saves those blocks just the same.
         */
        public int standing(Predicate<Position> solid) {
            return (int) cells.stream().filter(solid).count();
        }

        /** Whether {@code soldier} stands on the floor inside the walls. */
        public boolean inside(Position soldier) {
            return floor.stream().anyMatch(cell -> cell.x() == Math.floor(soldier.x()) && cell.z() == Math.floor(soldier.z())
                    && Math.abs(cell.y() - soldier.y()) < 1);
        }
    }

    private PatrolBase() {
    }

    /** The most floor a room can have and still be one a soldier is shut in: a 3x3 hut is 9. */
    static final int MAX_ROOM_CELLS = 16;

    /**
     * The room {@code soldier} is shut in, if it is: the cells at its feet a flood fill from where it
     * stands reaches through gaps a body fits through - open at the feet and at the head - when that
     * runs out within {@value #MAX_ROOM_CELLS} cells, with no block one high to step up onto and out
     * and no hole in the floor to drop through.
     * A gap one block high is wall: a soldier walled in by a half-built hut is as shut in as one in a
     * sealed one. Read from the world, not
     * remembered, so a squad that wakes up shut in - after a restart, say - knows it; the room
     * needn't be one the squad built. Nothing to build: {@code cells} is empty. A cell nobody knows
     * reads as open to {@code solid}, so ground not loaded yet never shuts anyone in.
     */
    public static java.util.Optional<Plan> enclosing(Position soldier, Predicate<Position> solid) {
        Position feet = new Position(Math.floor(soldier.x()), Math.floor(soldier.y()), Math.floor(soldier.z()));
        Predicate<Position> fits = cell -> !solid.test(cell) && !solid.test(new Position(cell.x(), cell.y() + 1, cell.z()));
        if (!fits.test(feet)) {
            return java.util.Optional.empty();
        }
        // Everywhere a body gets from here - a step down into a hole, a step up with headroom, a fall
        // - is the room; a room that grows past a hut's size is a way out. Live, a hut with a pit in
        // its floor, read as a way down and out, was never found, and three soldiers stood shut in it.
        java.util.Set<Position> room = new java.util.LinkedHashSet<>(List.of(feet));
        java.util.ArrayDeque<Position> frontier = new java.util.ArrayDeque<>(room);
        while (!frontier.isEmpty()) {
            Position cell = frontier.poll();
            java.util.List<Position> moves = new java.util.ArrayList<>();
            Position below = new Position(cell.x(), cell.y() - 1, cell.z());
            if (!solid.test(below)) {
                moves.add(below); // it falls
            } else {
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    Position next = new Position(cell.x() + d[0], cell.y(), cell.z() + d[1]);
                    Position up = new Position(next.x(), next.y() + 1, next.z());
                    if (fits.test(next)) {
                        moves.add(next);
                    } else if (fits.test(up) && !solid.test(new Position(cell.x(), cell.y() + 2, cell.z()))) {
                        moves.add(up); // a step up
                    }
                }
            }
            for (Position next : moves) {
                if (room.add(next)) {
                    if (room.size() > MAX_ROOM_CELLS) {
                        return java.util.Optional.empty();
                    }
                    frontier.add(next);
                }
            }
        }
        // Where a body stands, not falls through.
        room.removeIf(cell -> !solid.test(new Position(cell.x(), cell.y() - 1, cell.z())));
        List<Position> floor = room.stream()
                .sorted(java.util.Comparator.comparingDouble(Position::z).thenComparingDouble(Position::x)).toList();
        List<Position> stands = floor.stream().map(cell -> new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5)).toList();
        return java.util.Optional.of(new Plan(stands, floor, List.of()));
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
