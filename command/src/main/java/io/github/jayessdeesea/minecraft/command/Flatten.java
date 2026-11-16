package io.github.jayessdeesea.minecraft.command;

import java.util.Locale;
import java.util.Optional;

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
 */
public record Flatten(int minX, int minZ, int maxX, int maxZ, int top) {

    /** The keep's first ground: x and z -16..16 taken down to y=63 (operator, 2026-11-15). */
    public static final Flatten KEEP = new Flatten(Keep.MIN, Keep.MIN, Keep.MAX, Keep.MAX, Keep.TOP);

    /** The lowest and highest a site's top can be: the world's floor and its build limit. */
    static final int LOWEST = -64, HIGHEST = 318;

    /** How far off the site's west edge its depot stands: off the dig, on ground nothing digs. */
    static final int DEPOT_OFF = 4;

    public Flatten {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("a site's west and north edges are its lowest x and z: " + minX + ".." + maxX + ", " + minZ + ".." + maxZ);
        }
        if (top < LOWEST || top > HIGHEST) {
            throw new IllegalArgumentException("a site's top is within the world: " + top);
        }
    }

    /**
     * The site {@code text} names: "x1 z1 x2 z2 y", its corners either way round - what the operator
     * types after {@code !flatten}. Empty for anything else.
     */
    public static Optional<Flatten> parse(String text) {
        String[] words = text.strip().split("[\\s,]+");
        if (words.length != 5) {
            return Optional.empty();
        }
        try {
            int x1 = Integer.parseInt(words[0]), z1 = Integer.parseInt(words[1]);
            int x2 = Integer.parseInt(words[2]), z2 = Integer.parseInt(words[3]), y = Integer.parseInt(words[4]);
            if (y < LOWEST || y > HIGHEST) {
                return Optional.empty();
            }
            return Optional.of(new Flatten(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), y));
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

    /** Everything from its bottom to the top of the world: dug out, top down. */
    public Box dig() {
        return new Box(minX, bottom(), minZ, maxX, Keep.SKY_TOP, maxZ);
    }

    /** Its ground's top layer. */
    public Box slab() {
        return new Box(minX, top, minZ, maxX, top, maxZ);
    }

    /** Its ground's top and any hollow open under it down to {@link Keep#FOOTING} below: what's filled, built up. */
    public Box fill() {
        return new Box(minX, top - Keep.FOOTING, minZ, maxX, top, maxZ);
    }

    /** Whether column ({@code x}, {@code z}) is the site's. */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /** Where a squad sent to it is sent: its floor's middle. */
    public Position centre() {
        return new Position((minX + maxX + 1) / 2.0, floor(), (minZ + maxZ + 1) / 2.0);
    }

    /** Where its builders' depot goes: off its west edge, halfway along it, on ground nothing digs. */
    public Position depot() {
        return new Position(minX - DEPOT_OFF, floor(), Math.floorDiv(minZ + maxZ, 2));
    }

    /** Its widest half-span from its middle: how far round it to keep clear. */
    public int halfSpan() {
        return Math.max(maxX - minX, maxZ - minZ) / 2 + 1;
    }

    /** As the operator would say it: "x=-16..16 z=16..32 to y=63". */
    public String describe() {
        return String.format(Locale.ROOT, "x=%d..%d z=%d..%d to y=%d", minX, maxX, minZ, maxZ, top);
    }
}
