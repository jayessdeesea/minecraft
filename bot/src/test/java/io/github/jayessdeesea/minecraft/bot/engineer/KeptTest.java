package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.engineer.Kept.Use;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The table of kind by use, a row a test: each kind of cell against each use, then what a caller keeps of its own. */
class KeptTest {

    /** Flat ground with its top at y=63, and one of each kind on it, well apart. */
    private static final BlockPos SHUT_GATE = new BlockPos(0, 64, 0);
    private static final BlockPos OPEN_GATE = new BlockPos(10, 64, 0);
    private static final BlockPos FENCE = new BlockPos(20, 64, 0);
    private static final BlockPos FARMLAND = new BlockPos(30, 63, 0);
    private static final BlockPos GIVEN_UP = new BlockPos(40, 63, 0);
    private static final BlockPos CROP = new BlockPos(30, 64, 0);
    private static final BlockPos CHEST = new BlockPos(50, 64, 0);
    private static final BlockPos TABLE = new BlockPos(60, 64, 0);
    private static final BlockPos KEPT_OPEN = new BlockPos(70, 64, 0);
    private static final BlockPos WATER = new BlockPos(80, 63, 0);
    private static final BlockPos LOG = new BlockPos(79, 63, 0);
    private static final BlockPos WET_GATE = new BlockPos(80, 63, 1);
    private static final BlockPos GROUND = new BlockPos(100, 63, 0);
    private static final BlockPos AIR = new BlockPos(100, 64, 0);

    /** How many times the world was read, of any block. */
    private int reads;

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            reads++;
            BlockPos at = new BlockPos(x, y, z);
            return solidHere(x, y, z) || at.equals(SHUT_GATE.offset(0, 1, 0)) || at.equals(FENCE.offset(0, 1, 0));
        }

        @Override
        public boolean solidHere(int x, int y, int z) {
            BlockPos at = new BlockPos(x, y, z);
            if (at.equals(WATER) || at.equals(WET_GATE)) {
                return false;
            }
            return y <= 63 || at.equals(SHUT_GATE) || at.equals(FENCE) || at.equals(CHEST) || at.equals(TABLE);
        }

        @Override
        public boolean isGate(int x, int y, int z) {
            reads++;
            BlockPos at = new BlockPos(x, y, z);
            return at.equals(SHUT_GATE) || at.equals(OPEN_GATE) || at.equals(WET_GATE);
        }

        @Override
        public boolean isShutGate(int x, int y, int z) {
            return SHUT_GATE.equals(new BlockPos(x, y, z));
        }

        @Override
        public boolean isFarmland(int x, int y, int z) {
            reads++;
            BlockPos at = new BlockPos(x, y, z);
            return at.equals(FARMLAND) || at.equals(GIVEN_UP);
        }

        @Override
        public boolean farmlandGivenUp(int x, int z) {
            return x == GIVEN_UP.x() && z == GIVEN_UP.z();
        }

        @Override
        public OptionalInt wheatAge(int x, int y, int z) {
            reads++;
            return CROP.equals(new BlockPos(x, y, z)) ? OptionalInt.of(3) : OptionalInt.empty();
        }

        @Override
        public boolean isChest(int x, int y, int z) {
            reads++;
            return CHEST.equals(new BlockPos(x, y, z));
        }

        @Override
        public Optional<Workstation> workstation(int x, int y, int z) {
            reads++;
            return TABLE.equals(new BlockPos(x, y, z)) ? Optional.of(Workstation.CRAFTING_TABLE) : Optional.empty();
        }

        @Override
        public boolean noBuild(int x, int y, int z) {
            reads++;
            return KEPT_OPEN.equals(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            reads++;
            return WATER.equals(new BlockPos(x, y, z));
        }

        @Override
        public boolean isLog(int x, int y, int z) {
            return LOG.equals(new BlockPos(x, y, z));
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    /** The uses {@code cell} is kept from, by its kind alone. */
    private Set<Use> barred(BlockPos cell) {
        return barred(Kept.NONE, cell);
    }

    private Set<Use> barred(Kept kept, BlockPos cell) {
        Set<Use> uses = EnumSet.noneOf(Use.class);
        for (Use use : Use.values()) {
            if (kept.bars(use, world, cell)) {
                uses.add(use);
            }
            assertEquals(uses.contains(use), kept.why(use, world, cell).isPresent(), "a reason whenever it's barred: " + use + " at " + cell);
        }
        return uses;
    }

    @Test
    void aGateIsKeptFromABlockBuiltInItAndDugAndCutLikeAnyOther() {
        assertEquals(Set.of(Use.BUILD, Use.STAND), barred(SHUT_GATE), "shut, it reads solid: nothing stands in it");
        assertEquals(Set.of(Use.BUILD), barred(OPEN_GATE), "open, it's stood in");
        assertEquals(Optional.of("a gate"), Kept.NONE.why(Use.BUILD, world, OPEN_GATE));
    }

    @Test
    void theCellOverAFenceOrAShutGateIsKeptOnlyFromAStand() {
        assertEquals(Set.of(Use.STAND), barred(FENCE.offset(0, 1, 0)), "no block, and solid to a body");
        assertEquals(Set.of(Use.STAND), barred(SHUT_GATE.offset(0, 1, 0)));
        assertEquals(Set.of(), barred(OPEN_GATE.offset(0, 1, 0)), "over an open gate, open");
    }

    @Test
    void farmlandIsKeptFromADigWhileAFarmHasItAndTheCellOverItFromABlock() {
        assertEquals(Set.of(Use.DIG, Use.STAND), barred(FARMLAND), "a farm's farmland; its own cell has a block in it");
        assertEquals(Set.of(Use.STAND), barred(GIVEN_UP), "given up, it's soil");
        assertEquals(Set.of(Use.BUILD), barred(GIVEN_UP.offset(0, 1, 0)), "a block on farmland, given up or not");
        assertEquals(Optional.of("a farm's farmland"), Kept.NONE.why(Use.DIG, world, FARMLAND));
    }

    @Test
    void aCropIsKeptFromABlockBuiltInIt() {
        assertEquals(Set.of(Use.BUILD), barred(CROP), "in a crop, and on the farmland under it");
        assertEquals(Optional.of("on farmland"), Kept.NONE.why(Use.BUILD, world, CROP), "the first rule asked names it");
    }

    @Test
    void aChestAndAWorkstationAreKeptFromARoadsCutAndDugLikeAnyBlock() {
        assertEquals(Set.of(Use.CUT, Use.STAND), barred(CHEST));
        assertEquals(Set.of(Use.CUT, Use.STAND), barred(TABLE));
        assertEquals(Optional.of("a chest"), Kept.NONE.why(Use.CUT, world, CHEST));
        assertEquals(Optional.of("a workstation"), Kept.NONE.why(Use.CUT, world, TABLE));
    }

    @Test
    void aCellKeptOpenIsKeptOnlyFromABlockBuiltInIt() {
        assertEquals(Set.of(Use.BUILD), barred(KEPT_OPEN));
        assertEquals(Set.of(), barred(KEPT_OPEN.offset(0, 1, 0)), "the cell over it is not");
    }

    @Test
    void waterIsKeptFromARoadsCutAndTheGroundHoldingItBackFromADig() {
        assertEquals(Set.of(Use.CUT), barred(WATER), "the water's own cell");
        assertEquals(Set.of(Use.DIG, Use.STAND), barred(WATER.offset(1, 0, 0)), "the ground beside it");
        assertEquals(Set.of(Use.DIG, Use.STAND), barred(WATER.offset(0, -1, 0)), "the ground under it");
        assertEquals(Set.of(Use.STAND), barred(LOG), "a log beside it is no ground, and dug all the same");
        assertEquals(Set.of(Use.BUILD), barred(WET_GATE), "a gate beside water is dug all the same");
    }

    @Test
    void plainGroundIsKeptOnlyFromAStandInItAndOpenAirFromNothing() {
        assertEquals(Set.of(Use.STAND), barred(GROUND));
        assertEquals(Set.of(), barred(AIR));
        assertEquals(Optional.empty(), Kept.NONE.why(Use.BUILD, world, AIR));
    }

    /** The same answer as {@link Clearing#diggable} gives of every kind that breaks: one rule, asked whole or a row at a time. */
    @Test
    void whatBreaksIsDiggableJustWhereNoDigIsBarred() {
        WorldView soil = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return world.solidHere(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return world.isHazard(x, y, z);
            }

            @Override
            public boolean isGate(int x, int y, int z) {
                return world.isGate(x, y, z);
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return world.isFarmland(x, y, z);
            }

            @Override
            public boolean farmlandGivenUp(int x, int z) {
                return world.farmlandGivenUp(x, z);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        for (BlockPos block : List.of(SHUT_GATE, OPEN_GATE, WET_GATE, FARMLAND, GIVEN_UP, CHEST, TABLE, GROUND, WATER.offset(1, 0, 0),
                WATER.offset(0, -1, 0))) {
            assertEquals(Clearing.diggable(soil, block.x(), block.y(), block.z()), !Kept.NONE.bars(Use.DIG, soil, block), "at " + block);
        }
    }

    /** A route asks of every cell it looks at: no more reads than the rules for its use, and none for what a caller doesn't keep. */
    @Test
    void anAskReadsTheWorldOnlyForTheRulesOfItsUse() {
        reads = 0;
        Kept.NONE.bars(Use.BUILD, world, AIR);
        assertEquals(4, reads, "a block built: farmland under it, a crop, a way kept open, a gate");
        reads = 0;
        Kept.NONE.bars(Use.STAND, world, AIR);
        assertEquals(1, reads, "a stand: a block in the cell");
        reads = 0;
        Kept.NONE.bars(Use.CUT, world, AIR);
        assertEquals(3, reads, "a road's cut: liquid, a workstation, a chest");
    }

    @Test
    void aCallersCellsAndColumnsAreKeptFromEveryUse() {
        Kept cells = new Kept(Set.of(new Cell(AIR.x(), AIR.y(), AIR.z())), (x, z) -> false, Set.of());
        assertEquals(EnumSet.allOf(Use.class), barred(cells, AIR));
        assertEquals(Optional.of("a cell kept"), cells.why(Use.DIG, world, AIR));
        assertEquals(Set.of(), barred(cells, AIR.offset(0, 1, 0)), "the cell over it is not");

        Kept columns = new Kept(Set.of(), (x, z) -> x == AIR.x() && z == AIR.z(), Set.of());
        assertEquals(EnumSet.allOf(Use.class), barred(columns, AIR.offset(0, 9, 0)), "a column, top to bottom");
        assertEquals(Optional.of("a column kept"), columns.why(Use.STAND, world, AIR));
        assertEquals(Set.of(), barred(columns, AIR.offset(1, 0, 0)), "the column beside it is not");
    }

    /** What a caller holds is its cells and columns alone: a block's kind is no part of it, and no block is read for it. */
    @Test
    void holdsAnswersForACallersOwnCellsAndColumnsAndNeverByKind() {
        Kept kept = new Kept(Set.of(new Cell(AIR.x(), AIR.y(), AIR.z())), (x, z) -> x == GROUND.x() + 1, EnumSet.allOf(Use.class));
        reads = 0;

        assertTrue(kept.holds(AIR.x(), AIR.y(), AIR.z()), "its cell");
        assertFalse(kept.holds(AIR.x(), AIR.y() + 1, AIR.z()), "not the cell over it");
        assertTrue(kept.holds(GROUND.x() + 1, 0, 7) && kept.holds(GROUND.x() + 1, 300, 7), "its column, top to bottom");
        for (BlockPos kind : List.of(SHUT_GATE, OPEN_GATE, FARMLAND, CROP, CHEST, TABLE, KEPT_OPEN, WATER, GROUND)) {
            assertFalse(kept.holds(kind.x(), kind.y(), kind.z()), "none of its own at " + kind + ", whatever stands there");
            assertFalse(Kept.NONE.holds(kind.x(), kind.y(), kind.z()), "and nothing held by one that keeps nothing of its own");
        }
        assertEquals(0, reads, "the world is never read");
    }

    @Test
    void aSetOfCellsAsAKeptKeepsThoseCellsAndWhatNoneKeeps() {
        Cell a = new Cell(1, 70, 1);
        Kept kept = Kept.of(Set.of(a));

        assertEquals(new Kept(Set.of(a), Kept.NONE.columns(), Kept.NONE.gateBars()), kept, "its cells, no column, a gate as NONE has it");
        assertTrue(kept.holds(1, 70, 1));
        assertFalse(kept.holds(1, 71, 1) || kept.holds(2, 70, 1), "no column with them");
        assertEquals(kept, Kept.ofBlocks(List.of(new BlockPos(1, 70, 1))), "the same, its cells named as blocks");
        assertTrue(Kept.of(Set.of()) == Kept.NONE && Kept.ofBlocks(Set.of()) == Kept.NONE, "of no cells, NONE itself");
        for (Use use : Use.values()) {
            for (BlockPos at : List.of(SHUT_GATE, OPEN_GATE, FARMLAND, CROP, CHEST, TABLE, KEPT_OPEN, WATER, GROUND, AIR)) {
                assertEquals(Kept.NONE.bars(use, world, at), kept.bars(use, world, at), use + " at " + at + ": by kind, as NONE");
            }
        }
    }

    @Test
    void aGatesRuleIsEachCallers() {
        Kept flatten = new Kept(Set.of(), (x, z) -> false, Set.of());
        assertEquals(Set.of(), barred(flatten, OPEN_GATE), "one that keeps a gate from nothing");
        Kept road = new Kept(Set.of(), (x, z) -> false, EnumSet.allOf(Use.class));
        assertEquals(EnumSet.allOf(Use.class), barred(road, OPEN_GATE), "one that keeps it from everything");
        assertEquals(EnumSet.allOf(Use.class), barred(road, WET_GATE));
        assertEquals(Set.of(), barred(road, AIR), "and a cell that's no gate from nothing more");
    }

    @Test
    void andKeepsWhatEitherKeeps() {
        Cell a = new Cell(1, 70, 1), b = new Cell(2, 70, 2);
        Kept both = new Kept(Set.of(a), (x, z) -> x == 5, Set.of(Use.DIG)).and(new Kept(Set.of(b), (x, z) -> x == 6, Set.of(Use.CUT)));

        assertEquals(Set.of(a, b), both.cells());
        assertEquals(Set.of(Use.DIG, Use.CUT), both.gateBars());
        assertTrue(both.bars(Use.BUILD, world, new BlockPos(5, 70, 0)) && both.bars(Use.BUILD, world, new BlockPos(6, 70, 0)), "either's columns");
        assertFalse(both.bars(Use.BUILD, world, new BlockPos(7, 70, 0)));

        Kept withNone = Kept.NONE.and(new Kept(Set.of(a), (x, z) -> false, Set.of()));
        assertEquals(Set.of(Use.BUILD), withNone.gateBars(), "with nothing kept, a gate still isn't built in");
        assertTrue(withNone.bars(Use.DIG, world, new BlockPos(1, 70, 1)));
        assertTrue(new Kept(Set.of(), (x, z) -> x == 5, Set.of()).and(Kept.NONE).bars(Use.DIG, world, new BlockPos(5, 70, 0)));
    }

    @Test
    void butLetsOneCellGoAndKeepsAColumnWhole() {
        Cell a = new Cell(1, 70, 1), b = new Cell(2, 70, 2);
        Kept kept = new Kept(new HashSet<>(Set.of(a, b)), (x, z) -> x == 5, Set.of(Use.BUILD));

        Kept less = kept.but(a);
        assertEquals(Set.of(b), less.cells());
        assertEquals(Set.of(a, b), kept.cells(), "the one it was made from is as it was");
        assertEquals(kept.gateBars(), less.gateBars());
        assertTrue(less.but(new Cell(5, 70, 0)).bars(Use.DIG, world, new BlockPos(5, 70, 0)), "a cell in a kept column stays kept");
        assertEquals(Kept.NONE, Kept.NONE.but(a), "nothing to let go");
    }
}
