package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Forester;
import io.github.jayessdeesea.minecraft.bot.world.WorldModel;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Supply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The keep's wood party: who cuts trees for the depot and who plants its grove, and where they cut. One per
 * platoon, over the platoon's {@link KeepState}; the decision is {@link PlatoonSergeant#woodParty}'s, this
 * feeds it what the builders carry, the stockpile and the trees seen, and says the change on the net. It takes
 * no lock of its own: {@link #woodParty} is called with {@link FlattenWork#planLock} held, the lock that guards
 * the party it writes.
 */
final class KeepWood {

    /** Where a line said on the net goes: the runner queues it on the speaker's connection. */
    @FunctionalInterface
    interface Net {
        void say(String from, String to, String line);
    }

    /** Logged as the runner's, so the lines read as they always have. */
    private static final Logger LOG = LogManager.getLogger(SoldierRunner.class);

    private final KeepState keepState;
    private final Map<String, Forester> foresters;
    private final Supplier<Supply> logsSupply;
    private final ChainOfCommand chainOfCommand;
    private final WorldModel world;
    private final TreeFelling trees;
    private final FlattenWorks flattenWorks;
    private final LongSupplier clock;
    private final Predicate<Position> buried;
    private final Net net;

    KeepWood(KeepState keepState, Map<String, Forester> foresters, Supplier<Supply> logsSupply, ChainOfCommand chainOfCommand,
             WorldModel world, TreeFelling trees, FlattenWorks flattenWorks, LongSupplier clock, Predicate<Position> buried, Net net) {
        this.keepState = keepState;
        this.foresters = foresters;
        this.logsSupply = logsSupply;
        this.chainOfCommand = chainOfCommand;
        this.world = world;
        this.trees = trees;
        this.flattenWorks = flattenWorks;
        this.clock = clock;
        this.buried = buried;
        this.net = net;
    }

    /** How far from the keep's depot its wood party goes for trees: the ground the keep's squads work. */
    static final int KEEP_WOOD_REACH = 64;
    /** How long the keep's grove waits after a planting fails - no sapling, no leaves, no way to the cell. */
    static final long PLANTER_RETRY_MILLIS = 5 * 60_000;

    /**
     * The keep's wood party, as the platoon sergeant decides it ({@link PlatoonSergeant#woodParty}):
     * fed what the builders carry, the stockpile's logs, the trees the keep's squads have seen and
     * the grove's state. A planting that fails pauses the grove {@link #PLANTER_RETRY_MILLIS}, not
     * the planter - what failed it fails the next builder too: live, five took the job in turn within
     * a minute. Changes are logged and said on the net. Guarded by {@link FlattenWork#planLock}.
     */
    void woodParty(List<String> onKeep, List<LevelPlan.Builder> onHand, long now) {
        Optional<String> plantingFailed = keepState.party.planter().map(foresters::get).flatMap(Forester::failure);
        if (plantingFailed.isPresent()) {
            LOG.info("[Keep] the grove waits {} min: {}", PLANTER_RETRY_MILLIS / 60_000, plantingFailed.get());
            keepState.groveBarredUntil = now + PLANTER_RETRY_MILLIS;
        }
        List<PlatoonSergeant.Hand> crew = onHand.stream().map(b -> new PlatoonSergeant.Hand(b.name(),
                keepState.logsCarried.getOrDefault(b.name(), 0), keepState.saplingsCarried.getOrDefault(b.name(), 0))).toList();
        TreeFarm grove = keepState.grove;
        Supply logsMeasured = logsSupply.get();
        PlatoonSergeant.Wood wood = new PlatoonSergeant.Wood(chainOfCommand.stockpile().map(Stockpile::logs).orElse(0),
                // Or a tree the world model holds near the depot: live (field389), the keep's squads came up
                // from a night in the mine having seen no trees, and nobody cut at 3 logs.
                treesSeenLately(onKeep.stream().flatMap(u -> chainOfCommand.resourcesSeen(u).stream()).anyMatch(r -> r.resource() == Resource.WOOD)
                        || knownTree(now).isPresent(), now)
                        || grove == null && keepState.groveToClear != null, // the grove's ground to clear is trees seen
                grove != null && !Forester.planted(world, grove), now < keepState.groveBarredUntil,
                logsMeasured == null ? 0 : logsMeasured.reorderPoint(), logsMeasured == null ? 0 : logsMeasured.objective());
        PlatoonSergeant.WoodParty was = keepState.party;
        PlatoonSergeant.WoodParty party = PlatoonSergeant.woodParty(crew, flattenWorks.first().foreman, was, wood);
        if (!party.cutters().equals(was.cutters()) || !party.planter().equals(was.planter())) {
            int logs = crew.stream().mapToInt(PlatoonSergeant.Hand::logs).sum() + wood.stockpileLogs();
            LOG.info("[Keep] the wood party: {} cut(s) trees for the depot ({} logs between the builders and the stockpile, {} wanted){}",
                    party.cutters().isEmpty() ? "nobody" : party.cutters(), logs, (int) wood.back(),
                    party.planter().map(n -> ", " + n + " plants the keep's grove").orElse(""));
            String chief = flattenWorks.first().foreman;
            if (chief != null) {
                party.cutters().stream().filter(n -> !was.cutters().contains(n)).forEach(n -> net.say(chief, n, "KEEP WOOD " + PlatoonSergeant.WOOD_LOAD));
                party.planter().filter(n -> !was.planter().equals(Optional.of(n)))
                        .ifPresent(n -> net.say(chief, n, "KEEP PLANT " + grove.centre().x() + " " + grove.centre().z()));
            }
        }
        keepState.party = party;
        // Off the party, its tree is let go of, its patience with it too: a tree kept through a night down the
        // mine would be given up as out of reach at dawn, its patience run out while the cutter was far under it.
        trees.retainCutters(party.cutters());
        // Nobody cutting under the reserve says why, once a minute: live (field390), a day went by at 3-19
        // logs with the party never out and no line to say what held it.
        int logsNow = crew.stream().mapToInt(PlatoonSergeant.Hand::logs).sum() + wood.stockpileLogs();
        if (party.cutters().isEmpty() && logsNow < wood.out() && now - keepState.woodWhyAt >= WOOD_WHY_MILLIS) {
            keepState.woodWhyAt = now;
            LOG.warn("[Keep] nobody cuts at {} logs, under the reserve of {}: crew {}, trees seen lately {}, a known tree {}, the grove's ground to clear {}",
                    logsNow, (int) wood.out(), crew.size(), now - keepState.treesSeenAt <= TREES_SEEN_MILLIS,
                    knownTree(now).map(Position::toString).orElse("none within " + KEEP_WOOD_REACH), keepState.groveToClear);
        }
    }

    static final long WOOD_WHY_MILLIS = 60_000;

    /** How long trees once seen are counted seen: a survey that misses them doesn't stand the wood party down. */
    static final long TREES_SEEN_MILLIS = 120_000;

    /**
     * Whether trees are seen now, or were within {@link #TREES_SEEN_MILLIS}: live (field374), Ivy was put
     * on the wood party, off it 21 s later ("nobody cut(s) trees" at 5 logs) and on it again 30 s after,
     * each a survey that did or didn't see the trees, her rectangle handed back and forth with Bob.
     */
    private boolean treesSeenLately(boolean seenNow, long now) {
        if (seenNow) { keepState.treesSeenAt = now; }
        return now - keepState.treesSeenAt <= TREES_SEEN_MILLIS;
    }

    /** Whether {@code name} is off the dig in the keep's wood party. */
    boolean inWoodParty(String name) {
        return keepState.party.has(name);
    }

    /**
     * Where the wood party cuts: the ground being cleared for the keep's grove, while it has none; else
     * the trees nearest the depot a keep squad last saw, else round the grove, else the depot.
     */
    Position keepWoodAt(List<String> onKeep, TreeFarm grove) {
        BlockPos clearing = keepState.groveToClear;
        if (grove == null && clearing != null) {
            return new Position(clearing.x() + 0.5, clearing.y(), clearing.z() + 0.5);
        }
        // Trees on the surface near the depot only: live (field377), Ivy and Liam were sent 120 blocks off to
        // "trees" at y=-28 - a mineshaft's timbers a mining squad's survey saw - never a surface soldier
        // routed down into a cave (backlog/long-routes.md).
        Position depot = chainOfCommand.flatten().depot();
        return onKeep.stream().flatMap(u -> chainOfCommand.resourcesSeen(u).stream())
                .filter(site -> site.resource() == Resource.WOOD).map(ResourceSite::location)
                .filter(l -> !buried.test(l) && Math.hypot(l.x() - depot.x(), l.z() - depot.z()) <= KEEP_WOOD_REACH)
                .min(Comparator.comparingDouble(l -> Math.hypot(l.x() - chainOfCommand.flatten().depot().x(), l.z() - chainOfCommand.flatten().depot().z())))
                .or(() -> knownTree(clock.getAsLong()))
                .orElseGet(() -> grove != null ? new Position(grove.centre().x() + 0.5, grove.centre().y(), grove.centre().z() + 0.5)
                        : chainOfCommand.flatten().depot());
    }

    /**
     * The tree nearest the depot the world model holds, within {@link #KEEP_WOOD_REACH} across: a column
     * whose top is a log, under leaves or open sky - on the surface, never a mineshaft's timbers. Looked
     * for again every {@link #KNOWN_TREE_MILLIS}.
     */
    private Optional<Position> knownTree(long now) {
        if (now - flattenWorks.first().knownTreeAt >= KNOWN_TREE_MILLIS) {
            flattenWorks.first().knownTreeAt = now;
            Position depot = chainOfCommand.flatten().depot();
            int dx = (int) Math.floor(depot.x()), dy = (int) Math.floor(depot.y()), dz = (int) Math.floor(depot.z());
            Position best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = dx - KEEP_WOOD_REACH; x <= dx + KEEP_WOOD_REACH; x++) {
                for (int z = dz - KEEP_WOOD_REACH; z <= dz + KEEP_WOOD_REACH; z++) {
                    double d = Math.hypot(x - dx, z - dz);
                    if (d > KEEP_WOOD_REACH || d >= bestDistance) {
                        continue;
                    }
                    for (int y = dy + KNOWN_TREE_ABOVE; y >= dy - KNOWN_TREE_BELOW; y--) {
                        if (world.isLog(x, y, z)) {
                            if (!trees.letGo(new BlockPos(x, y, z))) {
                                best = new Position(x + 0.5, y, z + 0.5);
                                bestDistance = d;
                            }
                            break;
                        }
                        if (world.isSolid(x, y, z) && world.leaves(x, y, z).isEmpty()) {
                            break; // the ground: no tree on this column
                        }
                    }
                }
            }
            flattenWorks.first().knownTree = best;
        }
        return Optional.ofNullable(flattenWorks.first().knownTree);
    }

    static final long KNOWN_TREE_MILLIS = 10_000;
    /** How far over and under the depot's stand a known tree's column is looked down. */
    static final int KNOWN_TREE_ABOVE = 24;
    static final int KNOWN_TREE_BELOW = 8;

}
