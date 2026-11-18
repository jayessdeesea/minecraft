package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Clearing;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.github.jayessdeesea.minecraft.app.SoldierRunner.*;

/**
 * Pure geometry of the sites the force works - the flatten's boxes and columns, a tree's box, the
 * soil borrow - and how a ground order is said: no state, moved out of the soldier runner
 * (backlog/split-oversized-sources.md).
 */
final class SiteGeometry {

    private SiteGeometry() {
    }

    /** When {@code boxes}, with {@code columns} left at the level, are due done from now. */
    static long dueMillis(List<Box> boxes, List<LevelPlan.Column> columns, int climb) {
        long blocks = columns.stream().filter(c -> boxes.stream().anyMatch(b -> c.x() >= b.minX() && c.x() <= b.maxX()
                && c.z() >= b.minZ() && c.z() <= b.maxZ())).count();
        return DUE_SLACK_MILLIS + blocks * DUE_MILLIS_A_BLOCK + climb * DUE_MILLIS_A_CLIMB;
    }

    /** Blocks a builder at {@code at} climbs to {@code level}: none from at or over it, or not known where. */
    static int climb(Position at, int level) {
        return at == null ? 0 : Math.max(0, level - (int) Math.floor(at.y()));
    }

    /**
     * How high the flatten's rectangles reach: {@link #CEILING_SLACK} over the site's highest ground
     * or step, not the sky - each builder's rectangle is scanned every squad cycle (Clearing.toClear),
     * and live (commit 528's profile), from the level to y=319 that was a fifth of the CPU.
     */
    static int siteCeiling(Box site, int level, java.util.function.IntBinaryOperator calm, java.util.function.IntBinaryOperator calmStep) {
        int ceiling = level;
        for (int x = site.minX(); x <= site.maxX(); x++) {
            for (int z = site.minZ(); z <= site.maxZ(); z++) {
                ceiling = Math.max(ceiling, Math.max(calm.applyAsInt(x, z), calmStep.applyAsInt(x, z)));
            }
        }
        return ceiling + CEILING_SLACK;
    }

    static boolean keepColumnsLeft(int level, Box box, java.util.function.IntBinaryOperator top, java.util.function.IntBinaryOperator stepTop) {
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (top.applyAsInt(x, z) >= level || stepTop.applyAsInt(x, z) >= level) {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code boxes} as the log says them. */
    static String boxesSaid(List<Box> boxes) {
        return boxes.stream().map(b -> "x=" + b.minX() + ".." + b.maxX() + " z=" + b.minZ() + ".." + b.maxZ()).toList().toString();
    }

    /** The box round {@code tree}'s logs and its crown. */
    static Box treeBox(Set<BlockPos> tree) {
        return new Box(tree.stream().mapToInt(BlockPos::x).min().orElseThrow() - 2, tree.stream().mapToInt(BlockPos::y).min().orElseThrow(),
                tree.stream().mapToInt(BlockPos::z).min().orElseThrow() - 2, tree.stream().mapToInt(BlockPos::x).max().orElseThrow() + 2,
                tree.stream().mapToInt(BlockPos::y).max().orElseThrow() + TREE_CROWN, tree.stream().mapToInt(BlockPos::z).max().orElseThrow() + 2);
    }

    /** The column box {@code reach} each way round x, z, all the way up and down. */
    static Box around(int x, int z, int reach) {
        return new Box(x - reach, Integer.MIN_VALUE, z - reach, x + reach, Integer.MAX_VALUE, z + reach);
    }

    /** The least box holding every one of {@code cells}, if there are any. */
    static Optional<Box> bounds(List<BlockPos> cells) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (BlockPos c : cells) {
            lo[0] = Math.min(lo[0], c.x());
            lo[1] = Math.min(lo[1], c.y());
            lo[2] = Math.min(lo[2], c.z());
            hi[0] = Math.max(hi[0], c.x());
            hi[1] = Math.max(hi[1], c.y());
            hi[2] = Math.max(hi[2], c.z());
        }
        return Optional.of(new Box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
    }

    /**
     * Where a soil site's dirt comes from: the ground east of it over its top, which the keep's own site
     * there is to have dug anyway - not a borrow pit holed into ground that's kept.
     */
    static Box soilBorrow(Flatten ground) {
        return new Box(ground.maxX() + Flatten.BENCH + 1, ground.bottom(), ground.minZ(),
                ground.maxX() + Flatten.BENCH + 16, Keep.SKY_TOP, ground.maxZ());
    }

    /** The register's name for a command on the keep. */
    static String keepTask(Order order) {
        return order instanceof Order.Fill ? "fill the keep" : "dig the keep";
    }

    /** The register's name for a ground-work command: what it's on. */
    static String groundTask(Order order) {
        return switch (order) {
            case Order.Dig _ -> "dig the camp's ground";
            case Order.Fill _ -> "fill the camp's ground";
            case Order.Gather g -> "gather " + g.resource();
            default -> "gather SOIL";
        };
    }
}
