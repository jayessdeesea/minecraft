package io.github.jayessdeesea.minecraft.command;

/**
 * The engineer's boxes for ground work round a site (backlog/echelon-contracts.md): what to dig so
 * the ground is level and open to the sky, what to fill so it has no hollows, and where to take
 * the dirt from. Geometry only - which soldier works which box is {@link Tasking}'s, how a box is
 * worked is the soldier's.
 */
public final class Groundwork {

    /** How far above the site's feet level its sky is cleared. */
    public static final int SKY = 24;
    /** How far below it a hollow is filled from. */
    public static final int DEPTH = 16;
    /** Blocks beyond the site's edge the borrow pit begins, so its digging never undercuts a wall there. */
    public static final int BORROW_GAP = 3;
    /** How far the borrow pit runs out from there, and how deep it may be dug. */
    public static final int BORROW_WIDTH = 6;
    public static final int BORROW_DEPTH = 3;

    private Groundwork() {
    }

    /** Everything over the site's ground within {@code radius} of {@code feet}: dug out, a tree from its foot, the ground from its top. */
    public static Box dig(Cell feet, int radius) {
        return new Box(feet.x() - radius, feet.y(), feet.z() - radius, feet.x() + radius, feet.y() + SKY, feet.z() + radius);
    }

    /** Everything under the site's feet level within {@code radius}: its hollows, filled up to it. */
    public static Box fill(Cell feet, int radius) {
        return new Box(feet.x() - radius, feet.y() - DEPTH, feet.z() - radius, feet.x() + radius, feet.y() - 1, feet.z() + radius);
    }

    /**
     * Where dirt for the site is dug: a strip of ground off its {@code side} (0 east, 1 west, 2
     * south, 3 north), {@link #BORROW_GAP} beyond its {@code radius}, the top few blocks of it - so
     * each soldier's pit is its own and none undercuts the site.
     */
    public static Box borrowPit(Cell feet, int radius, int side) {
        int near = radius + BORROW_GAP, far = near + BORROW_WIDTH - 1;
        int lo = feet.y() - BORROW_DEPTH, hi = feet.y() + 2;
        return switch (Math.floorMod(side, 4)) {
            case 0 -> new Box(feet.x() + near, lo, feet.z() - radius, feet.x() + far, hi, feet.z() + radius);
            case 1 -> new Box(feet.x() - far, lo, feet.z() - radius, feet.x() - near, hi, feet.z() + radius);
            case 2 -> new Box(feet.x() - radius, lo, feet.z() + near, feet.x() + radius, hi, feet.z() + far);
            default -> new Box(feet.x() - radius, lo, feet.z() - far, feet.x() + radius, hi, feet.z() - near);
        };
    }
}
