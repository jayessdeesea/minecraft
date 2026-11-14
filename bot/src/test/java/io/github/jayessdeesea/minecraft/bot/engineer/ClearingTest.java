package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClearingTest {

    /** Flat ground, feet at y=64, with these logs and leaves on it. */
    private static WorldView with(Set<BlockPos> logs, Set<BlockPos> leaves) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos at = new BlockPos(x, y, z);
                return y <= 63 || logs.contains(at) || leaves.contains(at);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public Optional<String> leaves(int x, int y, int z) {
                return leaves.contains(new BlockPos(x, y, z)) ? Optional.of("oak") : Optional.empty();
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void oneUpInACanopyCutsWhatItReachesFromWhereItStands() {
        // A canopy floor at y=70, 6 over the ground, and the soldier on it: no stand on the ground
        // reaches the leaves beside it.
        Set<BlockPos> leaves = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                leaves.add(new BlockPos(x, 70, z));
            }
        }
        BlockPos beside = new BlockPos(1, 71, 0);
        leaves.add(beside);

        Clearing.Cut cut = Clearing.next(with(Set.of(), leaves), List.of(beside), new Position(0.5, 71, 0.5), Set.of()).orElseThrow();

        assertEquals(new BlockPos(0, 71, 0), cut.stand(), "cut from where it stands");
        assertEquals(Optional.empty(), Clearing.next(with(Set.of(), leaves), List.of(new BlockPos(0, 70, 0)), new Position(0.5, 71, 0.5), Set.of())
                .filter(c -> c.stand().equals(new BlockPos(0, 71, 0))), "never the block it stands on");
    }

    /** The dig over a camp with feet at y=64, 8 out: its ground's height up to its sky. */
    private static final Box SKY = new Box(-8, 64, -8, 8, 88, 8);
    /** The fill under it, 7 out. */
    private static final Box GROUND = new Box(-7, 48, -7, 7, 63, 7);

    /** A trunk {@code height} logs tall at (x, z), and a layer of leaves round its top. */
    private static Set<BlockPos> trunk(int x, int z, int height) {
        Set<BlockPos> logs = new HashSet<>();
        for (int y = 64; y < 64 + height; y++) {
            logs.add(new BlockPos(x, y, z));
        }
        return logs;
    }

    @Test
    void aTreeInTheCampIsCoverLogsAndLeaves() {
        Set<BlockPos> leaves = Set.of(new BlockPos(4, 69, 3), new BlockPos(2, 69, 2));
        List<BlockPos> cover = Clearing.toClear(with(trunk(3, 3, 5), leaves), SKY, Set.of());

        assertEquals(7, cover.size());
        assertEquals(new BlockPos(3, 64, 3), cover.getFirst(), "lowest first");
    }

    @Test
    void theTreeIsFelledFromTheBottomStandingOnTheGround() {
        Set<BlockPos> logs = trunk(3, 3, 5);
        WorldView world = with(logs, Set.of());
        List<BlockPos> cover = Clearing.toClear(world, SKY, Set.of());

        Clearing.Cut cut = Clearing.next(world, cover, new Position(0.5, 64, 0.5), Set.of()).orElseThrow();

        assertEquals(new BlockPos(3, 64, 3), cut.block());
        assertTrue(cut.log());
        assertEquals(64, cut.stand().y(), "on the ground beside it");
    }

    /**
     * Only from a stand the soldier can get to: live, the camp's last high block was handed out from
     * a stand walled off on a diagonal, and the upkeep stood at "1 left" for hours.
     */
    @Test
    void aCutIsOnlyHandedOutFromAStandTheSoldierCanReach() {
        Set<BlockPos> logs = trunk(3, 3, 5);
        WorldView world = with(logs, Set.of());
        List<BlockPos> cover = Clearing.toClear(world, SKY, Set.of());

        assertEquals(Optional.empty(), Clearing.next(world, cover, new Position(0.5, 64, 0.5), Set.of(), stand -> false), "none it can reach");
        Clearing.Cut cut = Clearing.next(world, cover, new Position(0.5, 64, 0.5), Set.of(), stand -> stand.x() > 3).orElseThrow();
        assertTrue(cut.stand().x() > 3, "from the side it can get to: " + cut.stand());
    }

    @Test
    void aSoldierReachesSixBlocksUpFromTheGroundAndNoHigher() {
        BlockPos high = new BlockPos(3, 70, 3);
        BlockPos tooHigh = new BlockPos(3, 71, 3);

        assertTrue(Clearing.next(with(Set.of(high), Set.of()), List.of(high), new Position(0.5, 64, 0.5), Set.of()).isPresent());
        assertEquals(Optional.empty(), Clearing.next(with(Set.of(tooHigh), Set.of()), List.of(tooHigh), new Position(0.5, 64, 0.5), Set.of()));
    }

    @Test
    void neverStandsOnTheTreeItCuts() {
        // A stump one log high: the only cell over it is on the log itself.
        BlockPos stump = new BlockPos(3, 64, 3);
        WorldView world = with(Set.of(stump), Set.of());

        Clearing.Cut cut = Clearing.next(world, List.of(stump), new Position(3.5, 65, 3.5), Set.of()).orElseThrow();

        assertTrue(!cut.stand().equals(stump.offset(0, 1, 0)), "not on top of the stump: " + cut.stand());
    }

    /** Flat dirt ground, feet at y=64, with a mound of dirt two high at (2, 2) and one high at (-2, -2). */
    private static final WorldView MOUND = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63 || x == 2 && z == 2 && y <= 65 || x == -2 && z == -2 && y == 64;
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return isSolid(x, y, z) && y >= 60;
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

    @Test
    void highGroundIsDugFromTheTopDown() {
        assertEquals(List.of(new BlockPos(2, 65, 2), new BlockPos(-2, 64, -2), new BlockPos(2, 64, 2)),
                Clearing.toClear(MOUND, SKY, Set.of()), "every block of it above the camp's ground, the highest first");
    }

    /**
     * A table or a chest left on the site is dug like any block, and the ground under it with it:
     * left, it stands on a pillar no dig can take (live, eight such columns held the keep). The
     * squad's own are the caller's {@code except}.
     */
    @Test
    void aTableLeftOnTheSiteIsDugAndTheGroundUnderIt() {
        BlockPos table = new BlockPos(2, 66, 2);
        WorldView withTable = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return MOUND.isSolid(x, y, z) || table.equals(new BlockPos(x, y, z));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return MOUND.isSoil(x, y, z);
            }

            @Override
            public Optional<io.github.jayessdeesea.minecraft.bot.world.Workstation> workstation(int x, int y, int z) {
                return table.equals(new BlockPos(x, y, z)) ? Optional.of(io.github.jayessdeesea.minecraft.bot.world.Workstation.CRAFTING_TABLE)
                        : Optional.empty();
            }

            @Override
            public Optional<io.github.jayessdeesea.minecraft.bot.world.Hardness> hardness(int x, int y, int z) {
                return table.equals(new BlockPos(x, y, z))
                        ? Optional.of(new io.github.jayessdeesea.minecraft.bot.world.Hardness(2.5, io.github.jayessdeesea.minecraft.bot.world.Hardness.HAND))
                        : Optional.empty();
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

        assertEquals(List.of(table, new BlockPos(2, 65, 2), new BlockPos(-2, 64, -2), new BlockPos(2, 64, 2)),
                Clearing.toClear(withTable, SKY, Set.of()), "the table first, then the mound it stood on");
        assertEquals(List.of(new BlockPos(-2, 64, -2)), Clearing.toClear(withTable, SKY, Set.of(new Cell(2, 66, 2))),
                "the squad's own table, excepted: kept, and its ground with it");
    }

    /**
     * One level at a time, the highest first (operator, 2026-11-13): the mound's top block is the
     * next cut though the other mound's block is at the soldier's feet - a lower level isn't on
     * offer while the top one has blocks left.
     */
    @Test
    void theTopLevelIsFinishedBeforeALowerBlockIsOffered() {
        List<BlockPos> cover = Clearing.toClear(MOUND, SKY, Set.of());
        Clearing.Cut cut = Clearing.next(MOUND, cover, new Position(-3.5, 64, -2.5), Set.of()).orElseThrow();

        assertEquals(new BlockPos(2, 65, 2), cut.block(), "the top level, not the block beside it");
    }

    /**
     * A level is swept in rows from its north edge, each row the other way from the last - not the
     * nearest block next (operator, 2026-11-13: no random walks). From the south-east corner the
     * first cut is the north-west block, and after it the one beside it east.
     */
    @Test
    void aLevelIsSweptInRowsNotToTheNearestBlock() {
        Set<BlockPos> slab = new HashSet<>();
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                slab.add(new BlockPos(x, 64, z));
            }
        }
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 63 || slab.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return isSolid(x, y, z) && y >= 60;
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
        List<BlockPos> cover = Clearing.toClear(world, new Box(-2, 64, -2, 4, 70, 4), Set.of());
        Position southEast = new Position(3.5, 64, 2.5);

        Clearing.Cut first = Clearing.next(world, cover, southEast, Set.of()).orElseThrow();
        assertEquals(new BlockPos(0, 64, 0), first.block(), "the sweep's first, not the nearest");
        Clearing.Cut second = Clearing.next(world, cover, southEast, Set.of(first.block())).orElseThrow();
        assertEquals(new BlockPos(1, 64, 0), second.block(), "along the row");
        assertEquals(0, Clearing.sweep(new BlockPos(0, 64, 0), 0, 2, 0));
        assertEquals(3, Clearing.sweep(new BlockPos(2, 64, 1), 0, 2, 0), "the second row walked back from the east");
    }

    @Test
    void aHutIsNotHighGround() {
        assertEquals(List.of(new BlockPos(2, 65, 2), new BlockPos(2, 64, 2)),
                Clearing.toClear(MOUND, SKY, Set.of(new Cell(-2, 64, -2))));
    }

    @Test
    void theBlockUnderOneASquadmateIsDiggingWaits() {
        // The mound's top is a squadmate's: its foot can't be dug from an open top until that's gone.
        assertEquals(Optional.empty(), Clearing.next(MOUND, List.of(new BlockPos(2, 64, 2), new BlockPos(2, 65, 2)),
                new Position(3.5, 64, 2.5), Set.of(new BlockPos(2, 65, 2))));
    }

    @Test
    void groundUnderABlockTheDigLeavesIsLeftToo() {
        // A box one high at the ground's level: the mound's top is over it, not in it.
        assertEquals(List.of(new BlockPos(-2, 64, -2)), Clearing.toClear(MOUND, new Box(-8, 64, -8, 8, 64, 8), Set.of()));
    }

    @Test
    void groundUnderAKeptCellIsLeft() {
        assertEquals(List.of(new BlockPos(-2, 64, -2)), Clearing.toClear(MOUND, SKY, Set.of(new Cell(2, 65, 2))),
                "the mound's top kept: the block under it stays, the other mound is dug");
    }

    @Test
    void groundWithWaterBesideItIsLeft() {
        WorldView pond = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return MOUND.isSolid(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return MOUND.isSoil(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return x == -3 && z == -2 && y == 64; // water lapping the low mound
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };

        assertEquals(List.of(new BlockPos(2, 65, 2), new BlockPos(2, 64, 2)), Clearing.toClear(pond, SKY, Set.of()),
                "dug, it would let the water into the camp");
    }

    @Test
    void aHumpIsDugFromBesideItNotFromOnIt() {
        BlockPos hump = new BlockPos(-2, 64, -2);
        Clearing.Cut cut = Clearing.next(MOUND, List.of(hump), new Position(-1.5, 65, -1.5), Set.of()).orElseThrow();

        assertTrue(!cut.stand().equals(hump.offset(0, 1, 0)), "not on top of it: " + cut.stand());
    }

    /** Flat ground, feet at y=64, with a pit 3 deep in column (1, 1). */
    private static final WorldView PIT = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63 && !(x == 1 && z == 1 && y >= 61);
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

    @Test
    void aPitInTheCampIsFilledFromItsBottomUp() {
        assertEquals(List.of(new BlockPos(1, 61, 1), new BlockPos(1, 62, 1), new BlockPos(1, 63, 1)),
                Clearing.toFill(PIT, GROUND, Set.of()));
    }

    @Test
    void aFloodedPitIsFilledFromTheWatersSurfaceUp() {
        WorldView flooded = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return PIT.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return x == 1 && z == 1 && y == 61; // water at the pit's bottom
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };

        assertEquals(List.of(new BlockPos(1, 62, 1), new BlockPos(1, 63, 1)), Clearing.toFill(flooded, GROUND, Set.of()),
                "capped over the water, up to the camp's ground");
    }

    @Test
    void theMinesShaftIsNotAHollow() {
        assertEquals(List.of(), Clearing.toFill(PIT, GROUND, Set.of(new Cell(1, 61, 1))));
    }

    @Test
    void anExcludedBlockIsLeftForAnother() {
        Set<BlockPos> logs = trunk(3, 3, 2);
        WorldView world = with(logs, Set.of());

        Clearing.Cut cut = Clearing.next(world, Clearing.toClear(world, SKY, Set.of()), new Position(0.5, 64, 0.5),
                Set.of(new BlockPos(3, 64, 3))).orElseThrow();

        assertEquals(new BlockPos(3, 65, 3), cut.block());
    }

    /** Stone in {@code rock}, gravel in {@code gravel} (which falls), nothing else. */
    private static WorldView stone(Set<BlockPos> rock, Set<BlockPos> gravel) {
        WorldView base = cells(rock, gravel);
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return base.isSolid(x, y, z);
            }

            @Override
            public boolean isStone(int x, int y, int z) {
                return rock.contains(new BlockPos(x, y, z)) && !gravel.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isGravel(int x, int y, int z) {
                return base.isGravel(x, y, z);
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

    /** Solid in {@code solid}, gravel in {@code gravel} (which falls), nothing else. */
    private static WorldView cells(Set<BlockPos> solid, Set<BlockPos> gravel) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos at = new BlockPos(x, y, z);
                return solid.contains(at) || gravel.contains(at);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isGravel(int x, int y, int z) {
                return gravel.contains(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    /**
     * A stair cut up out of a pocket is a tunnel: its cells lie under rock, none with an open top, so
     * the level-by-level clear offers none of them - and the tunnel clear offers the nearest, the head
     * room over the digger's own head included, from where it stands.
     */
    @Test
    void aTunnelIsCutCellByCellFromWhereTheDiggerStandsNotFromAnOpenTop() {
        Set<BlockPos> rock = new HashSet<>();
        for (int x = -2; x <= 4; x++) {
            for (int y = 56; y <= 63; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (!(x == 0 && z == 0 && (y == 60 || y == 61))) {
                        rock.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        WorldView world = stone(rock, Set.of());
        Position soldier = new Position(0.5, 60, 0.5);
        // The first step's cells of a stair up to the east: the head room over the digger, the step's body room and its head room.
        List<BlockPos> cut = List.of(new BlockPos(0, 62, 0), new BlockPos(1, 61, 0), new BlockPos(1, 62, 0));

        assertEquals(Optional.empty(), Clearing.next(world, cut, soldier, Set.of()), "no open top: the level clear offers nothing");
        Clearing.Cut first = Clearing.nextInTunnel(world, cut, soldier, Set.of(), s -> true).orElseThrow();
        assertEquals(new BlockPos(0, 60, 0), first.stand(), "from where it stands");
        assertTrue(cut.contains(first.block()), "one of the cut's cells: " + first.block());
        Set<BlockPos> gravelOver = Set.of(new BlockPos(0, 63, 0));
        WorldView loose = stone(rock, gravelOver);
        assertTrue(Clearing.nextInTunnel(loose, List.of(new BlockPos(0, 62, 0)), soldier, Set.of(), s -> true).isEmpty(),
                "gravel over the head room: not cut from under it");
    }

    /**
     * Dig toward what's known (operator, 2026-11-13): the block it stands on only onto solid ground
     * a block down - its own pillar, dug back down - and the block over its head only with nothing
     * over that to fall on it. To the side, always.
     */
    @Test
    void itDigsStraightDownOnlyOntoKnownGroundAndStraightUpOnlyFromUnderNothingThatFalls() {
        BlockPos stand = new BlockPos(0, 66, 0);
        BlockPos under = new BlockPos(0, 65, 0);
        assertTrue(Clearing.safeFrom(cells(Set.of(under, new BlockPos(0, 64, 0)), Set.of()), stand, under),
                "its own pillar: ground a block under it");
        assertTrue(!Clearing.safeFrom(cells(Set.of(under), Set.of()), stand, under), "open under it: a fall");
        WorldView overFarmland = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= 64;
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return y == 64;
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
        assertTrue(!Clearing.safeFrom(overFarmland, stand, under), "farmland under it: landing on it tramples it");
        assertTrue(Clearing.safeFrom(overFarmland, new BlockPos(1, 65, 0), under), "beside it, with the farmland a block down: fine");

        BlockPos over = new BlockPos(0, 68, 0);
        assertTrue(Clearing.safeFrom(cells(Set.of(over), Set.of()), stand, over), "nothing over it");
        assertTrue(!Clearing.safeFrom(cells(Set.of(over), Set.of(new BlockPos(0, 69, 0))), stand, over), "gravel over it would fall on its head");
        assertTrue(Clearing.safeFrom(cells(Set.of(), Set.of(new BlockPos(1, 69, 0))), stand, new BlockPos(1, 68, 0)), "beside it: always");
    }
}
