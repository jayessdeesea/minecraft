package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaircaseTest {

    private final Staircase stairs = new Staircase(new BlockPos(0, 64, 0), 40);

    /** Ground to y=63, with {@code open} cleared out of it. */
    private static WorldView ground(Set<BlockPos> open) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < 64 && !open.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void aCaveAtTheLandingIsNoLandingTillTheStepsReachIt() {
        Set<BlockPos> open = new HashSet<>();
        for (int k = 0; k < 8; k++) {
            open.addAll(stairs.cells(k)); // a third of the way down
        }
        for (BlockPos recess : stairs.landing()) {
            open.add(recess);
            open.add(recess.offset(0, 1, 0)); // a cave at the landing's depth
        }

        assertFalse(stairs.landingCut(ground(open)), "the steps stop 16 blocks above it");

        for (int k = 0; k < stairs.steps(); k++) {
            open.addAll(stairs.cells(k));
        }
        assertTrue(stairs.landingCut(ground(open)), "cut all the way down");
    }

    /**
     * A mine worked out is run on a stretch: every cut it had is still in it, in the same tunnel - so
     * a tunnel given up stays given up - and the main tunnel and its branches reach a stretch further.
     * Kept on disk by its main tunnel's end, it comes back as long as it was run.
     */
    @Test
    void aMineRunOnKeepsItsCutsAndComesBackFromDiskAsLong() {
        Staircase one = new Staircase(new BlockPos(0, 64, 0), 16);
        Staircase two = one.extended().orElseThrow();

        assertTrue(two.cuts().containsAll(one.cuts()), "every cut of the first stretch, in its own tunnel");
        assertTrue(two.cuts().size() > one.cuts().size());
        assertTrue(two.cuts().stream().anyMatch(c -> c.feet().equals(two.tunnelEnd())), "the tunnel reaches its end");
        assertTrue(Staircase.kept(two.head(), two.step(two.steps() - 1), java.util.Optional.of(two.tunnelEnd())).equals(two));
        assertTrue(Staircase.kept(one.head(), one.step(one.steps() - 1), java.util.Optional.empty()).equals(one));
        Staircase last = two;
        while (last.extended().isPresent()) {
            last = last.extended().get();
        }
        assertTrue(last.stretches() == Staircase.MAX_STRETCHES);
    }

    /**
     * A mine run on as far as it goes is cut a level deeper off the same stairs: the steps above are
     * the same, a fresh main tunnel under them, down to just above bedrock and no further.
     */
    @Test
    void aMineWorkedOutGoesALevelDeeperOffTheSameStairs() {
        Staircase upper = new Staircase(new BlockPos(0, 64, 0), 16, Staircase.MAX_STRETCHES, false);
        Staircase lower = upper.deeper().orElseThrow();

        assertTrue(lower.bottom() == 16 - Staircase.LEVEL_DROP && lower.stretches() == 1, lower.toString());
        for (int k = 0; k < upper.steps(); k++) {
            assertTrue(lower.step(k).equals(upper.step(k)), "step " + k + " is the same step");
        }
        assertTrue(Staircase.kept(lower.head(), lower.step(lower.steps() - 1), java.util.Optional.empty()).equals(lower), "kept as cut");
        Staircase last = lower;
        while (last.deeper().isPresent()) {
            last = last.deeper().get();
        }
        assertTrue(last.bottom() >= Staircase.DEEPEST && last.bottom() - Staircase.LEVEL_DROP < Staircase.DEEPEST, last.toString());
    }

    /**
     * Shared among miners, every tunnel is one miner's and no more than one's, and each miner's
     * branches are a stretch of the main tunnel of their own: no miner's root between another's.
     */
    @Test
    void aMineSharedAmongMinersGivesEachItsOwnStretch() {
        Staircase stairs = new Staircase(new BlockPos(0, 64, 0), 16).extended().orElseThrow();
        Set<Integer> all = new HashSet<>();
        stairs.cuts().forEach(c -> all.add(c.tunnel()));
        for (int miners = 1; miners <= 6; miners++) {
            Set<Integer> seen = new HashSet<>();
            int lastRoot = -1;
            for (int miner = 0; miner < miners; miner++) {
                Set<Integer> share = stairs.share(miner, miners);
                for (int tunnel : share) {
                    assertTrue(seen.add(tunnel), "tunnel " + tunnel + " is one miner's, of " + miners);
                }
                List<Integer> roots = share.stream().filter(t -> t > 0).map(t -> (t - 1) / 2).sorted().toList();
                if (!roots.isEmpty()) {
                    assertTrue(roots.getFirst() > lastRoot, "miner " + miner + "'s stretch lies past the last one's");
                    lastRoot = roots.getLast();
                }
            }
            assertTrue(seen.equals(all), "every tunnel someone's, with " + miners + " miners");
        }
        assertTrue(stairs.share(1, 4).stream().noneMatch(t -> t <= 0), "the stairs and main tunnel are the first miner's");
    }

    /** The store's recesses are cut with the main tunnel, clear of the landing's and of every branch. */
    @Test
    void theStoreIsCutBesideTheSecondCellClearOfTheLandingAndTheBranches() {
        Staircase stairs = new Staircase(new BlockPos(0, 64, 0), 16);
        Set<BlockPos> mainCells = new HashSet<>();
        Set<BlockPos> branchCells = new HashSet<>();
        stairs.cuts().forEach(c -> (c.tunnel() == 0 ? mainCells : c.tunnel() > 0 ? branchCells : new HashSet<BlockPos>()).addAll(c.cells()));
        for (BlockPos recess : stairs.store()) {
            assertTrue(mainCells.contains(recess), recess + " is cut with the main tunnel");
            assertFalse(branchCells.contains(recess), recess + " is no branch's");
            assertFalse(stairs.landing().contains(recess), recess + " is not the landing's");
        }
    }

    /**
     * The well standard (operator, 2026-11-15): every step in chunk-local 6..9, each beside the last,
     * the north-east corner (9, 6) at every y = 12k, the pillar 7..8 never stepped on, the landing at
     * a multiple of 12 on the corner - in a chunk on either side of zero, where chunk-local is floorMod.
     */
    @Test
    void aWellIsFourByFourCentredInItsChunkWithItsCornerOnTwelves() {
        for (int[] chunk : new int[][]{{0, 0}, {-1, -2}, {3, -1}}) {
            Staircase well = Staircase.well(chunk[0], chunk[1], 70, 16);
            assertTrue(well.bottom() == 12, "the landing at the deepest y = 12k no deeper than asked: " + well.bottom());
            BlockPos last = null;
            for (int k = -1; k < well.steps(); k++) {
                BlockPos step = k < 0 ? well.head() : well.step(k);
                int lx = Math.floorMod(step.x(), 16), lz = Math.floorMod(step.z(), 16);
                assertTrue(lx >= 6 && lx <= 9 && lz >= 6 && lz <= 9, step + " in the 4x4");
                assertFalse(lx >= 7 && lx <= 8 && lz >= 7 && lz <= 8, step + " off the pillar");
                if (Math.floorMod(step.y(), 12) == 0) {
                    assertTrue(lx == 9 && lz == 6, "at y=" + step.y() + " the north-east corner, not " + lx + "," + lz);
                }
                if (last != null) {
                    assertTrue(Math.abs(step.x() - last.x()) + Math.abs(step.z() - last.z()) == 1 && step.y() == last.y() - 1,
                            step + " one down and beside " + last);
                }
                last = step;
            }
            BlockPos landing = well.step(well.steps() - 1);
            assertTrue(Math.floorMod(landing.x(), 16) == 9 && Math.floorMod(landing.z(), 16) == 6 && landing.y() == 12, "landing " + landing);
            assertFalse(well.inShaft(well.landingStand().x(), well.landingStand().z()), "the main tunnel out of the well");
            assertTrue(well.cuts().stream().filter(c -> c.tunnel() >= 0).allMatch(c -> !well.inShaft(c.feet().x(), c.feet().z())),
                    "no tunnel back through the well");
        }
    }

    @Test
    void everyTorchStandsWhereABodyWalks() {
        Set<BlockPos> walkway = stairs.walkway();
        for (BlockPos light : stairs.lights()) {
            assertTrue(walkway.contains(light) || stairs.landing().contains(light), light + " is off the walkway");
        }
    }

    @Test
    void everyTunnelCellIsWithinATorchsLight() {
        List<BlockPos> lights = stairs.lights();
        stairs.cuts().stream().filter(c -> c.tunnel() >= 0).map(Staircase.Cut::feet).forEach(cell ->
                assertTrue(lights.stream().anyMatch(l -> Math.abs(l.x() - cell.x()) + Math.abs(l.y() - cell.y()) + Math.abs(l.z() - cell.z())
                        <= Lighting.REACH), cell + " is dark"));
    }
}
