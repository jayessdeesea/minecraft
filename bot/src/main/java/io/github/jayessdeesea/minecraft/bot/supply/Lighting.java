package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where torches go so no monster spawns on the ground round a point (docs/military-doctrine.md §14):
 * the squad's SECURE task. In the overworld a monster spawns only where block light is 0; a torch
 * gives 14, falling by one a block, so a cell within 13 blocks of one by an open path is safe. This
 * counts a cell lit when a torch is within {@link #REACH} blocks by taxicab distance - half that, so
 * a wall or a step between the two still leaves it lit.
 *
 * <p>Light is worked out from where torches stand, not read: the server's light data isn't decoded.
 * Only ground the squad can walk to is planned, and every planned torch stands on such ground -
 * ground beyond a wall or across a cliff is the wall's to keep, not the torches'. Farmland is left
 * bare: a torch stands on the soil, not in the crop. So is a canopy: no monster spawns on leaves,
 * and no torch stands on them - live, a soldier tried one on a treetop four hundred times.
 */
public final class Lighting {

    /** Taxicab distance from a torch at which a cell counts as lit. */
    public static final int REACH = 7;
    /**
     * Torches are first tried on the lattice where taxicab circles of radius {@link #REACH} tile the
     * ground without gaps or overlap - cells with {@code 15 dx + dz} a multiple of 113 - so the
     * fewest torches light it.
     */
    private static final int LATTICE = 2 * REACH * REACH + 2 * REACH + 1;
    private static final int LATTICE_STEP = 2 * REACH + 1;
    private static final int VERTICAL = 8;
    private static final int FLOOD_BUDGET = 6_000;

    private Lighting() {
    }

    /** The cells, nearest {@code centre} first, to place a torch in so the ground within {@code radius} of it is lit. */
    public static List<BlockPos> plan(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> ground = Pathfinder.reachable(world, centre, radius, FLOOD_BUDGET).stream()
                .filter(c -> spawnGround(world, c))
                .sorted(Comparator.comparingInt((BlockPos c) -> Math.abs(c.x() - centre.x()) + Math.abs(c.z() - centre.z()))
                        .thenComparingInt(BlockPos::y).thenComparingInt(BlockPos::x).thenComparingInt(BlockPos::z))
                .toList();
        List<BlockPos> lights = new ArrayList<>(torches(world, centre, radius + REACH));
        List<BlockPos> plan = new ArrayList<>();
        // The grid first, so torches stand evenly spaced; then any cell it left dark gets its own.
        for (BlockPos cell : ground) {
            if (Math.floorMod(LATTICE_STEP * (cell.x() - centre.x()) + (cell.z() - centre.z()), LATTICE) == 0
                    && !lit(cell, lights)) {
                lights.add(cell);
                plan.add(cell);
            }
        }
        for (BlockPos cell : ground) {
            if (!lit(cell, lights)) {
                lights.add(cell);
                plan.add(cell);
            }
        }
        return List.copyOf(plan);
    }

    /** How many of the walkable cells within {@code radius} of {@code centre} are still dark. */
    public static int dark(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> lights = torches(world, centre, radius + REACH);
        return (int) Pathfinder.reachable(world, centre, radius, FLOOD_BUDGET).stream()
                .filter(c -> spawnGround(world, c))
                .filter(c -> !lit(c, lights)).count();
    }

    /** Ground a monster could spawn on and a torch stand on: walkable, and neither farmland nor leaves. */
    private static boolean spawnGround(WorldView world, BlockPos c) {
        return Pathfinder.standable(world, c) && !world.isFarmland(c.x(), c.y() - 1, c.z()) && world.leaves(c.x(), c.y() - 1, c.z()).isEmpty();
    }

    static boolean lit(BlockPos cell, List<BlockPos> lights) {
        for (BlockPos light : lights) {
            if (Math.abs(light.x() - cell.x()) + Math.abs(light.y() - cell.y()) + Math.abs(light.z() - cell.z()) <= REACH) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> torches(WorldView world, BlockPos centre, int radius) {
        List<BlockPos> torches = new ArrayList<>();
        for (int x = centre.x() - radius; x <= centre.x() + radius; x++) {
            for (int z = centre.z() - radius; z <= centre.z() + radius; z++) {
                for (int y = centre.y() - VERTICAL; y <= centre.y() + VERTICAL; y++) {
                    if (world.isTorch(x, y, z)) {
                        torches.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return torches;
    }
}

