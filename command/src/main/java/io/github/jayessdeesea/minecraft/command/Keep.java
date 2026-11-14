package io.github.jayessdeesea.minecraft.command;

import java.util.Locale;
import java.util.Optional;

/**
 * The main base's keep (backlog/cells.md): a raised slab of solid cobblestone on chunks (-1, -1)
 * to (0, 0) - x and z from -16 to 15, centred on the origin - four blocks deep, y=64 to 67, on
 * ground at sea level, its floor walked at feet y=68 and the sky over it cleared. Cobblestone,
 * because endermen can't take it. The heights are the plan's, never the ground's: what the
 * world differs from them by is the work.
 *
 * <p>Geometry only, as {@link Groundwork} is: which soldier works which strip is
 * {@link Tasking#keep}'s.
 */
public final class Keep {

    /** The keep's west and north edge, and its east and south: two chunks a side. */
    public static final int MIN = -16;
    public static final int MAX = 15;
    /** The slab's bottom layer and its top: four deep, on ground at sea level, y=63. */
    public static final int BOTTOM = 64;
    public static final int TOP = 67;
    /** Feet height on the keep's floor. */
    public static final int FLOOR = TOP + 1;
    /** How far under the slab an open hollow - a pond, a pit - is filled from. */
    public static final int FOOTING = 8;

    /** Where a squad sent to build it is sent: the floor's middle. */
    public static final Position CENTRE = new Position(0, FLOOR, 0);
    /**
     * Where the builders' workshop and chest go: off the slab's west edge, toward the camp, on
     * ground nothing digs - a chest on the site would stand on a pillar of ground the dig can't take.
     */
    public static final Position DEPOT = new Position(MIN - 4, FLOOR, 0);

    private Keep() {
    }

    /**
     * How far below the highest ground left in and round its strip a soldier digs: neighbouring
     * strips stay within this of each other, so the site comes down as a terrace a soldier walks,
     * not as pits and towers (operator, 2026-11-13). One: a soldier steps up one block, and a
     * two-block edge between strips is a wall to it.
     */
    public static final int LAYER_WINDOW = 1;

    /** The lowest a soldier digs with the highest ground in and round its strip at {@code layer}: never under the slab. */
    public static int floor(int layer) {
        return Math.max(BOTTOM, layer - LAYER_WINDOW);
    }

    /** The top of the world: the sky over the keep is cleared all the way up. */
    public static final int SKY_TOP = 319;

    /**
     * The logs the keep's builders need between them: two for the chest at the depot (eight planks),
     * and one each for a pickaxe or four torches - stone dug by hand drops nothing, and the slab is
     * laid from what the dig drops.
     */
    public static final int LOGS_WANTED = 4;

    /**
     * The LOGSTAT's Class IV line for the keep's builders, said as the request it is when they're
     * short (FM 4-0: construction materials are requisitioned, or obtained locally; operator,
     * 2026-11-13: with no resources, the workers ask for supplies): fewer than {@link #LOGS_WANTED}
     * logs between {@code logsCarried} by the squad and {@code logsStockpiled} in the stockpile, so
     * with no chest standing at the depot ({@code chestStands}) none can be made, nor a pickaxe
     * nor a torch. Live, the keep's builders worked a day with no tree within 48 blocks, no chest to
     * stow in, and no pickaxe to dig stone for the slab. Empty when they have enough.
     */
    public static Optional<String> classFour(int logsCarried, int logsStockpiled, boolean chestStands) {
        if (logsCarried + logsStockpiled >= LOGS_WANTED) {
            return Optional.empty();
        }
        return Optional.of(String.format(Locale.ROOT, "Class IV RED - request: the keep's builders carry %d log%s between them and the"
                + " stockpile holds %d: %sno pickaxe or torch to make. Request logs - send a squad for WOOD where trees are.",
                logsCarried, logsCarried == 1 ? "" : "s", logsStockpiled, chestStands ? "" : "no chest at the keep's depot, "));
    }

    /**
     * Everything from the slab's bottom up to the top of the world: dug out, top down, but for the
     * slab's own stone. Not to a fixed height over the floor: ground is dug only from an open top,
     * and a hill standing over the box would have kept every block under it.
     */
    public static Box dig() {
        return new Box(MIN, BOTTOM, MIN, MAX, SKY_TOP, MAX);
    }

    /** The slab: four layers of cobblestone. */
    public static Box slab() {
        return new Box(MIN, BOTTOM, MIN, MAX, TOP, MAX);
    }

    /** What's filled: the slab, and any hollow open under it down to {@link #FOOTING} below. */
    public static Box fill() {
        return new Box(MIN, BOTTOM - FOOTING, MIN, MAX, TOP, MAX);
    }

    /** Whether column ({@code x}, {@code z}) is the keep's. */
    public static boolean contains(int x, int z) {
        return x >= MIN && x <= MAX && z >= MIN && z <= MAX;
    }
}
