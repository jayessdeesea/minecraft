package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The wall round a camp (docs/military-doctrine.md §15): a square ring {@code radius} blocks out
 * from a centre, two blocks high on the ground of each of its columns - a body can't jump it, and
 * nothing walks in from the dark - with one doorway, a column wide, in the middle of its south side:
 * the squads' way in and out, rather than a step cut through the wall. A column over water, or with
 * no ground near the centre's height, is left out. On top, a lip: a ring of blocks one out from the
 * wall at its top row, an overhang a spider climbing the wall's face can't get round. None outside
 * the doorway, where it would be a block at head height in the way out.
 */
public final class Perimeter {

    /** Blocks high: one more than a jump. */
    public static final int HEIGHT = 2;
    private static final int VERTICAL = 6;

    private Perimeter() {
    }

    /**
     * The wall round {@code centre}, laid out once on the ground as it is - laid out again after a
     * row is built, the ground would be the wall's top. Bottom row first, then nearest the centre's line.
     */
    public static List<BlockPos> layout(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius || dx == 0 && dz == radius) {
                    continue; // inside or outside the ring - or the doorway, mid-south, the way in and out
                }
                ground(world, centre.x() + dx, centre.z() + dz, centre.y()).ifPresent(feet -> {
                    for (int up = 0; up < HEIGHT; up++) {
                        cells.add(feet.offset(0, up, 0));
                    }
                });
            }
        }
        cells.sort(Comparator.comparingInt(BlockPos::y).thenComparingInt(c -> Math.abs(c.x() - centre.x()) + Math.abs(c.z() - centre.z())));
        // The lip, after the wall it hangs from: sides first, then the corners, which hang from the sides.
        List<BlockPos> lip = new ArrayList<>();
        int out = radius + 1;
        for (int dx = -out; dx <= out; dx++) {
            for (int dz = -out; dz <= out; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != out) {
                    continue;
                }
                int wx = Math.clamp(dx, -radius, radius);
                int wz = Math.clamp(dz, -radius, radius);
                if (wx == 0 && wz == radius) {
                    continue; // outside the doorway: in the way out, and no wall to hang from
                }
                int x = centre.x() + dx;
                int z = centre.z() + dz;
                ground(world, centre.x() + wx, centre.z() + wz, centre.y())
                        .ifPresent(feet -> lip.add(new BlockPos(x, feet.y() + HEIGHT - 1, z)));
            }
        }
        lip.sort(Comparator.comparingInt((BlockPos c) -> Math.abs(c.x() - centre.x()) == out && Math.abs(c.z() - centre.z()) == out ? 1 : 0));
        cells.addAll(lip);
        return List.copyOf(cells);
    }

    /** The cells of {@code layout} still open: what's left to build, in its order. */
    public static List<BlockPos> open(WorldView world, List<BlockPos> layout) {
        return layout.stream().filter(c -> !world.isSolid(c.x(), c.y(), c.z())).toList();
    }

    /** Where the wall stands in column ({@code x}, {@code z}): the highest standing cell near {@code nearY}, not in water. */
    static java.util.Optional<BlockPos> ground(WorldView world, int x, int z, int nearY) {
        for (int y = nearY + VERTICAL; y >= nearY - VERTICAL; y--) {
            BlockPos cell = new BlockPos(x, y, z);
            if (Pathfinder.standable(world, cell)) {
                return java.util.Optional.of(cell);
            }
        }
        return java.util.Optional.empty();
    }
}
