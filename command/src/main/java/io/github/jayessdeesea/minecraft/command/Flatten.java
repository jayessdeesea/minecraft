package io.github.jayessdeesea.minecraft.command;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * A site to flatten: an x and z rectangle taken down to a level - everything above {@code top} dug
 * out to the sky, the ground's top at {@code top} left as it is (operator, 2026-11-15: a primitive
 * to command the soldiers to flatten an area to a level, not tied to a keep). The keep's first
 * ground is one ({@link #KEEP}); the operator sets another with {@code !flatten}.
 *
 * <p>Geometry only, as {@link Groundwork} is: who works which part of it is the foreman's
 * ({@link LevelPlan}).
 *
 * @param minX its west edge
 * @param minZ its north edge
 * @param maxX its east edge
 * @param maxZ its south edge
 * @param top  the ground's top, kept: the floor is walked at {@code top + 1}
 * @param side the edge its depots stand off
 * @param soil whether its top is soil: its rock dug out of the top layer and the gaps filled with dirt, ground to
 *             grow on (operator, 2026-11-17: the farm-and-grove ground, backfilled with dirt)
 */
public record Flatten(int minX, int minZ, int maxX, int maxZ, int top, Side side, boolean soil) {

    /**
     * The edge a site's depots stand off: the west, as the keep's; or the north, where the west's ground is
     * taken - the farm-and-grove ground's west edge is the camp's farm by the water (operator, 2026-11-17).
     */
    public enum Side { WEST, NORTH }

    /** A site with its depots off its west edge. */
    public Flatten(int minX, int minZ, int maxX, int maxZ, int top) {
        this(minX, minZ, maxX, maxZ, top, Side.WEST);
    }

    /** A site whose top is left as it is. */
    public Flatten(int minX, int minZ, int maxX, int maxZ, int top, Side side) {
        this(minX, minZ, maxX, maxZ, top, side, false);
    }

    /** The keep's first ground: x and z -16..16 taken down to y=63 (operator, 2026-11-15). */
    public static final Flatten KEEP = new Flatten(Keep.MIN, Keep.MIN, Keep.MAX, Keep.MAX, Keep.TOP);

    /** The lowest and highest a site's top can be: the world's floor and its build limit. */
    static final int LOWEST = -64, HIGHEST = 318;

    /** How far off the site's edge its depot stands: off the dig and its bench, on ground nothing digs. */
    public static final int DEPOT_OFF = Flatten.BENCH + 4;

    public Flatten {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("a site's west and north edges are its lowest x and z: " + minX + ".." + maxX + ", " + minZ + ".." + maxZ);
        }
        if (top < LOWEST || top > HIGHEST) {
            throw new IllegalArgumentException("a site's top is within the world: " + top);
        }
        java.util.Objects.requireNonNull(side, "a site's depot side");
    }

    /**
     * The site {@code text} names: "x1 z1 x2 z2 y", its corners either way round, then "north" for its depots
     * off its north edge and "soil" for a top of soil, either or both - what the operator types after
     * {@code !flatten}. Empty for anything else.
     */
    public static Optional<Flatten> parse(String text) {
        String[] words = text.strip().split("[\\s,]+");
        if (words.length < 5 || words.length > 7) {
            return Optional.empty();
        }
        Side side = Side.WEST;
        boolean soil = false;
        for (int i = 5; i < words.length; i++) {
            String word = words[i].toLowerCase(Locale.ROOT);
            switch (word) {
                case "north", "west" -> side = Side.valueOf(word.toUpperCase(Locale.ROOT));
                case "soil" -> soil = true;
                default -> {
                    return Optional.empty();
                }
            }
        }
        try {
            int x1 = Integer.parseInt(words[0]), z1 = Integer.parseInt(words[1]);
            int x2 = Integer.parseInt(words[2]), z2 = Integer.parseInt(words[3]), y = Integer.parseInt(words[4]);
            if (y < LOWEST || y > HIGHEST) {
                return Optional.empty();
            }
            return Optional.of(new Flatten(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), y, side, soil));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The dig's bottom: everything from here up is taken out. */
    public int bottom() {
        return top + 1;
    }

    /** Feet height on its floor. */
    public int floor() {
        return top + 1;
    }

    /** Everything from its bottom to the top of the world - and a soil site's top layer, its rock - dug out, top down. */
    public Box dig() {
        return new Box(minX, soil ? top : bottom(), minZ, maxX, Keep.SKY_TOP, maxZ);
    }

    /**
     * How far round the site its edge is benched (clearing rule 6; 29 CFR 1926.652, Appendix B Table
     * B-1's 1:1 slope for Type B soil): the ground {@code d} out from the edge may stand {@code d} over
     * the site's top, no higher, so the edge is a walkable slope and never a cliff over the work.
     * Twelve (operator, 2026-11-17: slope the hills round the edge so soldiers can climb up): at three,
     * builders on the hill beyond climbed up on steps of their own and had no walk down. The depots stand
     * past it.
     */
    public static final int BENCH = 12;

    /**
     * The bench's row {@code d} out from the edge (1 to {@link #BENCH}), as boxes - the ring of
     * columns that far out - each from {@code d} over the site's floor up: what's in them is dug.
     */
    public java.util.List<Box> bench(int d) {
        int lo = bottom() + d;
        return java.util.List.of(
                new Box(minX - d, lo, minZ - d, maxX + d, Keep.SKY_TOP, minZ - d),      // north
                new Box(minX - d, lo, maxZ + d, maxX + d, Keep.SKY_TOP, maxZ + d),      // south
                new Box(minX - d, lo, minZ - d + 1, minX - d, Keep.SKY_TOP, maxZ + d - 1), // west
                new Box(maxX + d, lo, minZ - d + 1, maxX + d, Keep.SKY_TOP, maxZ + d - 1)); // east
    }

    /** Whether column ({@code x}, {@code z}) is the site's or its bench's. */
    public boolean benchedContains(int x, int z) {
        return x >= minX - BENCH && x <= maxX + BENCH && z >= minZ - BENCH && z <= maxZ + BENCH;
    }

    /** Its ground's top layer. */
    public Box slab() {
        return new Box(minX, top, minZ, maxX, top, maxZ);
    }

    /** Its ground's top and any hollow open under it down to {@link Keep#FOOTING} below: what's filled, built up. */
    public Box fill() {
        return new Box(minX, top - Keep.FOOTING, minZ, maxX, top, maxZ);
    }

    /** A square {@code half} blocks out from {@code centre}, taken down to {@code top}: the camp's ground. */
    public static Flatten around(Position centre, int half, int top) {
        int x = (int) Math.floor(centre.x()), z = (int) Math.floor(centre.z());
        return new Flatten(x - half, z - half, x + half, z + half, top);
    }

    /**
     * The camp's ground in place of this, if this lies past {@code most} of the {@code camp}: a square {@code first} out
     * from it, at {@code top} (operator, 2026-11-21: one small flat base - the ground flattened the camp's own).
     */
    public Optional<Flatten> camps(Position camp, int first, int most, int top) {
        return Math.hypot(centre().x() - camp.x(), centre().z() - camp.z()) > most ? Optional.of(around(camp, first, top)) : Optional.empty();
    }

    /** The camp's ground a {@code ring} further, if this is the camp's and short of {@code most}. */
    public Optional<Flatten> ringFurther(Position camp, int ring, int most) {
        return half() < most && Math.hypot(centre().x() - camp.x(), centre().z() - camp.z()) <= ring
                ? Optional.of(around(camp, Math.min(most, half() + ring), top)) : Optional.empty();
    }

    /** How far its square reaches out from its middle, the larger way. */
    public int half() {
        return Math.max(maxX - minX, maxZ - minZ) / 2;
    }

    /** Whether column ({@code x}, {@code z}) is the site's. */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Where a squad sent to it is sent: its floor's middle. */
    public Position centre() {
        return new Position((minX + maxX + 1) / 2.0, floor(), (minZ + maxZ + 1) / 2.0);
    }

    /** Where its builders' depot goes: off its depot side's edge, halfway along it, on ground nothing digs. */
    public Position depot() {
        return side == Side.WEST ? new Position(minX - DEPOT_OFF, floor(), Math.floorDiv(minZ + maxZ, 2))
                : new Position(Math.floorDiv(minX + maxX, 2), floor(), minZ - DEPOT_OFF);
    }

    /** How far apart its depots stand along its west edge: none of it more than about half that from one. */
    static final int DEPOT_SPACING = 48;

    /**
     * Its depots (operator, 2026-11-15: a site this big needs more than one): off its depot side's edge,
     * one every {@link #DEPOT_SPACING} blocks along it, evenly spread, {@link #depot} among them - the
     * middle one. x=-16..16 z=-64..64 has three, at z=-43, 0 and 43; a keep's one.
     */
    public java.util.List<Position> depots() {
        boolean west = side == Side.WEST;
        int from = west ? minZ : minX, length = (west ? maxZ - minZ : maxX - minX) + 1;
        int count = Math.max(1, (length + DEPOT_SPACING - 1) / DEPOT_SPACING);
        java.util.List<Position> depots = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int along = i == count / 2 ? (int) (west ? depot().z() : depot().x()) : from + (2 * i + 1) * length / (2 * count);
            depots.add(west ? new Position(minX - DEPOT_OFF, floor(), along) : new Position(along, floor(), minZ - DEPOT_OFF));
        }
        return java.util.List.copyOf(depots);
    }

    /** Its widest half-span from its middle: how far round it to keep clear. */
    public int halfSpan() {
        return Math.max(maxX - minX, maxZ - minZ) / 2 + 1;
    }

    /** As the operator would say it: "x=-16..16 z=16..32 to y=63", and "depots north" for a north side. */
    public String describe() {
        return String.format(Locale.ROOT, "x=%d..%d z=%d..%d to y=%d", minX, maxX, minZ, maxZ, top)
                + (side == Side.NORTH ? ", depots north" : "") + (soil ? ", topped with soil" : "");
    }

    /**
     * The next ground to bring to this site's level once this one is flat (operator, 2026-11-18: trend to sea level,
     * even in the airhead - flat is efficient to walk and to grow): of {@code ground}, the nearest box not inside this
     * site whose top {@code topOf} reads more than a block off this one's, as a site its size at this one's top, soil,
     * its depots off this one's side. Empty when all of it is level, or unread - and while this site itself doesn't
     * read level: a count of nothing left to dig, measured before its ground's last change, isn't flat.
     */
    public Optional<Flatten> next(List<Box> ground, Function<Box, OptionalInt> topOf) {
        if (topOf.apply(new Box(minX, top, minZ, maxX, top, maxZ)).stream().noneMatch(t -> Math.abs(t - top) <= 1)) {
            return Optional.empty();
        }
        double cx = (minX + maxX) / 2.0, cz = (minZ + maxZ) / 2.0;
        return ground.stream()
                .filter(b -> b.minX() < minX || b.maxX() > maxX || b.minZ() < minZ || b.maxZ() > maxZ)
                .filter(b -> topOf.apply(b).stream().anyMatch(t -> Math.abs(t - top) > 1))
                .min(Comparator.comparingDouble(b -> Math.hypot((b.minX() + b.maxX()) / 2.0 - cx, (b.minZ() + b.maxZ()) / 2.0 - cz)))
                .map(b -> new Flatten(b.minX(), b.minZ(), b.maxX(), b.maxZ(), top, side, true));
    }
}
