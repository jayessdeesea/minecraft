package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.List;

/**
 * A box of cells, its corners inclusive: the region a concrete command works on -
 * {@link Order.Dig}, {@link Order.Fill} and {@link Order.Gather}. The squad leader can say "dig x=0..31, y=64..88,
 * z=0..31" as one call, and the soldier works out the cells.
 *
 * <p>A soldier's commands are concrete like this - a box, a material, an amount - so it never
 * needs to know what it's building or why (backlog/echelon-contracts.md): the engineer turns a
 * task into boxes, the squad's drills hand them out.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Box {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("an empty box: " + minX + ".." + maxX + ", " + minY + ".." + maxY + ", " + minZ + ".." + maxZ);
        }
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** How many cells it holds. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Split into {@code parts} boxes along its longer side, as near equal as they come: one strip
     * each for soldiers working the same box, so they don't crowd one column. Fewer when it's
     * narrower than {@code parts}.
     */
    public List<Box> strips(int parts) {
        if (parts < 1) {
            throw new IllegalArgumentException("parts: " + parts);
        }
        boolean alongX = maxX - minX >= maxZ - minZ;
        int from = alongX ? minX : minZ, to = alongX ? maxX : maxZ;
        int width = to - from + 1;
        int n = Math.min(parts, width);
        List<Box> strips = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int a = from + i * width / n, b = from + (i + 1) * width / n - 1;
            strips.add(alongX ? new Box(a, minY, minZ, b, maxY, maxZ) : new Box(minX, minY, a, maxX, maxY, b));
        }
        return List.copyOf(strips);
    }

    /** As the squad leader would say it: "x=0..31, y=64..88, z=0..31". */
    @Override
    public String toString() {
        return "x=" + minX + ".." + maxX + ", y=" + minY + ".." + maxY + ", z=" + minZ + ".." + maxZ;
    }
}
