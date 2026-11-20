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
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.supply.Armourer;
import io.github.jayessdeesea.minecraft.bot.supply.Torchmaker;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import java.util.Map;

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

    /**
     * The point of {@code box} nearest {@code soldier}, at the box's middle height: where a gather from
     * it is looked for round. Its middle was 16 blocks' search in a pit 130 long - live (field340), the
     * keep's borrow pit along the site's side, its middle by the depots and dug out, and builders sent
     * for dirt stood a minute at a time finding none.
     */
    static Position nearestIn(Box box, Position soldier) {
        double x = Math.max(box.minX() + 0.5, Math.min(box.maxX() + 0.5, soldier.x()));
        double z = Math.max(box.minZ() + 0.5, Math.min(box.maxZ() + 0.5, soldier.z()));
        return new Position(x, (box.minY() + box.maxY()) / 2.0, z);
    }

    /** Whether a player's body - 0.6 across, 1.8 high, feet at {@code feet} - is in {@code cell}. */
    static boolean overlaps(Position feet, BlockPos cell) {
        double half = 0.3;
        return feet.x() + half > cell.x() && feet.x() - half < cell.x() + 1
                && feet.z() + half > cell.z() && feet.z() - half < cell.z() + 1
                && feet.y() + 1.8 > cell.y() && feet.y() < cell.y() + 1;
    }
    static boolean overlaps(Box a, Box b) {
        return a.minX() <= b.maxX() && b.minX() <= a.maxX() && a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
    }

    /** Whether {@code at} is at {@code workshop}'s stand: within reach of its fixtures. */
    static boolean atStand(Position at, Workshop workshop) {
        return Math.hypot(at.x() - workshop.stand().x() - 0.5, at.z() - workshop.stand().z() - 0.5) <= Armourer.WORKSHOP_TOLERANCE
                && Math.abs(at.y() - workshop.stand().y()) <= 1;
    }

    /** A depot's dump: ground {@link #DUMP_OFF} west of its stand, off the walks to its chests. */
    static Optional<Position> dumpOf(Workshop depot, io.github.jayessdeesea.minecraft.bot.world.WorldView world) {
        BlockPos stand = depot.stand();
        return TerrainSurveyor.surface(world, stand.x() - DUMP_OFF, stand.z(), stand.y() + CEILING_SLACK)
                .map(cell -> new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5));
    }

    /**
     * The stockpile a soldier draws from and stocks: its own workshop's chest, or with none standing
     * there, the camp's - the force's stores are common, whichever squad put them there (the
     * operator's, docs/echelons/tenets.md). Live, the keep's builders had no chest at the keep's
     * workshop - fitting one takes logs - and with only their own to go to drew nothing all day:
     * not the logs for the chest, nor for a pickaxe or a torch.
     */
    static Optional<Position> stockpileOf(Optional<Position> own, Optional<Position> camp) {
        return own.or(() -> camp);
    }

    /**
     * Why a soldier can't make torches, for its CANTCO: what it's short of, or with the makings
     * carried, that it has no workshop to make them at - live, a CANTCO read "short of {}".
     */
    static String torchShortfall(Inventory inventory, Workshop workshop) {
        Map<ItemClass, Integer> short_ = Torchmaker.shortOf(inventory, LIGHTING_TORCHES);
        return !short_.isEmpty() ? "short of " + short_ + " to make them"
                : workshop == null ? "the makings carried, but no workshop sited to make them at"
                : "the makings carried, but not yet made at the workshop at " + workshop.stand();
    }

    /** Of {@code main} - none if null - and {@code outposts}, the depot nearest {@code at}: the main one if none is nearer. */
    static Workshop nearestOf(Workshop main, List<Workshop> outposts, Position at) {
        Workshop best = main;
        double bestDistance = best == null ? Double.MAX_VALUE : Math.hypot(best.stand().x() + 0.5 - at.x(), best.stand().z() + 0.5 - at.z());
        for (Workshop w : outposts) {
            double d = Math.hypot(w.stand().x() + 0.5 - at.x(), w.stand().z() + 0.5 - at.z());
            if (d < bestDistance) {
                best = w;
                bestDistance = d;
            }
        }
        return best;
    }

    /** How near its mine's head, across, a miner's ground reaches the mine's stairs. */
    static final int MINE_STAIR_REACH = 4;

    /**
     * Whether the cells {@code walkable} from {@code from} are a closed pocket: the flood ran out of ground well inside
     * its span ({@code SoldierRunner.CUT_OFF_SPAN}) and its budget, so nothing past them is reached - not a gallery the
     * flood was cut short in.
     */
    static boolean closedPocket(Position from, java.util.Set<BlockPos> walkable) {
        return walkable.size() < SoldierRunner.CUT_OFF_BUDGET && walkable.stream().allMatch(c -> Math.max(Math.abs(c.x() - Math.floor(from.x())),
                Math.abs(c.z() - Math.floor(from.z()))) < SoldierRunner.CUT_OFF_SPAN - 1);
    }

    /**
     * How far {@code hand} is from {@code strip} of a dig, for the hands it's shared to (command.Tasking): one already
     * digging over it nearest of all - a share stays with whoever holds it - then the nearest across.
     */
    static double digReach(io.github.jayessdeesea.minecraft.command.SoldierStatus hand, Box strip) {
        if (hand.order() instanceof Order.Dig dig && dig.box().minX() <= strip.maxX() && dig.box().maxX() >= strip.minX()
                && dig.box().minZ() <= strip.maxZ() && dig.box().maxZ() >= strip.minZ() && dig.box().minY() <= strip.maxY() && dig.box().maxY() >= strip.minY()) {
            return -1;
        }
        return Math.hypot(hand.position().x() - (strip.minX() + strip.maxX()) / 2.0, hand.position().z() - (strip.minZ() + strip.maxZ()) / 2.0);
    }

    /**
     * The ground work's {@code dig} and {@code fill} shared out to {@code hands}: a hand to a strip, the nearest - its
     * holder first ({@link #digReach}) - and the hands spare of a dig narrower than the crew kept off it till it's done,
     * on their other work (operator, 2026-11-20: spare hands reallocated; back on as the dig's width changed and off again,
     * they were taken off the wall and the dig in turn eight times in the squad simulation) - unless it's stalled
     * ({@link SquadState#spareDigTill}), every hand on it then.
     */
    static java.util.Map<String, Order> shareGround(SquadState state, java.util.List<io.github.jayessdeesea.minecraft.command.SoldierStatus> hands,
                                                     io.github.jayessdeesea.minecraft.command.Cell at, int radius, Optional<Box> dig, Optional<Box> fill,
                                                     java.util.Set<io.github.jayessdeesea.minecraft.command.Cell> except, int digLeft, long now) {
        boolean stalled = state.spareDigTill(digLeft, now, hands.stream().map(io.github.jayessdeesea.minecraft.command.SoldierStatus::name).toList());
        java.util.List<io.github.jayessdeesea.minecraft.command.Tasking.Hand> crew = hands.stream().filter(h -> !state.digSpare.contains(h.name()))
                .map(h -> new io.github.jayessdeesea.minecraft.command.Tasking.Hand(h.name(),
                        h.condition().flatMap(c -> c.kit()).map(k -> k.stone() + k.dirt()).orElse(0))).toList();
        java.util.Map<String, Order> orders = stalled
                ? io.github.jayessdeesea.minecraft.command.Tasking.allocate(crew, at, radius, dig, fill, except, state.fetchingDirt, true)
                : io.github.jayessdeesea.minecraft.command.Tasking.allocate(crew, at, radius, dig, fill, except, state.fetchingDirt,
                (n, b) -> hands.stream().filter(h -> h.name().equals(n)).findFirst().map(h -> digReach(h, b)).orElse(1e9));
        crew.stream().filter(h -> digLeft > 0 && !orders.containsKey(h.name())).forEach(h -> state.digSpare.add(h.name()));
        return orders;
    }

    /** The camps the garrison has been configured with, by chest-store key: the current one first recorded, the rest moved from. */
    static final String CAMPS = "camps";
    /** How far round an old camp, across, its chests are looked for: its lit ground's reach. */
    static final int OLD_CAMP_REACH = SECURE_RADIUS;

    /**
     * The camps moved from: every camp {@code store} has seen configured - {@code camp} recorded there as it comes -
     * but those within a wall's breadth of it (operator, 2026-11-20: the camp to sea level, its chests moved).
     */
    static List<BlockPos> oldCamps(WallStore store, int[] camp) {
        if (camp == null) {
            return List.of();
        }
        java.util.List<BlockPos> camps = new java.util.ArrayList<>(store.load(CAMPS).orElse(List.of()));
        BlockPos here = new BlockPos(camp[0], 0, camp[1]);
        if (!camps.contains(here)) {
            camps.add(here);
            store.save(CAMPS, camps);
        }
        return camps.stream().filter(c -> Math.hypot(c.x() - camp[0], c.z() - camp[1]) > 2 * WALL_RADIUS).toList();
    }

    /** Every chest standing on {@code ground}'s dig - in its columns, from its floor to {@code above} over it - in a loaded column. */
    static Set<BlockPos> chestsOn(io.github.jayessdeesea.minecraft.bot.world.WorldView world, Flatten ground, int above) {
        Set<BlockPos> found = new java.util.HashSet<>();
        for (int x = ground.minX(); x <= ground.maxX(); x++) {
            for (int z = ground.minZ(); z <= ground.maxZ(); z++) {
                for (int y = ground.bottom(); world.isLoaded(x, z) && y <= ground.bottom() + above; y++) {
                    if (world.isChest(x, y, z)) {
                        found.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return found;
    }

    /** Every chest standing within {@code reach} across of each of {@code camps}, at any height of a loaded column. */
    static Set<BlockPos> chestsAround(io.github.jayessdeesea.minecraft.bot.world.WorldView world, List<BlockPos> camps, int reach) {
        Set<BlockPos> found = new java.util.HashSet<>();
        for (BlockPos c : camps) {
            for (int x = c.x() - reach; x <= c.x() + reach; x++) {
                for (int z = c.z() - reach; z <= c.z() + reach; z++) {
                    if (!world.isLoaded(x, z)) {
                        continue;
                    }
                    for (int y = OLD_CAMP_LOW; y <= OLD_CAMP_HIGH; y++) {
                        if (world.isChest(x, y, z)) {
                            found.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        return found;
    }

    /** The lowest an old camp's chest is looked for: a cellar's, not a mine's stash far under it. */
    static final int OLD_CAMP_LOW = 40;
    /** The highest: a hilltop camp's tallest hut. */
    static final int OLD_CAMP_HIGH = 160;
}
