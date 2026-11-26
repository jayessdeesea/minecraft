package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.AccessRoute;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.Hardness;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Cell;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Groundwork;
import io.github.jayessdeesea.minecraft.command.LevelPace;
import io.github.jayessdeesea.minecraft.command.LevelPlan;
import io.github.jayessdeesea.minecraft.command.MissionEstimate;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.Supply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One ground's flatten work: the foreman's plan for the level under way and its crews, the haul road, the workshop and
 * its outposts, the chests, the dig's measures and the chests condemned on it. The keep's, held per ground once several
 * are worked (backlog/named-work.md); the platoon's wood party and each builder's fights, fetches and draws stay in
 * {@link KeepState}. Each field says who writes it and under what lock; {@link #planLock} guards the plan and, by
 * the same object, the wood party. A holder only: what's done with it is still {@link SoldierRunner}'s.
 */
final class FlattenWork {

    /** Logged as the runner's: the lines are the keep's, and the simulations grep them by that name. */
    private static final Logger LOG = LogManager.getLogger(SoldierRunner.class);

    /** How often the haul road is planned again: the floor it leads to goes down as the keep is dug. */
    static final long ROAD_PLAN_MILLIS = 30_000;
    /** Raw ore in the chests worth a smelter: a chestplate's copper. */
    static final int SMELT_BATCH = 8;
    /** How recently a fetcher must have found nothing for its squad's pit to move on. */
    static final long PIT_MOVE_MILLIS = 60_000;
    /** How near its pit a fetcher must be for its finding nothing to move the pit on. */
    static final double PIT_NEAR = 32;
    /** The sides of the keep a share's borrow pit goes off, in turn - 0 east, 2 south, 3 north; never 1, west, the depot's. */
    private static final int[] PIT_SIDES_FIRST = {3, 0, 2}, PIT_SIDES_SECOND = {0, 2, 3};

    /** The foreman's plan for a level: who's on it, their rectangles, and when it was made. */
    record Plan(int level, Set<String> crew, Map<String, List<Box>> boxes, long at) {
    }

    /** The keep's work left as measured, for its rate - the main loop's. */
    final Supply.Burn workBurn = new Supply.Burn(SoldierRunner.DAY_MILLIS);
    /** The keep's estimate as last worked out, and whether it was feasible when last logged - the main loop's. */
    volatile MissionEstimate estimate;
    Boolean feasible;

    /** Ore on the keep's site no builder's pickaxe gets the drop from, by squad - each squad's loop writes its own. */
    final Map<String, Integer> oreAsideBySquad = new ConcurrentHashMap<>();

    /** The keep's workshop, at its depot, shared by every squad building it - squad loop only. */
    volatile Workshop workshop;
    /**
     * The flatten's other depots, along its edge (Flatten.depots): where its builders put their
     * loads in, the nearest of them or the main one - squad loops write, ticks read. Draws are from
     * the main depot, whose chests hold the stores.
     */
    volatile List<Workshop> outposts = List.of();

    /** The depot ground last said to have nowhere for the workshop, so it's said once - squad loops. */
    volatile BlockPos workshopNowhere;

    /** The cells of the keep's depot's chests, in the order they're filled - squad loops write, ticks read. */
    volatile List<BlockPos> chests = List.of();
    /** Which of {@link #chests} is being filled. */
    volatile int chestAt;

    /** The keep's haul road as last planned, when, and who cuts it - guarded by {@link #planLock}. */
    volatile AccessRoute road;
    long roadAt = Long.MIN_VALUE / 2;
    volatile String roadCutter;

    /** Each squad's hands free for the haul road, by squad - the squad loop's. */
    final Map<String, Map<String, Position>> roadHands = new ConcurrentHashMap<>();

    /** Each squad's hands on the keep and where they stand, as its last keep cycle saw them - the squad loop's. */
    final Map<String, Map<String, Position>> handsAt = new ConcurrentHashMap<>();

    /** Soldiers whose dig or fill is the keep's, not the camp's - squad loop writes, tick reads. */
    final Set<String> hands = ConcurrentHashMap.newKeySet();

    /**
     * When each builder's rectangles on the level under way are due done, and only while they're not:
     * a builder dealt in with none here is done - written under {@link #planLock}, read by ticks.
     */
    final Map<String, Long> due = new ConcurrentHashMap<>();

    /** Each of the ground's columns' top as last seen loaded, by packed x and z - squad loops, under its own lock. */
    final Map<Long, Integer> columnTops = new HashMap<>();
    /** Ore in the dig whose rectangle's holder can't take it, by squad as each last saw it: the ore errand's too. */
    final Map<String, Set<Cell>> oreBeyondHolder = new ConcurrentHashMap<>();
    /** The builders given work on the level under way, done or not - guarded by {@link #planLock}. */
    final Set<String> dealt = new HashSet<>();

    /** The keep's smelter, if one is detailed - squad loops write, ticks read. */
    volatile String smelter;

    /** The tree {@link #knownTree} last found, and when it looked - squad loops only. */
    volatile Position knownTree;
    volatile long knownTreeAt = Long.MIN_VALUE / 2;

    /** Each keep builder's digs on the level under way (command.LevelPace) - ticks write, squad loops read and clear. */
    final Map<String, LevelPace.Tally> pace = new ConcurrentHashMap<>();
    /**
     * Each keep builder's ticks on the level under way, by what held them - the routine holding its
     * hands, or waiting with no rectangle (command.LevelPace) - its own tick writes, squad loops take.
     */
    final Map<String, Map<String, Integer>> spent = new ConcurrentHashMap<>();
    /** The keep's dig so far, level on level - guarded by {@link #planLock}. */
    LevelPace.Run run = LevelPace.Run.NONE;
    /** When the level under way was first planned, the nights since left out - guarded by {@link #planLock}. */
    long levelAt;
    /** Since when it's been dark over the level under way, or {@code Long.MIN_VALUE} by day - guarded by {@link #planLock}. */
    long darkSince = Long.MIN_VALUE;

    /** The keep's foreman, by name - squad loops. */
    volatile String foreman;
    /** The foreman's plan for the site's level - guarded by {@link #planLock}; read by every squad's loop. */
    volatile Plan plan;
    final Object planLock = new Object();
    /** Each keep squad's builders on hand - on the keep, fed, not fighting - and where they stand: squad loops. */
    final Map<String, List<LevelPlan.Builder>> crews = new ConcurrentHashMap<>();

    /** Each keep squad's cells still to fill, -1 while it's still digging - squad loops write, the site measure reads. */
    final Map<String, Integer> fillLeftBySquad = new ConcurrentHashMap<>();

    /** Each keep squad's blocks still to dig in its share, at its last look - squad loops write, ticks read. */
    final Map<String, Integer> digLeftBySquad = new ConcurrentHashMap<>();

    /** The site's columns' tops in the keep's last scan, for a rise's line: the keep's squad loop writes and reads. */
    volatile Map<Long, Integer> lastScanTops = Map.of();

    /** Chests standing on the ground to flatten, to salvage before they're dug - keep squad loops write, ticks read. */
    volatile Set<BlockPos> condemned = Set.of();
    /** The keep hand salvaging them; null with none - keep squad loops write, ticks read. */
    volatile String salvager;
    /** When the ground to flatten was last looked over for chests, and the site it was. */
    volatile long condemnedAt = Long.MIN_VALUE;
    volatile Flatten condemnedFor;

    /** The keep's ore set aside: the whole site's, so every squad's count is the same; the largest. */
    int oreAside() {
        return oreAsideBySquad.values().stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** The blocks still to dig in every squad's share, at each's last look. */
    int digLeft() {
        return digLeftBySquad.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Whether any squad has got to the fill: its count is -1 while it's still digging. */
    boolean fillKnown() {
        return fillLeftBySquad.values().stream().anyMatch(n -> n >= 0);
    }

    /** The cells still to fill in the shares that have got to it. */
    int fillLeft() {
        return fillLeftBySquad.values().stream().mapToInt(Integer::intValue).filter(n -> n >= 0).sum();
    }

    /** The builders on hand, every squad's. */
    int crewSize() {
        return crews.values().stream().mapToInt(List::size).sum();
    }

    /** {@code full}'s chest is full: the depot's next chest goes up, if it's the one being filled and there's another in its row. */
    void chestFull(Position full) {
        synchronized (planLock) {
            List<BlockPos> cells = chests;
            int at = chestAt;
            if (at + 1 < cells.size() && cells.get(at).equals(BlockPos.containing(full.x(), full.y(), full.z()))) {
                chestAt = at + 1;
                LOG.info("[Keep] the depot's chest at {} is full - the next goes up at {} ({} of {})", cells.get(at), cells.get(at + 1), at + 2,
                        cells.size());
            }
        }
    }

    /**
     * The keep's haul road: planned from the depot's stand to a standing cell on the keep's floor
     * ({@link AccessRoute}), out of the keep's dig till its last cell, every {@link #ROAD_PLAN_MILLIS};
     * its cutter the builder on hand nearest its start while any of it is left to cut, nobody once
     * it's cut. Said when it changes. Run under {@link #planLock}.
     */
    void haulRoad(List<LevelPlan.Builder> onHand, long now, WorldView world, Supplier<Flatten> flatten, Predicate<String> inWoodParty) {
        Workshop depot = workshop;
        if (depot == null || onHand.isEmpty()) {
            roadCutter = null;
            return;
        }
        if (now - roadAt >= ROAD_PLAN_MILLIS || road == null) {
            roadAt = now;
            Flatten ground = flatten.get();
            Optional<AccessRoute> planned = AccessRoute.plan(world, depot.stand(), c -> ground.contains(c.x(), c.z()) && Pathfinder.standable(world, c), KeptGround.site(ground));
            AccessRoute was = road;
            road = planned.orElse(null);
            if (planned.isEmpty() && was != null) {
                LOG.warn("[Keep] no haul road from the depot at {} to the keep's floor within reach of the search", depot.stand());
            } else if (planned.isPresent() && (was == null || !was.feet().equals(planned.get().feet()))) {
                LOG.info("[Keep] the haul road: {} steps from the depot at {} to {}, {} block(s) to cut, {} floor(s) to put down",
                        planned.get().feet().size(), depot.stand(), planned.get().feet().getLast(), planned.get().toCut().size(),
                        planned.get().floors().size());
            }
        }
        AccessRoute planRoad = road;
        // Kept by the one cutting it while it's on hand: picked afresh as the nearest each cycle, the
        // cutter changed every second or two as the builders moved, each change was a new crew for
        // the level, and the level was shared out again every second (live: Carol, Gus, Hal, Carol...).
        String was = roadCutter;
        String cutter = planRoad == null || planRoad.toCut().isEmpty() ? null
                : was != null && onHand.stream().anyMatch(b -> b.name().equals(was)) && !inWoodParty.test(was) ? was
                : onHand.stream().filter(b -> !inWoodParty.test(b.name()))
                .min(Comparator.comparingDouble(b -> Math.hypot(b.at().x() - planRoad.feet().getFirst().x(), b.at().z() - planRoad.feet().getFirst().z())))
                .map(LevelPlan.Builder::name).orElse(null);
        if (!Objects.equals(cutter, roadCutter) && cutter != null) {
            LOG.info("[Keep] {} cuts the haul road: {} block(s) left", cutter, planRoad.toCut().size());
        }
        roadCutter = cutter;
    }

    /**
     * Each of the ground's columns' top as the world has it where it's loaded, into {@code tops}; where its chunk
     * isn't, its last known top stands for it, or the level drops when the chunk goes and comes back when it's seen
     * again. Live, the big site's east edge is the first column of a chunk mostly off it, loaded and let go as
     * builders moved, and the level went 86, 87, 86 a minute, dealt out again each time.
     */
    void refreshColumnTops(WorldView world, Flatten ground, Map<Long, Integer> tops) {
        synchronized (columnTops) {
            for (int x = ground.minX() - Flatten.BENCH; x <= ground.maxX() + Flatten.BENCH; x++) {
                for (int z = ground.minZ() - Flatten.BENCH; z <= ground.maxZ() + Flatten.BENCH; z++) {
                    long key = ((long) x << 32) ^ (z & 0xffffffffL);
                    if (world.isLoaded(x, z)) {
                        Integer now = tops.get(key);
                        if (now == null) {
                            columnTops.remove(key);
                        } else {
                            columnTops.put(key, now);
                        }
                    } else {
                        Integer known = columnTops.get(key);
                        if (known != null) {
                            tops.merge(key, known, Math::max);
                        }
                    }
                }
            }
        }
    }

    /**
     * Where {@code share}'s borrow pit is when it has moved on {@code shift} times: x and z, off one of the keep's sides
     * in turn. Off each side's own edge, halfway along it: a long site's pit is beside it, not its length away - live, a
     * pit put past a 129-long site's half-span from its middle sent a builder 72 blocks east - and past the bench, so a
     * pit never holes the slope (Flatten.BENCH).
     */
    static Pit pitAt(Flatten ground, int share, int shift) {
        int[] sides = share == 0 ? PIT_SIDES_FIRST : PIT_SIDES_SECOND;
        int side = sides[Math.floorMod(shift, sides.length)];
        Position mid = ground.centre();
        int midX = (int) Math.floor(mid.x()), midZ = (int) Math.floor(mid.z());
        int halfX = (ground.maxX() - ground.minX()) / 2 + 1, halfZ = (ground.maxZ() - ground.minZ()) / 2 + 1;
        int sideHalf = side == 0 || side == 1 ? halfX : halfZ;
        int edge = sideHalf + Flatten.BENCH + Groundwork.BORROW_GAP + 3;
        return new Pit(midX + (side == 0 ? edge : side == 1 ? -edge : 0), midZ + (side == 2 ? edge : side == 3 ? -edge : 0),
                midX, midZ, sideHalf, side);
    }

    /** A borrow pit's place ({@code x}, {@code z}), and what it was found from: the ground's middle, its half-length on that side, the side. */
    record Pit(int x, int z, int midX, int midZ, int sideHalf, int side) {
    }

    /**
     * Whether the pit at {@code pitX}, {@code pitZ} is dug out: not within {@link #PIT_MOVE_MILLIS} of its last move
     * ({@code movedAt}), and a fetcher at it has found nothing since that move and within that time.
     */
    static boolean pitDugOut(long now, long movedAt, List<SoldierStatus> hands, Set<String> fetching,
                             Map<String, Long> gatherFoundNothing, int pitX, int pitZ) {
        return now - movedAt >= PIT_MOVE_MILLIS
                && hands.stream().filter(h -> fetching.contains(h.name()))
                .filter(h -> Math.hypot(h.position().x() - pitX, h.position().z() - pitZ) <= PIT_NEAR)
                .map(SoldierStatus::name)
                .anyMatch(n -> now - gatherFoundNothing.getOrDefault(n, Long.MIN_VALUE / 2) < PIT_MOVE_MILLIS
                        && gatherFoundNothing.getOrDefault(n, Long.MIN_VALUE / 2) > movedAt);
    }

    /**
     * The set-aside ore {@code hand} is to take: at or over {@code level}, its drop got with
     * {@code hand}'s pickaxe, and {@code hand} the nearest of the keep's hands - every squad's - who
     * could get it. Empty for one with none.
     */
    Optional<Cell> oreErrand(String hand, int level, WorldView world, Map<String, Integer> pickaxeTiers, Collection<SquadState> squads) {
        Map<String, Position> on = new HashMap<>();
        handsAt.values().forEach(on::putAll);
        if (!on.containsKey(hand) || level == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        Set<Cell> aside = new HashSet<>();
        squads.forEach(s -> aside.addAll(s.keepOreAside));
        oreBeyondHolder.values().forEach(aside::addAll);
        // Only ore still there: one taken, or gone with its pillar, isn't an errand. Live (field427), a cell at
        // y=87 over the level at y=62 was dealt to Alice, Gus and Bob in turn 310 times in five minutes, each
        // finding nothing to cut there, and the keep's crew stood.
        return aside.stream().filter(c -> c.y() >= level - 1 && world.isOre(c.x(), c.y(), c.z())).filter(c -> {
                    Optional<Hardness> hardness = world.hardness(c.x(), c.y(), c.z());
                    Predicate<String> can = n -> hardness.map(hd -> hd.drops(pickaxeTiers.getOrDefault(n, Hardness.HAND))).orElse(false);
                    if (!can.test(hand)) {
                        return false;
                    }
                    // The nearest who can.
                    return on.entrySet().stream().filter(e -> can.test(e.getKey()))
                            .min(Comparator.comparingDouble(e -> Math.hypot(e.getValue().x() - c.x(), e.getValue().z() - c.z())))
                            .map(e -> e.getKey().equals(hand)).orElse(false);
                })
                .min(Comparator.comparingDouble(c -> Math.hypot(on.get(hand).x() - c.x(), on.get(hand).z() - c.z())));
    }

    /**
     * The keep's smelter (backlog/common-stockpile.md, the ore's chain of hands: the smelter): one
     * builder off the dig while the surface chests hold raw ore ({@code raw}) - drawn from whichever holds it,
     * smelted at the main depot's furnace, the ingots put back - so armour needs a table and a chest's
     * ingots, not a furnace and fuel where each soldier stands. Live (field302), the farm squad walked
     * between a table with no furnace by it, a furnace 22 blocks off and a table 50 off, for copper
     * leggings never made, with 74 raw copper in chests. Of the crew, one with no rectangle, nearest
     * the main depot; kept while there's ore to smelt or it carries some ({@code carries}).
     */
    String chooseSmelter(List<LevelPlan.Builder> onHand, Plan plan, int raw, Set<String> carries, Predicate<String> inWoodParty) {
        Workshop depot = workshop;
        String was = smelter;
        boolean onCrew = was != null && onHand.stream().anyMatch(b -> b.name().equals(was));
        if (onCrew && (raw > 0 || carries.contains(was))) {
            return was;
        }
        if (depot == null || raw < SMELT_BATCH) {
            if (was != null) {
                LOG.info("[Keep] {} is done smelting - back on the dig", was);
            }
            return null;
        }
        Optional<String> chosen = onHand.stream().filter(b -> plan == null || !plan.boxes().containsKey(b.name()))
                .filter(b -> !inWoodParty.test(b.name()) && !b.name().equals(roadCutter))
                .min(Comparator.comparingDouble(b -> Math.hypot(b.at().x() - depot.stand().x(), b.at().z() - depot.stand().z())))
                .map(LevelPlan.Builder::name);
        chosen.filter(n -> !n.equals(was)).ifPresent(n -> LOG.info("[Keep] {} is the smelter: {} raw ore in the chests", n, raw));
        return chosen.orElse(null);
    }
}
