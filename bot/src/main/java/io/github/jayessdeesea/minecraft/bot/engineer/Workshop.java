package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Where a squad crafts and smelts (backlog/sites.md): a cell to stand in, with a crafting table and
 * a furnace beside it, each in a cell the engineer names. A soldier crafts only at a workshop, and
 * never chooses where its table goes: a recipe it can't make in its own grid sends it to the
 * workshop, and a fixture missing there is placed into its cell by a {@code Place} order.
 *
 * <p>Two kinds: the mine base's, at the bottom of the camp's stairs, with its table and furnace in
 * the landing's recesses; and a hasty one, sited beside a squad's objective on open ground, where a
 * table already standing is taken as its own - so the same workshop is found again after a restart.
 *
 * <p>A workshop on the surface also has a chest: behind its table or its furnace, within reach of the
 * stand. It's the site's store - what a full pack leaves, and what one squad leaves for another.
 *
 * @param stand   where the soldier stands to work
 * @param table   the crafting table's cell
 * @param furnace the furnace's cell
 * @param chest   the chest's cell, where there's room for one
 */
public record Workshop(BlockPos stand, BlockPos table, BlockPos furnace, Optional<BlockPos> chest) {

    /** A workshop with no store. */
    public Workshop(BlockPos stand, BlockPos table, BlockPos furnace) {
        this(stand, table, furnace, Optional.empty());
    }

    /** How far round the ground asked a hasty workshop is looked for. */
    static final int SEARCH = 6;
    private static final int[][] SIDES = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    /** The mine base's workshop: the landing's stand, its table in one recess and its furnace in the other. */
    public static Workshop mine(Staircase stairs) {
        List<BlockPos> recesses = stairs.landing();
        return new Workshop(stairs.landingStand(), recesses.get(0), recesses.get(1));
    }

    /**
     * A hasty workshop near {@code ground}: round a table already standing within {@link #SEARCH},
     * nearest first, else at the standable cell nearest {@code ground} with two free cells side by
     * side round it - adjacent sides, so the stand stays open on the other two. Cells in
     * {@code claimed} - stairs, walls, others' digs - aren't used. Empty with nowhere to put one.
     */
    public static Optional<Workshop> hasty(WorldView world, BlockPos ground, Set<BlockPos> claimed) {
        Comparator<BlockPos> nearest = Comparator.comparingInt(c -> Math.abs(c.x() - ground.x()) + Math.abs(c.z() - ground.z())
                + 2 * Math.abs(c.y() - ground.y()));
        List<BlockPos> around = new ArrayList<>();
        for (int dx = -SEARCH; dx <= SEARCH; dx++) {
            for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    around.add(ground.offset(dx, dy, dz));
                }
            }
        }
        around.sort(nearest);
        for (BlockPos table : around) {
            if (world.workstation(table.x(), table.y(), table.z()).equals(Optional.of(Workstation.CRAFTING_TABLE))) {
                for (int[] side : SIDES) {
                    BlockPos stand = table.offset(-side[0], 0, -side[1]);
                    if (Pathfinder.standable(world, stand) && !claimed.contains(stand)) {
                        Optional<BlockPos> furnace = furnaceBeside(world, stand, table, claimed);
                        if (furnace.isPresent()) {
                            return Optional.of(new Workshop(stand, table, furnace.get(), chestBehind(world, stand, table, furnace.get(), claimed)));
                        }
                    }
                }
            }
        }
        for (BlockPos stand : around) {
            if (!Pathfinder.standable(world, stand) || claimed.contains(stand)) {
                continue;
            }
            for (int i = 0; i < SIDES.length; i++) {
                BlockPos table = stand.offset(SIDES[i][0], 0, SIDES[i][1]);
                int[] next = SIDES[(i + 1) % SIDES.length];
                BlockPos furnace = stand.offset(next[0], 0, next[1]);
                if (free(world, table, claimed) && free(world, furnace, claimed)) {
                    return Optional.of(new Workshop(stand, table, furnace, chestBehind(world, stand, table, furnace, claimed)));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The chest's cell: behind the table from the stand, else behind the furnace - two blocks out,
     * in reach - free, or holding the chest put there before.
     */
    private static Optional<BlockPos> chestBehind(WorldView world, BlockPos stand, BlockPos table, BlockPos furnace, Set<BlockPos> claimed) {
        for (BlockPos fixture : List.of(table, furnace)) {
            BlockPos behind = fixture.offset(fixture.x() - stand.x(), 0, fixture.z() - stand.z());
            boolean taken = world.isChest(behind.x(), behind.y(), behind.z());
            if (!claimed.contains(behind) && (free(world, behind, claimed) || taken)) {
                return Optional.of(behind);
            }
        }
        return Optional.empty();
    }

    /** Whether the workshop's chest stands in its cell. */
    public boolean chestStands(WorldView world) {
        // A chest, not any block: live, a block that stood in the chest's cell was taken for one, and
        // the squad's loads went to it two thousand times over a night.
        return chest.filter(c -> world.isChest(c.x(), c.y(), c.z())).isPresent();
    }

    /** A furnace already beside {@code stand}, else a free cell beside it next to the table's side. */
    private static Optional<BlockPos> furnaceBeside(WorldView world, BlockPos stand, BlockPos table, Set<BlockPos> claimed) {
        for (int[] side : SIDES) {
            BlockPos cell = stand.offset(side[0], 0, side[1]);
            if (!cell.equals(table) && world.workstation(cell.x(), cell.y(), cell.z()).equals(Optional.of(Workstation.FURNACE))) {
                return Optional.of(cell);
            }
        }
        for (int[] side : SIDES) {
            BlockPos cell = stand.offset(side[0], 0, side[1]);
            boolean oppositeTable = cell.x() - stand.x() == stand.x() - table.x() && cell.z() - stand.z() == stand.z() - table.z();
            if (!cell.equals(table) && !oppositeTable && free(world, cell, claimed)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    /** A cell a fixture can go into: open, with solid ground under it and nothing claimed. */
    private static boolean free(WorldView world, BlockPos cell, Set<BlockPos> claimed) {
        return !claimed.contains(cell) && !world.isSolid(cell.x(), cell.y(), cell.z()) && !world.isHazard(cell.x(), cell.y(), cell.z())
                && world.isSolid(cell.x(), cell.y() - 1, cell.z()) && !world.isHazard(cell.x(), cell.y() - 1, cell.z());
    }

    /** Whether {@code station}'s fixture stands in its cell. */
    public boolean stands(WorldView world, Workstation station) {
        BlockPos cell = cell(station);
        return world.workstation(cell.x(), cell.y(), cell.z()).equals(Optional.of(station));
    }

    /** The cell {@code station}'s fixture goes in. */
    public BlockPos cell(Workstation station) {
        return station == Workstation.CRAFTING_TABLE ? table : furnace;
    }
}
