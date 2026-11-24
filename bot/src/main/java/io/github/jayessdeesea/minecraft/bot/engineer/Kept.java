package io.github.jayessdeesea.minecraft.bot.engineer;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Cell;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * What is kept from a use: the one answer to "may this block be dug, cut, built in, stood in?". A caller keeps cells and
 * columns of its own - a camp's walls, another mission's chunk - and every caller keeps what a block's kind keeps: one
 * table of kind by use ({@link #keeps}), so no two askers answer differently for a gate or a chest. Pure: the world in,
 * the answer out.
 *
 * @param cells    the cells kept from every use
 * @param columns  the columns (x, z) kept from every use, top to bottom
 * @param gateBars the uses a gate's own cell is kept from: a flatten of a farm given up digs its gates and another dig
 *                 leaves them, so a gate's rule is each caller's, not the world's
 */
public record Kept(Set<Cell> cells, BiPredicate<Integer, Integer> columns, Set<Use> gateBars) {

    /** What is done to a cell: its block dug out, cut through for a road, a block built in it, a body stood in it. */
    public enum Use { DIG, CUT, BUILD, STAND }

    /**
     * What a cell is, as a rule keeps it - in the order asked, which is the order each use's askers read the world in:
     * a route asks of every cell it looks at, so the first read is the likeliest to answer.
     */
    public enum Kind {
        /** The cell over farmland: a block put down on farmland turns it to dirt. */
        ON_FARMLAND("on farmland"),
        /** A crop's own cell. */
        CROP("a crop"),
        /** A cell the force keeps open: a staircase's cut ({@link WorldView#noBuild}). */
        NO_BUILD("a way kept open"),
        /** A gate's own cell, shut or open: a way through to a walker. Kept from what {@link Kept#gateBars} says. */
        GATE("a gate"),
        /** Farmland a farm still has: where it's given up it's soil ({@link WorldView#farmlandGivenUp}). */
        FARMLAND("a farm's farmland"),
        /** Ground with water or lava beside or over it, which would flow into the hole ({@link Clearing#letsInWater}). */
        HOLDS_WATER("ground holding water or lava back"),
        /** Water, lava, fire: nothing a road goes through. */
        HAZARD("water, lava or fire"),
        /** A crafting table or a furnace. */
        WORKSTATION("a workstation"),
        /** A chest. */
        CHEST("a chest"),
        /**
         * A cell with a block in it as a body meets it ({@link WorldView#isSolid}): ground, a chest, a shut gate - and the
         * cell over a fence or a shut gate, no block itself. An open gate's cell is none.
         */
        BLOCK("a block in the cell");

        private static final Kind[] ASKED = values();

        private final String rule;

        Kind(String rule) {
            this.rule = rule;
        }

        /** Whether the cell at (x, y, z) is of this kind. */
        boolean at(WorldView world, int x, int y, int z) {
            return switch (this) {
                case ON_FARMLAND -> world.isFarmland(x, y - 1, z);
                case CROP -> world.wheatAge(x, y, z).isPresent();
                case NO_BUILD -> world.noBuild(x, y, z);
                case GATE -> world.isGate(x, y, z);
                case FARMLAND -> world.isFarmland(x, y, z) && !world.farmlandGivenUp(x, z);
                case HOLDS_WATER -> Clearing.letsInWater(world, new BlockPos(x, y, z));
                case HAZARD -> world.isHazard(x, y, z);
                case WORKSTATION -> world.workstation(x, y, z).isPresent();
                case CHEST -> world.isChest(x, y, z);
                case BLOCK -> world.isSolid(x, y, z);
            };
        }
    }

    /** No column kept, known by identity: asking it would box two coordinates a call, and a route asks of every cell. */
    private static final BiPredicate<Integer, Integer> NO_COLUMNS = (x, z) -> false;
    private static final String KEPT_CELL = "a cell kept";
    private static final String KEPT_COLUMN = "a column kept";

    /** Nothing of a caller's own kept: what a block's kind keeps, and a gate from being built in. */
    public static final Kept NONE = new Kept(Set.of(), NO_COLUMNS, Set.of(Use.BUILD));

    public Kept {
        cells = Set.copyOf(cells);
        Objects.requireNonNull(columns, "columns");
        gateBars = Set.copyOf(gateBars);
    }

    /** A caller's {@code cells} kept and no column, with what {@link #NONE} keeps: a set of cells, as a {@code Kept}. */
    public static Kept of(Set<Cell> cells) {
        return cells.isEmpty() ? NONE : new Kept(cells, NO_COLUMNS, NONE.gateBars);
    }

    /** As {@link #of}, its cells named as blocks. */
    public static Kept ofBlocks(Collection<BlockPos> blocks) {
        Set<Cell> cells = new HashSet<>();
        blocks.forEach(b -> cells.add(new Cell(b.x(), b.y(), b.z())));
        return of(cells);
    }

    /**
     * The table: whether a cell of {@code kind} is kept from {@code use}. A chest or a workstation is dug like any block
     * that breaks - the squad's own are a caller's cells - but no road is cut through one; farmland and a crop are a
     * farm's, kept from the dig and from a block put on them, and walked over freely.
     */
    public boolean keeps(Kind kind, Use use) {
        return switch (kind) {
            case ON_FARMLAND, CROP, NO_BUILD -> use == Use.BUILD;
            case GATE -> gateBars.contains(use);
            case FARMLAND, HOLDS_WATER -> use == Use.DIG;
            case HAZARD, WORKSTATION, CHEST -> use == Use.CUT;
            case BLOCK -> use == Use.STAND;
        };
    }

    /** Whether {@code cell} is kept from {@code use}: a cell or a column of the caller's, or by its kind. */
    public boolean bars(Use use, WorldView world, BlockPos cell) {
        return barring(use, world, cell.x(), cell.y(), cell.z()) != null;
    }

    /** The rule that keeps {@code cell} from {@code use}, named for a log; empty when nothing does. */
    public Optional<String> why(Use use, WorldView world, BlockPos cell) {
        return Optional.ofNullable(barring(use, world, cell.x(), cell.y(), cell.z()));
    }

    /**
     * One row of the table asked alone: whether the cell at (x, y, z) is of {@code kind} and that keeps it from
     * {@code use}. For an asker that reads the world for more than what is kept - {@link Clearing#diggable} asks what
     * breaks between the rows - so it reads no cell twice.
     */
    public boolean bars(Use use, Kind kind, WorldView world, int x, int y, int z) {
        return keeps(kind, use) && kind.at(world, x, y, z);
    }

    /**
     * Whether the cell at (x, y, z) is the caller's own - one of its cells, or in one of its columns - whatever stands in
     * it and whatever the use. For an asker that reads what a block's kind keeps itself, so it reads no cell twice: this
     * reads no block.
     */
    public boolean holds(int x, int y, int z) {
        return !cells.isEmpty() && cells.contains(new Cell(x, y, z)) || columns != NO_COLUMNS && columns.test(x, z);
    }

    private String barring(Use use, WorldView world, int x, int y, int z) {
        if (!cells.isEmpty() && cells.contains(new Cell(x, y, z))) {
            return KEPT_CELL;
        }
        if (columns != NO_COLUMNS && columns.test(x, z)) {
            return KEPT_COLUMN;
        }
        // A tree's block and a gate are dug whatever is beside them: neither is ground, and neither holds water back.
        if (use == Use.DIG && (world.isLog(x, y, z) || world.leaves(x, y, z).isPresent() || world.isGate(x, y, z))) {
            return bars(use, Kind.GATE, world, x, y, z) ? Kind.GATE.rule : null;
        }
        for (Kind kind : Kind.ASKED) {
            if (bars(use, kind, world, x, y, z)) {
                return kind.rule;
            }
        }
        return null;
    }

    /** What either keeps. */
    public Kept and(Kept other) {
        Set<Cell> both = new HashSet<>(cells);
        both.addAll(other.cells);
        Set<Use> gates = new HashSet<>(gateBars);
        gates.addAll(other.gateBars);
        BiPredicate<Integer, Integer> either = columns == NO_COLUMNS ? other.columns
                : other.columns == NO_COLUMNS ? columns : columns.or(other.columns);
        return new Kept(both, either, gates);
    }

    /** This, less {@code cell} of its cells - the one a caller is sent to work. A column kept is kept whole. */
    public Kept but(Cell cell) {
        if (!cells.contains(cell)) {
            return this;
        }
        Set<Cell> rest = new HashSet<>(cells);
        rest.remove(cell);
        return new Kept(rest, columns, gateBars);
    }
}
