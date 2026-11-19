package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.command.Phase;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The camp's mine kept on disk, and the worlds the squad simulation cuts it in (SquadSimulationTest). */
final class KeptStairs {

    private KeptStairs() {
    }

    /** {@link #KEPT} kept on disk as the camp's mine under {@code state}, in the buildup: the phase that mines. */
    static void keptInTheBuildup(Path state) {
        new WallStore(state, "stairs").save("0,0", List.of(KEPT.head(), KEPT.step(KEPT.steps() - 1)));
        new PhaseBook(new WallStore(state, "chests")).keep(Phase.BUILDUP, 0);
    }

    /** The camp's mine as kept on disk in {@code SquadSimulationTest.aRestartCutsTheCampsKeptStaircaseOnDown}: from (4, 64, 4) down to y=16. */
    static final Staircase KEPT = new Staircase(
            new BlockPos(4, SquadSimulationTest.GROUND + 1, 4), 16);

    /**
     * Flat, with {@link #KEPT}'s first six steps cut, and a block of dirt dropped on its head - as
     * live, where the ground over an old shaft read a block higher than when it was sited.
     */
    static int keptStairsCut(int x, int y, int z) {
        if (x == 4 && y == SquadSimulationTest.GROUND + 1 && z == 4) {
            return SimServer.DIRT;
        }
        for (int k = 0; k < 6; k++) {
            if (KEPT.cells(k).contains(new BlockPos(x, y, z))) {
                return SimServer.AIR;
            }
        }
        return SquadSimulationTest.flat(x, y, z);
    }

    /** Every cell of {@link #KEPT}'s cuts - stairs, landing, tunnels - as air: a mine cut to the bottom. */
    static final Set<BlockPos> KEPT_CUT = KEPT.cuts().stream()
            .flatMap(c -> c.cells().stream()).collect(Collectors.toUnmodifiableSet());

    static int keptStairsAllCut(int x, int y, int z) {
        return KEPT_CUT.contains(new BlockPos(x, y, z)) ? SimServer.AIR : SquadSimulationTest.flat(x, y, z);
    }

    /** As {@link #keptStairsAllCut}, the camp's ground lit by torches every 3 blocks, and a mound on it to dig. */
    static int keptStairsAllCutInALitCamp(int x, int y, int z) {
        if (y == SquadSimulationTest.GROUND + 1 && Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0 && Math.abs(x) <= 15 && Math.abs(z) <= 15
                && !KEPT_CUT.contains(new BlockPos(x, y, z)) && Math.abs(x - 4) > 2 && Math.abs(z - 4) > 2) {
            return SimServer.TORCH;
        }
        if (x >= -6 && x <= -5 && z >= -6 && z <= -5 && y > SquadSimulationTest.GROUND && y <= SquadSimulationTest.GROUND + 2) {
            return SimServer.DIRT;
        }
        // And its stairs lit, as live.
        if (KEPT.lights().contains(new BlockPos(x, y, z))) {
            return SimServer.TORCH;
        }
        return keptStairsAllCut(x, y, z);
    }

    /** As {@link #keptStairsAllCut}, with the landing's stand's floor gone: ore taken from under it. */
    static int keptStairsCutLandingHoled(int x, int y, int z) {
        BlockPos stand = KEPT.landingStand();
        return x == stand.x() && y == stand.y() - 1 && z == stand.z() ? SimServer.AIR : keptStairsAllCut(x, y, z);
    }
}
