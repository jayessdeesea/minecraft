package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Food from the land: wheat standing ripe anywhere near the camp, harvested and replanted by a soldier with no food
 * of its own - none carried, none in the stockpile, no game in sight - rather than waiting on the camp's (operator,
 * 2026-11-20: when the soldiers are starving they look for food anywhere; wheat stands everywhere within 100 blocks).
 * A forager's plot is a farm like any other to its {@link Farmer}: it harvests what's ripe, plants the seeds the
 * harvest gives in the farmland it leaves, and bakes the wheat it carries. Pure.
 */
public final class Forage {

    /** How far across from the camp ripe wheat is looked for. */
    public static final int REACH = 100;
    /** How far up or down from the camp's ground. */
    public static final int DEPTH = 24;
    /** Ripe wheat a forager is given at a time: a few loaves' worth, a short walk round. */
    public static final int CELLS = 24;

    private Forage() {
    }

    /** The farmland under every ripe wheat within {@code reach} across and {@code depth} up or down of {@code centre}, in loaded columns. */
    public static List<BlockPos> ripe(WorldView world, BlockPos centre, int reach, int depth) {
        List<BlockPos> found = new ArrayList<>();
        for (int x = centre.x() - reach; x <= centre.x() + reach; x++) {
            for (int z = centre.z() - reach; z <= centre.z() + reach; z++) {
                for (int y = centre.y() - depth; world.isLoaded(x, z) && y <= centre.y() + depth; y++) {
                    if (world.wheatAge(x, y, z).orElse(-1) == Digger.RIPE) {
                        found.add(new BlockPos(x, y - 1, z));
                    }
                }
            }
        }
        return found;
    }

    /** A forager at {@code at}'s plot: the {@link #CELLS} of {@code ripe} nearest it, none of them {@code taken} - a squadmate's. */
    public static Optional<FarmPlan> plot(List<BlockPos> ripe, Position at, Set<BlockPos> taken) {
        List<BlockPos> cells = ripe.stream().filter(c -> !taken.contains(c))
                .sorted(Comparator.comparingDouble(c -> Math.hypot(Math.hypot(c.x() + 0.5 - at.x(), c.z() + 0.5 - at.z()), c.y() + 1 - at.y())))
                .limit(CELLS).toList();
        return cells.isEmpty() ? Optional.empty() : Optional.of(new FarmPlan(cells.getFirst(), cells));
    }
}
