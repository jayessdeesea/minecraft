package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /** A well already worked out or blocked is never sited again: the next chunk's is. */
    @Test
    void aDeadWellIsPassedOverForTheNextChunks() {
        WorldView flat = ground(Set.of());
        Staircase first = Staircase.siteWell(flat, 8, 8, 64, 16).orElseThrow();
        Staircase next = Staircase.siteWell(flat, 8, 8, 64, 16, first.head()::equals).orElseThrow();
        assertFalse(next.head().equals(first.head()), "not the dead well at " + first.head());
    }

    /** A hollow under a step doesn't send the well to another chunk: it's cut where it's sited, the step floored by its miner. */
    @Test
    void aHollowUnderAStepDoesNotRuleAWellOut() {
        Staircase first = Staircase.siteWell(ground(Set.of()), 8, 8, 64, 16).orElseThrow();
        BlockPos underTheSecondStep = first.cuts().get(1).feet().offset(0, -1, 0);
        WorldView hollow = ground(Set.of(underTheSecondStep, underTheSecondStep.offset(0, -1, 0)));
        assertTrue(first.blocked(hollow).orElse("").startsWith("no floor"), "the hollow is seen: " + first.blocked(hollow));
        assertEquals(first.head(), Staircase.siteWell(hollow, 8, 8, 64, 16).orElseThrow().head(), "and the well is this chunk's still");
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
     * The grid (the rules of a mine): every tunnel three high, and the strips off the spine where the
     * world's coordinate along it is a multiple of 3 - wherever the mine's shaft is - so every block
     * between two strips is seen from one, and none is dug twice.
     */
    @Test
    void aLevelIsAGridOfStripsThreeHighOnTheWorldsThirds() {
        for (int x0 = 0; x0 < 3; x0++) {
            Staircase stairs = new Staircase(new BlockPos(x0, 64, 0), 16);
            for (Staircase.Cut cut : stairs.cuts()) {
                if (cut.tunnel() >= 0 && cut.cells().size() != 2) {
                    assertTrue(cut.cells().size() == Staircase.TUNNEL_HEIGHT, "three high: " + cut);
                }
            }
            List<Staircase.Cut> main = stairs.cuts().stream().filter(c -> c.tunnel() == 0 && c.cells().size() == 3).toList();
            boolean alongX = main.get(1).feet().x() != main.get(0).feet().x();
            Set<Integer> rootsAt = new HashSet<>();
            stairs.cuts().stream().filter(c -> c.tunnel() > 0).forEach(c -> rootsAt.add(alongX ? c.stand().x() : c.stand().z()));
            List<Staircase.Cut> firsts = stairs.cuts().stream().filter(c -> c.tunnel() > 0)
                    .filter(c -> main.stream().anyMatch(m -> m.feet().equals(c.stand()))).toList();
            assertFalse(firsts.isEmpty());
            for (Staircase.Cut first : firsts) {
                int along = alongX ? first.stand().x() : first.stand().z();
                assertTrue(Math.floorMod(along, Staircase.BRANCH_EVERY) == 0, "a strip at a world third, head x=" + x0 + ": " + first.stand());
            }
        }
    }

    /**
     * A well's levels at y % 4 == 0 (the rules of a mine): each its own tunnels, out through the wall
     * its walkway's on at that height - east, south and west in turn - so no two levels one above
     * another run the same way, and none goes back through the shaft.
     */
    @Test
    void aWellsLevelsEveryFourRunOffDifferentWays() {
        Staircase well = Staircase.well(0, 0, 70, 12);
        Set<BlockPos> stands = new HashSet<>();
        for (int level : new int[]{12, 8, 4}) {
            Staircase at = well.atLevel(level).orElseThrow();
            BlockPos landing = at.landingStand();
            assertTrue(!at.inShaft(landing.x(), landing.z()), "the landing's out of the shaft at y=" + level + ": " + landing);
            BlockPos last = at.step(at.steps() - 1);
            stands.add(new BlockPos(landing.x() - last.x(), 0, landing.z() - last.z()));
        }
        assertTrue(stands.size() == 3, "three ways: " + stands);
        assertFalse(well.atLevel(10).isPresent(), "not off the y % 4 == 0 heights");
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
                if (miners == 2) {
                    // Two to a level, one to each side of the spine (the rules of a mine).
                    int side = miner;
                    assertTrue(share.stream().filter(t -> t > 0).allMatch(t -> t % 2 == (side == 0 ? 1 : 0)), "miner " + miner + " one side's");
                } else if (!roots.isEmpty()) {
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

    /**
     * A chest row by a mine's head leaves its way down: live (field585) the stockpile's row put a chest in Bravo's stair
     * head at (-10, 64, 7), and half the platoon stood beside it, "no route" down.
     */
    @Test
    void aChestRowLeavesAMinesHead() {
        Staircase mine = new Staircase(new BlockPos(-10, 64, 7), 0);
        io.github.jayessdeesea.minecraft.bot.engineer.Workshop shop = new io.github.jayessdeesea.minecraft.bot.engineer.Workshop(
                new BlockPos(-6, 64, 7), new BlockPos(-6, 64, 9), new BlockPos(-6, 64, 5), java.util.Optional.empty());
        io.github.jayessdeesea.minecraft.bot.world.WorldView flat = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < 64;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public java.util.List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(
                    io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return java.util.List.of();
            }
        };
        java.util.List<BlockPos> row = shop.chestCells(flat, mine.headCells(), 17);
        assertTrue(row.size() > 4 && row.stream().noneMatch(mine.headCells()::contains), "none in the shaft: " + row);
        assertTrue(shop.chestCells(flat, java.util.Set.of(), 17).stream().anyMatch(mine.headCells()::contains), "without it, one was");
    }

    /**
     * A chest in a step of the stairs - out of a recess - is in the way and is cut, its contents spilt: live (field585) a
     * chest in Bravo's stair head at (-10, 64, 7), left standing, shut the mine to the half of the platoon sent down it.
     */
    @Test
    void aChestInAStepIsCut() {
        Staircase mine = new Staircase(new BlockPos(-10, 64, 7), 0);
        Staircase.Cut first = mine.cuts().getFirst();
        BlockPos chest = first.cells().getLast();
        io.github.jayessdeesea.minecraft.bot.world.WorldView chested = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(chest);
            }

            @Override
            public boolean isChest(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(chest);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public java.util.List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(
                    io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                return java.util.List.of();
            }
        };
        assertTrue(first.toCut(chested, chest), "the chest in the step is to cut");
        assertTrue(Miner.firstUncut(chested, mine).equals(java.util.OptionalInt.of(0)), "and the first step is uncut again");
    }
}
