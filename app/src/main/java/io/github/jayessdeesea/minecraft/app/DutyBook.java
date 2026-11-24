package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.command.BakerDuty;
import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.DutyOrder;
import io.github.jayessdeesea.minecraft.command.DutyYield;
import io.github.jayessdeesea.minecraft.command.FarmDuties;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ShareWatch;
import io.github.jayessdeesea.minecraft.command.WoodDuties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.ToLongFunction;

/**
 * Each producer's duty order from its squad leader (command.DutyOrder): one value a soldier has been told, in
 * place of a map per duty. The squad loop writes it, each order said when it changes - the squad leader's view
 * ("[Alpha -> Alice] DUTY: harvester over x 6..8, z -4..4, the dump at (3, 64, 2)") - and the tick reads it.
 * Thread-safe.
 */
final class DutyBook {

    private static final Logger LOG = LogManager.getLogger(DutyBook.class);

    /** A duty's yield as the errand ledger counts it: the routine that works it, the item, and whether it's spent - planted - not made. */
    record Yield(String routine, String item, boolean spent) {
    }

    /** What each duty yields: a harvester's wheat, a baker's bread, a woodcutter's logs, a planter's saplings planted. */
    static final Map<DutyOrder.Duty, Yield> YIELDS = Map.of(
            DutyOrder.Duty.HARVESTER, new Yield("FARMER", "wheat", false), DutyOrder.Duty.BAKER, new Yield("ARMOURER", "food", false),
            DutyOrder.Duty.WOODCUTTER, new Yield("GATHERER", "log", false), DutyOrder.Duty.PLANTER, new Yield("FORESTER", "sapling", true));

    private final Map<String, DutyOrder> orders = new ConcurrentHashMap<>();
    private final ShareWatch watch = new ShareWatch();
    /** Each squad's woodcutters' strips as they stand, the wood and the roster they were dealt for, and when. */
    private final Map<String, Map<String, Box>> woodStrips = new HashMap<>();
    private final Map<String, Optional<Objective>> woodFor = new HashMap<>();
    private final Map<String, List<String>> woodRoster = new HashMap<>();
    private final Map<String, Long> woodDealtAt = new HashMap<>();
    /** The squads whose wood has been dealt out whole a second time, logs standing after every strip was cut out. */
    private final Set<String> woodRedealt = new java.util.HashSet<>();
    /** The ground each soldier's gather last searched and found nothing in, while that stands. */
    private final Map<String, Box> searchedEmpty = new ConcurrentHashMap<>();
    /** How long a wood with no strip left and logs still standing waits before it's dealt out whole again. */
    static final long REDEAL_MILLIS = 5 * 60_000;
    /** The squads whose wood, on the objective they have, is spent: every strip of it cut out. */
    private final Set<String> spentSquads = ConcurrentHashMap.newKeySet();
    /** When each producer was given the ground it works. */
    private final Map<String, Long> givenAt = new ConcurrentHashMap<>();

    /**
     * {@code squad}'s {@code soldiers}' orders this cycle at {@code now}: {@code given}'s for its producers, none for the
     * rest - and, the squad on {@code production} work, the alarm on one holding without a share.
     */
    void assign(String squad, List<String> soldiers, Map<String, DutyOrder> given, Optional<String> production, long now) {
        for (String soldier : soldiers) {
            DutyOrder order = given.get(soldier);
            DutyOrder was = order == null ? orders.remove(soldier) : orders.put(soldier, order);
            if (order != null && !order.equals(was)) {
                LOG.info("[{} -> {}] DUTY: {}", squad, soldier, order.line());
            }
            if (order != null && (was == null || !order.ground().equals(was.ground()))) {
                givenAt.put(soldier, now);
            }
            if (order != null) {
                watch.shared(soldier);
            } else if (production.isPresent()) {
                watch.withoutShare(squad, soldier, production.get(), now).ifPresent(LOG::warn);
            } else {
                watch.off(soldier);
            }
        }
    }

    /**
     * {@code soldier}'s share sampled at {@code now}: what its duty has yielded, as {@code ledger} counted it, against what
     * it should in a spell (command.DutyYield) with {@code workable} in it to work - a baker's wheat stored and carried,
     * a harvester's ripe cells, a woodcutter's or planter's ground left (more than none); {@code cantco} its soldier's
     * failure at its duty now, if any. A miss is answered by its cause (command.MissAnswer): what goes up the chain is
     * a warning, the rest the squad leader's word on the net.
     */
    void sample(String squad, String soldier, ErrandLedger ledger, IntSupplier workable, Optional<String> cantco, long now) {
        DutyOrder order = orders.get(soldier);
        if (order != null) {
            Yield yield = YIELDS.get(order.duty());
            int made = yield.spent() ? ledger.spent(soldier, yield.routine(), yield.item()) : ledger.made(soldier, yield.routine(), yield.item());
            watch.sample(squad, order, new ShareWatch.Seen(made, cantco, () -> ledger.held(soldier), yield.routine()),
                    () -> DutyYield.expected(order, workable.getAsInt()), now).forEach(l -> {
                if (l.startsWith("[Alarm]") || l.contains("-> PL]")) {
                    LOG.warn(l);
                } else {
                    LOG.info(l);
                }
            });
        }
    }

    /**
     * Of {@code soldiers} on producers' duties, how many are behind their shares, for the squad's progress - the
     * platoon leader's view - and the platoon leader's alarm when half or more are (command.ShareWatch).
     */
    Optional<String> behind(String squad, List<String> soldiers, String task) {
        List<String> producers = soldiers.stream().filter(orders::containsKey).toList();
        long behind = watch.behind(producers);
        watch.squadBehind(squad, task, behind, producers.size()).ifPresent(l -> {
            if (l.startsWith("[Alarm]")) {
                LOG.warn(l);
            } else {
                LOG.info(l);
            }
        });
        // And each one idle for a cause its squad leader says up - a baker with no wheat stored: what the platoon leader is shown.
        List<String> said = new ArrayList<>(watch.idle(producers));
        if (behind > 0) {
            said.addFirst(behind + " of " + producers.size() + " producers behind their shares");
        }
        return said.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", said));
    }

    /**
     * The duty orders of {@code soldiers}, their yield to {@code dump}: the wood's {@code woodDuties}, and on the farm -
     * those {@code farming} - the shares of its plot among everyone farming it, whatever their squad: one set of shares
     * and one baker to a farm. Live (field473), Alpha's and Delta's first harvesters were both given x -31..-20 of the
     * camp's farm, each squad sharing the plot among its own.
     */
    static Map<String, DutyOrder> given(List<String> soldiers, Map<String, WoodDuties.Duty> woodDuties, Map<String, FarmPlan> farming,
                                        Optional<Position> dump) {
        return given(soldiers, woodDuties, farming, dump, Optional.empty(), Optional.empty());
    }

    /**
     * As above, the baker's makings named (operator, 2026-11-21: the soldier is how): the chest it draws its wheat from,
     * {@code wheat} - the nearest the stores book knows holding some - the table it bakes at, {@code table}, and a batch of
     * {@link io.github.jayessdeesea.minecraft.command.Subsistence#BAKE_BATCH} loaves. Live (field640-657), told only "baker,
     * the dump at", its draw was the soldier's own rule and it sat a run on 15 wheat beside chests of 544.
     */
    static Map<String, DutyOrder> given(List<String> soldiers, Map<String, WoodDuties.Duty> woodDuties, Map<String, FarmPlan> farming,
                                        Optional<Position> dump, Optional<Position> wheat, Optional<Position> table) {
        Map<String, DutyOrder> given = new HashMap<>(WoodDuties.orders(woodDuties, dump));
        soldiers.stream().filter(farming::containsKey).findFirst().ifPresent(first -> {
            List<String> onTheFarm = onOneFarm(first, farming);
            given.putAll(FarmDuties.orders(onTheFarm, onTheFarm.stream().flatMap(n -> farming.get(n).plot().stream()).distinct()
                    .map(c -> new Position(c.x(), c.y(), c.z())).toList(), dump, onTheFarm.stream().flatMap(n -> farming.get(n).chunks().stream())
                    .distinct().map(c -> new FarmDuties.Tended(c.box(), c.chestAt())).toList()));
        });
        given.replaceAll((soldier, order) -> order.duty() == DutyOrder.Duty.BAKER
                ? order.making(wheat, table, io.github.jayessdeesea.minecraft.command.Subsistence.BAKE_BATCH) : order);
        return given;
    }

    /**
     * Everyone farming the one stretch of ground {@code first} farms: the farmers whose plots share a cell with it, and
     * theirs, and so on - whatever water each plot is round. Live (field484), Alpha's and Charlie's plots round two
     * waters overlapped, and Alice and Ivy were both given x -31..-29.
     */
    static List<String> onOneFarm(String first, Map<String, FarmPlan> farming) {
        java.util.Set<String> on = new java.util.TreeSet<>(List.of(first));
        java.util.Set<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> ground = new java.util.HashSet<>(farming.get(first).plot());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, FarmPlan> e : farming.entrySet()) {
                if (!on.contains(e.getKey()) && (e.getValue().water().equals(farming.get(first).water()) || e.getValue().plot().stream().anyMatch(ground::contains))) {
                    on.add(e.getKey());
                    ground.addAll(e.getValue().plot());
                    grew = true;
                }
            }
        }
        return List.copyOf(on);
    }

    /** {@code soldier}'s gather searched {@code ground} and found nothing in it - or, none, has work or no bounds. */
    void searched(String soldier, Optional<Box> ground) {
        ground.ifPresentOrElse(g -> searchedEmpty.put(soldier, g), () -> searchedEmpty.remove(soldier));
    }

    /**
     * {@code squad}'s wood duties on {@code objective} at {@code now}, its {@code roster} in order: each woodcutter on the
     * strip it holds - dealt once, evenly - and its planter over the whole. A woodcutter whose gather has looked its own
     * strip over and found nothing ({@link #searched}) is cut out, said once, and it alone is moved: onto half of the
     * strip with the most trees standing ({@code trees}: each standing tree's x and z in a box), cut along a line
     * between them, whose woodcutter keeps the half it stands in ({@code at}) - the rest, and the trees they've begun,
     * as they were. With no strip left to share it stands by. The wood is said {@code spent} once, when no strip is left
     * and no log stands in it; with logs standing it's dealt out whole once more after {@link #REDEAL_MILLIS}, and spent
     * when that's cut out too - what's left then nobody could reach.
     * (backlog/wood-reshare.md: strips recomputed from what was cut out moved every woodcutter at each cut-out, a tree
     * half felled was left outside its feller's new strip, and the wood was said spent with logs standing.)
     */
    synchronized Map<String, WoodDuties.Duty> wood(String squad, List<String> roster, Optional<Objective> objective,
                                                   java.util.function.Function<Box, List<int[]>> trees,
                                                   java.util.function.Function<String, Optional<Position>> at,
                                                   java.util.function.Consumer<Position> spent, long now) {
        Optional<Objective> wood = objective.filter(o -> o.task().equals(new Objective.Task.Gather(io.github.jayessdeesea.minecraft.command.Resource.WOOD)));
        if (wood.isEmpty()) {
            woodStrips.remove(squad);
            woodFor.remove(squad);
            woodRoster.remove(squad);
            spentSquads.remove(squad);
            return Map.of();
        }
        Position forest = wood.get().location();
        if (!wood.equals(woodFor.get(squad))) {
            spentSquads.remove(squad);
            woodRedealt.remove(squad);
        }
        if (!wood.equals(woodFor.get(squad)) || !roster.equals(woodRoster.get(squad))) {
            woodFor.put(squad, wood);
            woodRoster.put(squad, List.copyOf(roster));
            woodStrips.put(squad, new java.util.LinkedHashMap<>(WoodDuties.strips(roster, forest)));
            woodDealtAt.put(squad, now);
        }
        Map<String, Box> strips = woodStrips.get(squad);
        for (String cutter : List.copyOf(strips.keySet())) {
            Box strip = strips.get(cutter);
            if (!strip.equals(searchedEmpty.get(cutter))) {
                continue;
            }
            strips.remove(cutter);
            searchedEmpty.remove(cutter);
            LOG.info("[{}] {}'s strip over x {}..{}, z {}..{} is cut out", squad, cutter, strip.minX(), strip.maxX(), strip.minZ(), strip.maxZ());
            String donor = null;
            List<int[]> most = List.of();
            List<Box> halves = List.of();
            for (Map.Entry<String, Box> other : strips.entrySet()) {
                List<int[]> standing = trees.apply(other.getValue());
                Optional<List<Box>> cut = standing.size() > most.size() ? WoodDuties.split(other.getValue(), standing) : Optional.empty();
                if (cut.isPresent()) {
                    donor = other.getKey();
                    most = standing;
                    halves = cut.get();
                }
            }
            if (donor == null) {
                LOG.info("[{}] no strip with trees to share: {} has none", squad, cutter);
                continue;
            }
            Box lower = halves.get(0), upper = halves.get(1);
            boolean inUpper = at.apply(donor).filter(p -> upper.contains((int) Math.floor(p.x()), upper.minY(), (int) Math.floor(p.z()))).isPresent();
            strips.put(donor, inUpper ? upper : lower);
            strips.put(cutter, inUpper ? lower : upper);
            Box given = strips.get(cutter);
            LOG.info("[{}] {} shares {}'s strip, the one with the most standing ({} trees): x {}..{}, z {}..{}", squad, cutter, donor, most.size(),
                    given.minX(), given.maxX(), given.minZ(), given.maxZ());
        }
        if (strips.isEmpty() && !spentSquads.contains(squad)) {
            int standing = trees.apply(WoodDuties.whole(forest)).size();
            if (standing > 0 && !woodRedealt.contains(squad)) {
                // Logs its woodcutters each left: looked over once more, whole, by whoever can reach them - a different try.
                if (now - woodDealtAt.getOrDefault(squad, now) >= REDEAL_MILLIS) {
                    woodRedealt.add(squad);
                    strips.putAll(WoodDuties.strips(roster, forest));
                    woodDealtAt.put(squad, now);
                    LOG.info("[{}] every strip cut out and {} tree(s) still standing: the wood dealt out whole once more", squad, standing);
                }
            } else {
                if (standing > 0) {
                    LOG.info("[{}] the wood is spent with {} tree(s) left that nobody could reach, twice over", squad, standing);
                }
                spentSquads.add(squad);
                spent.accept(forest);
            }
        }
        return WoodDuties.duties(roster, forest, strips);
    }

    /** Each standing tree's x and z in {@code box} as {@code world} has it: a column with a log in it. */
    static List<int[]> logsIn(io.github.jayessdeesea.minecraft.bot.world.WorldView world, Box box) {
        List<int[]> found = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    if (world.isLog(x, y, z)) {
                        found.add(new int[]{x, z});
                        break;
                    }
                }
            }
        }
        return found;
    }

    /** Whether {@code squad}'s wood, on the objective it has, is spent: its task done. */
    boolean spent(String squad) {
        return spentSquads.contains(squad);
    }

    /** {@code soldier}'s duty order, if it's a producer. */
    Optional<DutyOrder> of(String soldier) {
        return Optional.ofNullable(orders.get(soldier));
    }

    /**
     * Where {@code soldier}'s yield goes: its duty order's dump, a chest standing there - a chunk farm's tender's the farm's
     * own - else none, and it stockpiles as any soldier does.
     */
    Optional<Position> dump(String soldier, io.github.jayessdeesea.minecraft.bot.world.WorldView world) {
        return of(soldier).flatMap(DutyOrder::dump).filter(d -> world.isChest((int) Math.floor(d.x()), (int) Math.floor(d.y()), (int) Math.floor(d.z())));
    }

    /**
     * Whether {@code soldier} is a planter with nothing to plant: no ground in its wood opened by the felling ({@code grove}
     * none), or all of it planted - its share isn't behind, as a harvester's isn't while its plot grows.
     */
    boolean plantsNothing(String soldier, io.github.jayessdeesea.minecraft.bot.farm.TreeFarm grove, io.github.jayessdeesea.minecraft.bot.world.WorldView world) {
        return is(soldier, DutyOrder.Duty.PLANTER) && (grove == null || io.github.jayessdeesea.minecraft.bot.supply.Forester.planted(world, grove));
    }

    /** The table {@code soldier}'s order names it to work at - a baker's - as the cell it stands in. */
    Optional<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> table(String soldier) {
        return of(soldier).flatMap(DutyOrder::table).map(t -> io.github.jayessdeesea.minecraft.bot.nav.BlockPos.containing(t.x(), t.y(), t.z()));
    }

    /**
     * The workshop {@code soldier} makes its kit at: if its order names a table, the one of {@code known} that table is
     * - none if none is, and it bakes at the table or not at all - else its {@code own}.
     */
    Optional<Workshop> workshop(String soldier, Optional<Workshop> own, Workshop... known) {
        return table(soldier).map(t -> java.util.Arrays.stream(known).filter(w -> w != null && w.table().equals(t)).findFirst()).orElse(own);
    }

    /**
     * The table a baker's order names, by its squad leader's rule: the first of {@code workshops} - the camp's, then the
     * keep's - whose table stands, as the cell's centre; none with none standing, said in its order ("no table standing")
     * and up the chain on its CANTCO (command.MissAnswer). The duty orders' eval: the camp's table dug, the order re-pointed.
     */
    static Optional<Position> standingTable(io.github.jayessdeesea.minecraft.bot.world.WorldView world, Workshop... workshops) {
        return java.util.Arrays.stream(workshops).filter(w -> w != null && world.workstation(w.table().x(), w.table().y(), w.table().z())
                        .equals(Optional.of(io.github.jayessdeesea.minecraft.bot.world.Workstation.CRAFTING_TABLE)))
                .findFirst().map(w -> new Position(w.table().x() + 0.5, w.table().y(), w.table().z() + 0.5));
    }

    /** Whether {@code soldier}, carrying {@code wheat}, draws its order's: a baker short of its batch (command.BakerDuty). */
    boolean drawsWheat(String soldier, int wheat) {
        return of(soldier).filter(o -> BakerDuty.draws(o, wheat)).isPresent();
    }

    /** What {@code soldier}'s draw of wheat is made up to: its order's (command.BakerDuty), none with no baker's order. */
    int wheatDrawnTo(String soldier) {
        return of(soldier).filter(o -> o.duty() == DutyOrder.Duty.BAKER && o.batch() > 0).map(BakerDuty::drawTo).orElse(0);
    }

    /**
     * Whether {@code soldier}, carrying {@code wheat} and {@code rations}, goes and bakes: a baker by its order's batch
     * (command.BakerDuty), anyone else by the standing batch (command.Subsistence).
     */
    boolean bakeTrip(String soldier, int wheat, int rations) {
        return of(soldier).filter(o -> o.duty() == DutyOrder.Duty.BAKER).map(o -> BakerDuty.bakes(o, wheat, rations))
                .orElseGet(() -> io.github.jayessdeesea.minecraft.command.Subsistence.bakeTrip(wheat, rations));
    }

    /** Whether {@code soldier}'s order is {@code duty}. */
    boolean is(String soldier, DutyOrder.Duty duty) {
        return of(soldier).filter(o -> o.duty() == duty).isPresent();
    }

    /**
     * {@code soldier}'s field of {@code farm}: the plot's cells in its ground for a harvester, none for the baker - its
     * work is at the dump - and the whole for a farmer with no farm order. A chunk farm's tender has its chunk; the
     * other harvesters the ground outside the chunks tended, whatever their ground's box takes in.
     */
    /** The harvester whose share lies nearest {@code chunk}'s chest, by name on a tie. */
    private String nearestHarvester(ChunkFarm chunk) {
        return orders.values().stream().filter(o -> o.duty() == DutyOrder.Duty.HARVESTER && o.ground().isPresent())
                .min(java.util.Comparator.<DutyOrder>comparingDouble(o -> {
                    Box g = o.ground().get();
                    double cx = Math.max(g.minX(), Math.min(g.maxX(), chunk.chest().x())), cz = Math.max(g.minZ(), Math.min(g.maxZ(), chunk.chest().z()));
                    return Math.hypot(cx - chunk.chest().x(), cz - chunk.chest().z());
                }).thenComparing(DutyOrder::soldier)).map(DutyOrder::soldier).orElse("");
    }

    FarmPlan field(String soldier, FarmPlan farm) {
        DutyOrder order = orders.get(soldier);
        if (farm == null || order == null || !order.farm()) {
            return farm;
        }
        Set<Position> chests = orders.values().stream().flatMap(o -> o.dump().stream()).collect(java.util.stream.Collectors.toSet());
        List<ChunkFarm> tended = farm.chunks().stream().filter(c -> chests.contains(c.chestAt())).toList();
        boolean tender = order.dump().filter(d -> tended.stream().anyMatch(c -> c.chestAt().equals(d))).isPresent();
        java.util.function.Predicate<io.github.jayessdeesea.minecraft.bot.nav.BlockPos> mine = c -> order.ground().filter(g -> g.contains(c.x(), c.y(), c.z())).isPresent()
                && (tender || tended.stream().noneMatch(t -> t.box().contains(c.x(), c.y(), c.z())));
        // A chunk farm no tender has and no share holds the chest of - its ground not level yet, off every strip - is the
        // nearest harvester's: live (field516-544), the camp's chunk farm lay a day off all the strips, nobody's, SURVEYED.
        java.util.function.Predicate<ChunkFarm> orphan = c -> !tended.contains(c) && orders.values().stream()
                .noneMatch(o -> o.farm() && o.ground().filter(g -> g.contains(c.chest().x(), c.chest().y(), c.chest().z())).isPresent());
        return order.duty() == DutyOrder.Duty.BAKER ? null
                : new FarmPlan(farm.water(), farm.plot().stream().filter(mine).toList(), farm.trenches().stream().filter(mine).toList(),
                farm.chunks().stream().filter(c -> tender ? order.dump().get().equals(c.chestAt())
                        : !tended.contains(c) && mine.test(c.chest()) || orphan.test(c) && soldier.equals(nearestHarvester(c))).toList());
    }
}
