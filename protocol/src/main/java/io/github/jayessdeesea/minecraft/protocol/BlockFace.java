package io.github.jayessdeesea.minecraft.protocol;

/**
 * A face of a block, in the order the wire numbers them ({@code Direction}: down, up, north, south,
 * west, east), with the offset to the neighbouring cell on that side.
 */
public enum BlockFace {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public final int dx;
    public final int dy;
    public final int dz;

    BlockFace(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    /** The face pointing the other way. */
    public BlockFace opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
