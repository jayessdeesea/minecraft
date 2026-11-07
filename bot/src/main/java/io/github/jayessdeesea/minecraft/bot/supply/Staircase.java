package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.List;
import java.util.Optional;

/**
 * A spiral staircase cut down into the ground (docs/military-doctrine.md §16): one block down a step,
 * round the eight cells about a central pillar, so the whole shaft stays inside 3x3 and every eight
 * steps it passes under its own top, five blocks of rock between turns. Each step is three cells
 * cleared - feet, head, and the head room a body needs to step down onto it - and its floor is left
 * standing. Steps go one block down and sideways, so a soldier walks down and jumps back up them:
 * no ladder, and a slip is a fall of one block.
 *
 * @param head    the cell a soldier stands in at the top, on the pillar
 * @param bottom  the height the feet reach on the last step
 */
public record Staircase(BlockPos head, int bottom) {

    /** The eight cells round the pillar, each beside the last: north, then clockwise. */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};
    /** Deepest a staircase goes: a few blocks clear of bedrock's ragged top. */
    public static final int DEEPEST = -58;

    public Staircase {
        if (bottom >= head.y() || bottom < DEEPEST) {
            throw new IllegalArgumentException("a staircase from " + head + " goes down, to no deeper than " + DEEPEST + ", not " + bottom);
        }
    }

    /** How many steps down. */
    public int steps() {
        return head.y() - bottom;
    }

    /** Where the feet are on step {@code k} (0 the first down from the head): one block lower each step. */
    public BlockPos step(int k) {
        int[] d = RING[Math.floorMod(k, RING.length)];
        return new BlockPos(head.x() + d[0], head.y() - 1 - k, head.z() + d[1]);
    }

    /** Where a soldier stands to cut step {@code k}: the step above it, or the head. */
    public BlockPos above(int k) {
        return k == 0 ? head : step(k - 1);
    }

    /** The cells step {@code k} clears: its feet, its head, and the head room over them to step down into it. */
    public List<BlockPos> cells(int k) {
        BlockPos feet = step(k);
        return List.of(feet.offset(0, 2, 0), feet.offset(0, 1, 0), feet);
    }

    /** The staircase's head at the ground at ({@code x}, {@code z}): the highest standing cell within 24 blocks of {@code nearY}. */
    public static Optional<BlockPos> headAt(WorldView world, int x, int z, int nearY) {
        for (int y = nearY + 24; y >= nearY - 24; y--) {
            BlockPos cell = new BlockPos(x, y, z);
            if (Pathfinder.standable(world, cell)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
