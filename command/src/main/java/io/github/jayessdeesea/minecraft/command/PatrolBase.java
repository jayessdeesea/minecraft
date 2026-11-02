package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;

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
