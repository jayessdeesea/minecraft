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
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Plan;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.github.jayessdeesea.minecraft.bot.engineer.AccessRoute;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.command.Cell;

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

    /** {@code w}'s chest cell, centred. */
    static Position chestCentre(Workshop w) {
        return w.chest().map(c -> new Position(c.x() + 0.5, c.y(), c.z() + 0.5)).orElse(new Position(0, Integer.MIN_VALUE, 0));
    }

    static Position centre(BlockPos cell) {
        return new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }

    static Set<Long> footprint(PatrolBase.Plan plan) {
        return Stream.concat(plan.floor().stream(), plan.cells().stream())
                .map(c -> column((int) Math.floor(c.x()), (int) Math.floor(c.z())))
                .collect(Collectors.toUnmodifiableSet());
    }

    static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /** A dig of the haul road's cells and no others: a tunnel dig, cell by cell from where the cutter stands. */
    static Order.Dig roadDig(AccessRoute road) {
        return Order.Dig.of(road.toCut().stream().map(b -> new Cell(b.x(), b.y(), b.z())).toList());
    }

    /**
     * How a grove site ranks, lowest best (operator, 2026-11-14: tree farms on flat ground, ideally
     * at sea level): its cells' rise from lowest to highest counts most, then how far its middle is
     * off sea level, then how far it is from the depot, {@code ring} blocks.
     */
    static int groveScore(TreeFarm grove, int ring) {
        int low = grove.plot().stream().mapToInt(BlockPos::y).min().orElseThrow();
        int high = grove.plot().stream().mapToInt(BlockPos::y).max().orElseThrow();
        return 8 * (high - low) + Math.abs(grove.centre().y() - (SEA_LEVEL + 1)) + ring / 8;
    }

    /** A staircase as kept on disk: its head, its last step, and - once run on - its main tunnel's end. */
    static Staircase keptStairs(List<BlockPos> kept, boolean well) {
        return Staircase.kept(kept.get(0), kept.get(1), kept.size() == 3 ? Optional.of(kept.get(2)) : Optional.empty(), well);
    }

    /** Where a mine is kept on disk by {@code base}: a well's under its own key, a spiral's under the base. */
    static String mineKey(String base, boolean well) {
        return well ? "well " + base : base;
    }

    /** What's kept on disk of {@code stairs}: see {@link #keptStairs}. */
    static List<BlockPos> toKeep(Staircase stairs) {
        return stairs.stretches() == 1 ? List.of(stairs.head(), stairs.step(stairs.steps() - 1))
                : List.of(stairs.head(), stairs.step(stairs.steps() - 1), stairs.tunnelEnd());
    }

    /** Where a squad's own level of the mine ordered at ({@code x}, {@code z}) is kept on disk. */
    static String levelKey(int x, int z, String squad) {
        // A file's name: a unit's name may be a path ("Alpha/miner"), which won't write.
        return "mine" + x + "," + z + " " + squad.replaceAll("[^A-Za-z0-9]", "-");
    }

    static double distance(Position a, Position b) {
        return Math.sqrt((a.x() - b.x()) * (a.x() - b.x()) + (a.y() - b.y()) * (a.y() - b.y()) + (a.z() - b.z()) * (a.z() - b.z()));
    }

    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger(SiteGeometry.class);

    /**
     * Forgets each of {@code seen}'s chests the world, loaded there, no longer shows - and says which, and what
     * the world shows there instead: live (field452), the force's stores flipped between 34 logs and 1 every
     * half minute, a chest of logs dropped and seen again.
     */
    static void forgetChestsGone(java.util.Map<BlockPos, ?> seen, io.github.jayessdeesea.minecraft.bot.world.WorldView world) {
        seen.keySet().removeIf(c -> {
            boolean gone = world.isLoaded(c.x(), c.z()) && !world.isChest(c.x(), c.y(), c.z());
            if (gone) {
                LOG.warn("[Stores] the chest at {} is forgotten: the world there shows solid {}, hazard {}, its section known {}", c,
                        world.isSolid(c.x(), c.y(), c.z()), world.isHazard(c.x(), c.y(), c.z()), world.openSection(c.x(), c.y(), c.z()));
            }
            return gone;
        });
    }
}
