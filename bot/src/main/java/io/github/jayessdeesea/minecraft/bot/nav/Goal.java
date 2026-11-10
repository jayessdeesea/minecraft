package io.github.jayessdeesea.minecraft.bot.nav;

/**
 * Where a soldier is trying to get: any standing spot within {@code horizontalTolerance} (distance
 * in the x/z plane from the point, to the cell's centre) and {@code verticalTolerance} (blocks of
 * feet height) of ({@code x}, {@code y}, {@code z}). The tolerances are the soldier's freedom - a
 * squad leader that wants everyone on one floor keeps {@code verticalTolerance} small; a squad
 * spread out in the open gets a large horizontal one.
 */
public record Goal(double x, double y, double z, double horizontalTolerance, double verticalTolerance) {

    public boolean contains(double px, double py, double pz) {
        return horizontalDistance(px, pz) <= horizontalTolerance && Math.abs(py - y) <= verticalTolerance;
    }

    /**
     * Whether standing in {@code cell} counts - judged at the cell's centre, or the cell holding the
     * point itself. A goal finer than half a block on a cell's edge has no centre within it: live,
     * soldiers ordered to such a point reported no route from the cell it was in.
     */
    public boolean contains(BlockPos cell) {
        return contains(cell.x() + 0.5, cell.y(), cell.z() + 0.5)
                || (int) Math.floor(x) == cell.x() && (int) Math.floor(z) == cell.z() && Math.abs(cell.y() - y) <= verticalTolerance;
    }

    /** Admissible distance-to-go for A*: how far outside the tolerance zone the cell is. */
    public double estimate(BlockPos cell) {
        return Math.max(0, horizontalDistance(cell.x() + 0.5, cell.z() + 0.5) - horizontalTolerance)
                + Math.max(0, Math.abs(cell.y() - y) - verticalTolerance);
    }

    private double horizontalDistance(double px, double pz) {
        return Math.hypot(px - x, pz - z);
    }
}
