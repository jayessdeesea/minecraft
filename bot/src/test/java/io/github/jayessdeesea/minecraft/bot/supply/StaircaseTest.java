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
