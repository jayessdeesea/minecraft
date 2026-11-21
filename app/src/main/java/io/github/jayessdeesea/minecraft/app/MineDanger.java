package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.command.DangerAreas;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongBiFunction;

/**
 * The tunnels of a mine that run into a danger area (command.DangerAreas): a cut of theirs within the danger radius of
 * {@link DangerAreas#DEATHS} or more of the force's deaths in the window, given up as a tunnel closed by water is. The
 * mine's head is checked as well (SoldierRunner); its galleries run to 200 blocks from it - live (field598), Charlie lost
 * Jack twice, Kate and Ivy at (15, -10, 121) in a cave of skeletons 112 blocks down a gallery of the keep's mine at
 * (6, 16, 8), and the mine's head read safe.
 */
final class MineDanger {

    private MineDanger() {
    }

    /** Each tunnel of {@code cuts} that comes within the radius of a danger area, by {@code near} - with where it does. */
    static Map<Integer, BlockPos> tunnels(List<Staircase.Cut> cuts, ToLongBiFunction<Double, Double> near) {
        Map<Integer, BlockPos> into = new TreeMap<>();
        for (Staircase.Cut cut : cuts) {
            if (cut.tunnel() < 0 || into.containsKey(cut.tunnel())) {
                continue; // the stairs are the way down, for the head's own check
            }
            BlockPos at = cut.feet();
            if (near.applyAsLong(at.x() + 0.5, at.z() + 0.5) >= DangerAreas.DEATHS) {
                into.put(cut.tunnel(), at);
            }
        }
        return into;
    }
}
