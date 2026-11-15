package io.github.jayessdeesea.minecraft.command;


/**
 * The main base's keep (backlog/cells.md): x and z from -16 to 16, centred on the origin, taken
 * down to y=63 (operator, 2026-11-15) - everything above it dug out to the sky, the ground's top
 * at y=63 solid, filled where it's open, its floor walked at feet y=64. The heights are the plan's,
 * never the ground's: what the world differs from them by is the work.
 *
 * <p>Geometry only, as {@link Groundwork} is: which soldier works which strip is
 * {@link Tasking#keep}'s.
 */
public final class Keep {

    /** The keep's west and north edge, and its east and south: 33 blocks a side. */
    public static final int MIN = -16;
    public static final int MAX = 16;
    /** The ground's top, y=63: the layer kept solid, filled where it's open. */
    public static final int TOP = 63;
    /** The dig's bottom: everything from here up is taken out. */
    public static final int BOTTOM = TOP + 1;
    /** Feet height on the keep's floor. */
    public static final int FLOOR = TOP + 1;
    /**
     * Whether the keep's ground is built up - its open cells filled - once it's dug down: not for
     * now (operator, 2026-11-15: "don't bother building it up"); taken down to y=63 and left.
     */
    public static final boolean FILL = false;
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

    /** The top of the world: the sky over the keep is cleared all the way up. */
    public static final int SKY_TOP = 319;

    /**
     * The logs a squad of the keep's builders needs between it and the stockpile to set up the depot
     * and keep its tools (operator, 2026-11-14: a flattening campaign starts with its supply depot):
     * two a chest (eight planks) for the chests the dig's stone wants, one for the table, and a
     * handle each for a pickaxe and a shovel - stone dug by hand drops nothing, and the slab is laid
     * from what the dig drops. Live, at four, squads carrying a dozen logs asked for none, the depot
     * had no chest for days, and four builders of eight lacked a pickaxe or a shovel.
     */
    public static final int LOGS_WANTED = 16;

    /**
     * Everything from the dig's bottom up to the top of the world: dug out, top down. Not to a fixed height over the floor: ground is dug only from an open top,
     * and a hill standing over the box would have kept every block under it.
     */
    public static Box dig() {
        return new Box(MIN, BOTTOM, MIN, MAX, SKY_TOP, MAX);
    }

    /** The keep's ground: its top layer, kept solid. */
    public static Box slab() {
        return new Box(MIN, TOP, MIN, MAX, TOP, MAX);
    }

    /** What's filled: the ground's top, and any hollow open under it down to {@link #FOOTING} below. */
    public static Box fill() {
        return new Box(MIN, TOP - FOOTING, MIN, MAX, TOP, MAX);
    }

    /** Whether column ({@code x}, {@code z}) is the keep's. */
    public static boolean contains(int x, int z) {
        return x >= MIN && x <= MAX && z >= MIN && z <= MAX;
    }
}
