package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * One soldier keeping its squad's wheat farm ({@link FarmPlan}): each tick it does the most
 * pressing job left, one cell at a time, in this order -
 * <ol>
 *   <li>with a bucket, its water holes ({@link FarmPlan#holes}): each dug, then filled at a pond ({@link WaterCarrier});</li>
 *   <li>its trenches cut ({@link #trenchToCut}), out from the pond, for its water to flow along;</li>
 *   <li>its earthwork ({@link #earthwork}): a block put down on the plot dug off, and the ground round
 *   it cut down to the plot's level, so every cell is walked onto on the flat;</li>
 *   <li>its chunk farms' fixtures ({@link #fixtures}), with what it carries: the fence, the gates - opened, the
 *   farm's way in - and the chest;</li>
 *   <li>harvest ripe wheat - wheat, and seeds to plant again;</li>
 *   <li>plant seeds on farmland with nothing growing;</li>
 *   <li>till the plot's ground into farmland, with a hoe;</li>
 *   <li>with no seeds and ground still to plant, break grass nearby for them - one in eight drops some.</li>
 * </ol>
 * The hoe, and bread from the wheat, are the armourer's to make; this tends the ground.
 *
 * <p>Each job is a short expect script (docs/architecture.md § Behavior modeling): the soldier stands on the
 * cell, the item is in hand when the server's resent inventory says so, and the job is done when
 * the server's block update says so - the ground is farmland, the seed has sprouted. Soldiers
 * working one farm share {@code claims}, so each takes a different cell. A job whose answer
 * doesn't come is a fault: it says why ({@link #failure}) and waits before the next.
 */
public final class Farmer {

    private static final Logger LOG = LogManager.getLogger(Farmer.class);

    static final int WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    static final int COOLDOWN_TICKS = 200;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;
    /** The squad's steps and pillars - a live view the runner keeps - which the earthwork leaves standing. */
    private final Set<BlockPos> steps;

    private Digger digger;
    private Script fieldWork;
    /** Fills the farm's holes with water, by bucket. */
    private final WaterCarrier carrier;
    /** The hole it's filling. */
    private BlockPos watering;
    /** What a chunk farm's put up with, in the order it's put up: its fence, its gates, its chest. */
    static final List<ItemClass> FIXTURES = List.of(ItemClass.FENCE, ItemClass.FENCE_GATE, ItemClass.CHEST, ItemClass.TORCH);
    /** Puts up each kind of fixture. */
    private final Map<ItemClass, Builder> builders = new EnumMap<>(ItemClass.class);
    /** What lays a water hole's floor, of the cobblestone or dirt it carries. */
    private final Builder holeFloor;
    /** Fills a chunk farm's ground where it's missing (FarmSite, the SURVEYED stage). */
    private final Builder groundFill;
    /** The kind of fixture it's putting up. */
    private ItemClass placing;
    /** Does the farm's {@link #earthwork}: a {@link Clearer} over it. */
    private final Clearer unburier;
    private BlockPos claimed;
    private Position standAt;
    private Position soldier;
    private int cooldown;
    private String failure;

    public Farmer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this(name, session, inventory, claims, new Setbacks<>(PASS_OVER_TICKS, 16L * PASS_OVER_TICKS));
    }

    /**
     * As {@link #Farmer(String, PlaySession, Inventory, Set)}, with the cells squadmates working the
     * same farm couldn't work, {@code shared}: one out of one soldier's reach is passed over by all
     * of them - live, four soldiers each found the same unreachable wheat for themselves.
     */
    public Farmer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Setbacks<BlockPos> shared) {
        this(name, session, inventory, claims, shared, Set.of());
    }

    /**
     * As above, with {@code steps} the squad's own standing steps and pillars, which its earthwork
     * leaves: in the squad simulation a farmer dug the step out of its hollow as dirt over the plot,
     * its route built the step again, and each jump up it that fell back trampled the farmland.
     */
    public Farmer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Setbacks<BlockPos> shared,
                  Set<BlockPos> steps) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.shared = shared;
        this.steps = steps;
        this.carrier = new WaterCarrier(name, session, inventory);
        FIXTURES.forEach(item -> builders.put(item, new Builder(name, session, inventory, List.of(item))));
        this.holeFloor = new Builder(name, session, inventory, List.of(ItemClass.COBBLESTONE, ItemClass.DIRT));
        this.groundFill = new Builder(name, session, inventory, List.of(ItemClass.DIRT, ItemClass.COBBLESTONE));
        // Cut from off the farmland: in the squad simulation, stands on the plot in its hollow had the
        // farmers walking in and out over it, trampling it, to dig the ground round it. But from it when no stand off it
        // reaches (onFarmland): live (field576), a pillar two high on a plot cell, farmland all round, was never cut.
        this.unburier = new Clearer(name, session, inventory, claims, (world, stand) -> onFarmland || !world.isFarmland(stand.x(), stand.y() - 1, stand.z()));
    }

    /**
     * The blocks to dig so {@code farm}'s plot is open and walked onto on the flat: the block over
     * each cell that's cobblestone or soil, and the soil over the ground beside each, up to two
     * blocks, where that ground is level with the plot - an apron round it. Live, a third of the
     * camp's farm lay under cobblestone and dirt the soldiers had put down on it, steps built out of
     * its hollow, the farmland under each turned to dirt; and the rest, a block below the ground
     * round it, was reached only by stepping down onto it, which tramples farmland.
     */
    /** {@link #earthwork} less the squad's own steps, which stay. */
    private List<BlockPos> unburying(WorldView world, FarmPlan farm) {
        return earthwork(world, farm).stream().filter(c -> !steps.contains(c)).toList();
    }

    static List<BlockPos> earthwork(WorldView world, FarmPlan farm) {
        Set<BlockPos> plot = Set.copyOf(farm.plot());
        Set<BlockPos> dig = new LinkedHashSet<>();
        for (BlockPos c : farm.plot()) {
            // The whole heap over it, to its top: the clearer cuts the highest first, and live (field574) a cell's dirt
            // with dirt over it - the block over it no earthwork - left the camp's farm 13 blocks no cut could take.
            for (BlockPos top = c.offset(0, 1, 0); world.isCobblestone(top.x(), top.y(), top.z())
                    || world.isSoil(top.x(), top.y(), top.z()) && !world.isFarmland(top.x(), top.y(), top.z()); top = top.offset(0, 1, 0)) {
                dig.add(top);
            }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                BlockPos beside = c.offset(d[0], 0, d[1]);
                if (plot.contains(beside) || farm.trenches().contains(beside) || !world.isSolid(beside.x(), beside.y(), beside.z())
                        || world.isWater(beside.x(), beside.y(), beside.z())) {
                    continue;
                }
                for (int up = 1; up <= 2; up++) {
                    BlockPos b = beside.offset(0, up, 0);
                    if (world.isSoil(b.x(), b.y(), b.z()) && !world.isFarmland(b.x(), b.y(), b.z())) {
                        dig.add(b);
                    }
                }
            }
        }
        // A chunk farm's ground levelled to its template (FarmSite, the SURVEYED stage): what stands over it, three high,
        // dug - the top first - for the dirt its hollows are filled with. Live (field516-543), the camp's chunk farm stood
        // a day SURVEYED, 64 blocks over its ground and 52 cells under, its tender farming only what lay level.
        for (ChunkFarm chunk : farm.chunks()) {
            for (BlockPos g : chunk.ground()) {
                for (int up = 3; up >= 1; up--) {
                    BlockPos b = g.offset(0, up, 0);
                    if ((world.isSoil(b.x(), b.y(), b.z()) || world.isCobblestone(b.x(), b.y(), b.z()) || world.isStone(b.x(), b.y(), b.z()))
                            && !world.isFarmland(b.x(), b.y(), b.z())) {
                        dig.add(b);
                    }
                }
            }
        }
        return List.copyOf(dig);
    }

    /** The cells of {@code farm}'s chunk farms' ground missing - open, or water - for its tender to fill with the dirt it carries. */
    static List<BlockPos> groundToFill(WorldView world, FarmPlan farm) {
        // Not a trench: its water is the farm's own (in the squad simulation, the tender filled the camp farm's trench in).
        Set<BlockPos> trenches = Set.copyOf(farm.trenches());
        return farm.chunks().stream().flatMap(c -> c.ground().stream()).filter(g -> !trenches.contains(g))
                .filter(g -> !world.isSolid(g.x(), g.y(), g.z()) || world.isWater(g.x(), g.y(), g.z())).toList();
    }

    /**
     * The cells of {@code farm}'s trenches to cut now: ground still, beside water - out from the pond a cell at a time,
     * so each fills as it's dug: a cell dug ahead of the water is a hole, and a route fills it in again. Cut by the
     * farmer itself, not its earthwork: a clearing never makes a cut that lets water in, and a trench is that cut.
     */
    static List<BlockPos> trenchToCut(WorldView world, FarmPlan farm) {
        return farm.trenches().stream().filter(c -> world.isSolid(c.x(), c.y(), c.z()) && !world.isWater(c.x(), c.y(), c.z())
                && (world.isWater(c.x() + 1, c.y(), c.z()) || world.isWater(c.x() - 1, c.y(), c.z())
                || world.isWater(c.x(), c.y(), c.z() + 1) || world.isWater(c.x(), c.y(), c.z() - 1))).toList();
    }

    /** Where to stand to cut trench cell {@code c}: on the ground beside it, across the trench - not in its water's way. */
    private static Optional<BlockPos> trenchStand(WorldView world, BlockPos c) {
        boolean alongX = world.isWater(c.x() + 1, c.y(), c.z()) || world.isWater(c.x() - 1, c.y(), c.z());
        return java.util.stream.Stream.of(alongX ? c.offset(0, 1, 1) : c.offset(1, 1, 0), alongX ? c.offset(0, 1, -1) : c.offset(-1, 1, 0))
                .filter(s -> Pathfinder.standable(world, s)).findFirst();
    }

    /** The cells any farmer working this farm couldn't work, for a while. */
    private final Setbacks<BlockPos> shared;

    /**
     * The cells of {@code farm}'s chunk farms still to put {@code item} in - its fence posts, its gates or its chest -
     * open, with ground under them, less those {@code skipped}: none for any other item.
     */
    static List<BlockPos> fixtures(WorldView world, FarmPlan farm, ItemClass item, Set<BlockPos> skipped) {
        return farm.chunks().stream().flatMap(c -> (item == ItemClass.FENCE ? c.fence() : item == ItemClass.FENCE_GATE ? c.gates()
                        : item == ItemClass.CHEST ? List.of(c.chest()) : item == ItemClass.TORCH ? c.torches() : List.<BlockPos>of()).stream())
                .filter(f -> !world.solidHere(f.x(), f.y(), f.z()) && !world.isGate(f.x(), f.y(), f.z()) && !world.isWater(f.x(), f.y(), f.z())
                        && !world.isTorch(f.x(), f.y(), f.z()) && world.isSolid(f.x(), f.y() - 1, f.z()) && !skipped.contains(f)).toList();
    }

    /**
     * What a farmer keeping {@code farm} needs made, carrying what {@code inventory} holds: a hoe; a bucket while its
     * holes are dry; and the fixtures its chunk farms still want - fence posts, gates, a chest - one batch at a time.
     */
    public static Set<Recipe> wants(WorldView world, FarmPlan farm, Inventory inventory) {
        Set<Recipe> wants = new LinkedHashSet<>();
        if (inventory.count(ItemClass.HOE) == 0) {
            wants.add(Recipe.WOODEN_HOE);
        }
        if (inventory.count(ItemClass.BUCKET) + inventory.count(ItemClass.WATER_BUCKET) == 0
                && farm.holes().stream().anyMatch(h -> !world.isWaterSource(h.x(), h.y(), h.z()))) {
            wants.add(Recipe.BUCKET);
        }
        Map<ItemClass, Recipe> makes = Map.of(ItemClass.FENCE, Recipe.FENCE, ItemClass.FENCE_GATE, Recipe.FENCE_GATE, ItemClass.CHEST, Recipe.CHEST,
                ItemClass.TORCH, Recipe.TORCHES);
        for (ItemClass item : FIXTURES) {
            // Torches only from coal it carries: a farmer sets up no smelter for charcoal - in the squad simulation, the
            // furnace that wanted held up its fence and chest.
            boolean makings = item != ItemClass.TORCH || inventory.count(ItemClass.COAL) > 0;
            if (inventory.count(item) == 0 && makings && !fixtures(world, farm, item, Set.of()).isEmpty()) {
                wants.add(makes.get(item));
            }
        }
        return wants;
    }

    /** A tick of putting up the farm's fixtures of the kind under way: over when none is left, or none is carried. */
    private void place(WorldView world, FarmPlan farm) {
        Builder builder = builders.get(placing);
        List<BlockPos> left = fixtures(world, farm, placing, builder.skipped());
        if (left.isEmpty() || inventory.count(placing) == 0) {
            builder.stop();
            placing = null;
            return;
        }
        if (builder.tick(soldier, world, left) instanceof Builder.Progress.Failed(String why)) {
            // A cell that won't take one is the builder's to leave out: the rest are put up, and the farm's other work goes on.
            LOG.info("{} leaves the farm's {} for now - {}", name, placing, why);
            placing = null;
        }
    }

    /** Opens the shut gate at {@code gate}, from the ground beside it: the farm's way in, left open. */
    private void open(WorldView world, BlockPos gate) {
        Optional<BlockPos> stand = WaterCarrier.beside(world, gate.offset(0, -1, 0));
        if (stand.isEmpty()) {
            passedOver.failed(gate, ticks, "no ground beside the farm's gate at " + gate);
            return;
        }
        claimed = gate;
        claims.add(gate);
        BlockPos s = stand.get();
        standAt = new Position(s.x() + 0.5, s.y(), s.z() + 0.5);
        fieldWork = new Script(name, List.of(
                new Step("walk to " + s, () -> {
                }, new Expect("standing at " + s, () -> Math.hypot(soldier.x() - standAt.x(), soldier.z() - standAt.z()) < 0.6
                        && Math.abs(soldier.y() - standAt.y()) < 0.6), Math.max(WALK_TICKS, Digger.walkTicks(soldier, s))),
                new Step("open the gate at " + gate, () -> !world.isSolid(gate.x(), gate.y(), gate.z()), () -> session.useBlock(gate.x(), gate.y(), gate.z()),
                        new Expect("the gate at " + gate + " open", () -> !world.isSolid(gate.x(), gate.y(), gate.z())), ANSWER_TICKS)));
        LOG.info("{} opens the farm's gate at {}", name, gate);
    }

    /** A tick of filling the hole it's watering: done when its water's there; a run that fails passes the hole over. */
    private void fill(WorldView world) {
        switch (carrier.tick(soldier, world, watering)) {
            case WaterCarrier.Progress.Working _ -> {
            }
            case WaterCarrier.Progress.Done _ -> watering = null;
            case WaterCarrier.Progress.Failed(String why) -> {
                passedOver.failed(watering, ticks, why);
                watering = null;
                fail(why);
            }
        }
    }

    /**
     * Whether {@code farm} has work in it: wheat ripe, farmland bare, or ground to till. A planted
     * plot growing has none - its farmers are free for other work until it ripens: live, a squad's
     * farm half stood by its growing wheat with the camp to keep.
     */
    public static boolean hasWork(WorldView world, FarmPlan farm, Set<BlockPos> steps) {
        return farm.plot().stream().anyMatch(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).orElse(-1) == Digger.RIPE
                || world.isFarmland(c.x(), c.y(), c.z()) && world.wheatAge(c.x(), c.y() + 1, c.z()).isEmpty() && !world.isSolid(c.x(), c.y() + 1, c.z())
                || world.isTillable(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z()))
                || !trenchToCut(world, farm).isEmpty() || earthwork(world, farm).stream().anyMatch(c -> !steps.contains(c))
                || !groundToFill(world, farm).isEmpty();
    }

    public synchronized boolean active() {
        return digger != null || fieldWork != null || unburier.active() || carrier.active() || placing != null;
    }

    /** Where the soldier should be standing for the job under way. */
    public synchronized Optional<Position> standAt() {
        return digger != null ? Optional.of(digger.standAt()) : unburier.active() ? unburier.standAt()
                : carrier.active() ? carrier.standAt() : placing != null ? builders.get(placing).standAt() : Optional.ofNullable(standAt);
    }

    /** Why the last job failed, while the soldier waits before the next. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Each tick: works {@code farm} while {@code free}; anything else stops the job under way. */
    /** How far a farmer looks for grass to break for seeds. */
    static final int SEEDS_RADIUS = 32;
    /** How long a plot cell whose job failed - not walked to, not answered - is first passed over; longer each time. */
    public static final int PASS_OVER_TICKS = 2400;
    private long ticks;
    /** How many times a harvest is started on one crop, never done, before it's passed over. */
    static final int RESTARTS_BEFORE_PASSING_OVER = 3;
    /** The crop last started on, and how many times running - reset by any harvest done. */
    private BlockPos lastHarvest;
    private int restarts;
    private final Setbacks<BlockPos> passedOver = new Setbacks<>(PASS_OVER_TICKS, 16L * PASS_OVER_TICKS);

    /** The water hole it's watering, if it is: for tests. */
    synchronized Optional<BlockPos> wateringHole() {
        return Optional.ofNullable(watering);
    }

    /** Why it needs its squad leader's help, if it's out of ways of its own ({@link Setbacks#stuck}). */
    public synchronized Optional<String> stuck() {
        return passedOver.stuck().map(s -> "can't keep the farm: " + s);
    }

    /** How far from its eyes a farmer breaks a crop as it passes: inside the server's reach. */
    static final double PASSING_REACH = 4.0;
    /** Ticks between two crops broken in passing: a crop breaks at a touch, and the server takes one a tick. */
    static final int PASSING_TICKS = 2;

    /**
     * Ripe wheat of its plot within reach broken as it goes, whatever its job - walking to the next crop, tilling,
     * sowing - one each {@link #PASSING_TICKS} (operator, 2026-11-20: harvest moving forward). Wheat breaks at a touch,
     * so a farmer walking a row brings it in: in the squad simulation, stopping at a stand for each crop, 25 a minute.
     */
    private void harvestInPassing(Position soldier, WorldView world, FarmPlan farm) {
        // Crop-ticks: each ripe crop of its plot a tick - over the crops brought in, how long one stands ripe before it's
        // in, whatever the field's size or how fast it grows (Little's law). So too each bare cell, with seeds carried.
        ripeTicks += farm.plot().stream().filter(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).orElse(-1) == Digger.RIPE).count();
        if (inventory.count(ItemClass.SEEDS) > 0) {
            bareTicks += farm.plot().stream().filter(c -> world.isFarmland(c.x(), c.y(), c.z())
                    && world.wheatAge(c.x(), c.y() + 1, c.z()).isEmpty() && !world.isSolid(c.x(), c.y() + 1, c.z())).count();
        }
        if (ticks % PASSING_TICKS != 0) {
            return;
        }
        double ex = soldier.x(), ey = soldier.y() + 1.62, ez = soldier.z();
        farm.plot().stream().map(c -> c.offset(0, 1, 0))
                .filter(crop -> world.wheatAge(crop.x(), crop.y(), crop.z()).orElse(-1) == Digger.RIPE && !claims.contains(crop))
                .filter(crop -> Math.pow(crop.x() + 0.5 - ex, 2) + Math.pow(crop.y() + 0.5 - ey, 2) + Math.pow(crop.z() + 0.5 - ez, 2)
                        <= PASSING_REACH * PASSING_REACH)
                .min(java.util.Comparator.comparingDouble(crop -> Math.pow(crop.x() + 0.5 - ex, 2) + Math.pow(crop.z() + 0.5 - ez, 2)))
                .ifPresent(crop -> {
                    session.startDigging(crop.x(), crop.y(), crop.z());
                    session.finishDigging(crop.x(), crop.y(), crop.z());
                    harvested++;
                });
        sowInPassing(ex, ey, ez, world, farm);
    }

    /** Ticks a cell sown in passing is left for the server's answer before it's sown again. */
    static final int SOWN_ANSWER_TICKS = 20;
    /** Cells sown in passing, the tick each was sent: not sent again till the server's had time to answer. */
    private final java.util.Map<BlockPos, Long> sownAt = new java.util.HashMap<>();

    /**
     * With seeds already in hand, the bare farmland of its plot within reach sown as it goes, one each
     * {@link #PASSING_TICKS} (operator, 2026-11-20: tilling and sowing the way harvesting is done): a farmer walking to
     * the next cell to sow plants every one it passes. Seeds aren't taken in hand for it - the hand isn't swapped about.
     */
    private void sowInPassing(double ex, double ey, double ez, WorldView world, FarmPlan farm) {
        if (!inHand(ItemClass.SEEDS)) {
            return;
        }
        sownAt.values().removeIf(at -> ticks - at > SOWN_ANSWER_TICKS);
        farm.plot().stream()
                .filter(c -> world.isFarmland(c.x(), c.y(), c.z()) && world.wheatAge(c.x(), c.y() + 1, c.z()).isEmpty()
                        && !world.isSolid(c.x(), c.y() + 1, c.z()) && !sownAt.containsKey(c) && (c.equals(claimed) || !claims.contains(c)))
                .filter(c -> Math.pow(c.x() + 0.5 - ex, 2) + Math.pow(c.y() + 1 - ey, 2) + Math.pow(c.z() + 0.5 - ez, 2)
                        <= PASSING_REACH * PASSING_REACH)
                .min(java.util.Comparator.comparingDouble(c -> Math.pow(c.x() + 0.5 - ex, 2) + Math.pow(c.z() + 0.5 - ez, 2)))
                .ifPresent(c -> {
                    session.useBlockFace(c.x(), c.y(), c.z(), BlockFace.UP);
                    sownAt.put(c, ticks);
                    sown++;
                });
    }

    /**
     * The ledger's baselines, measured by the same counters in the squad simulation (TaskOrganisationSimulationTest's
     * tilled field kept for a ledger spell, its wheat ripening and resown): the crop-ticks a crop stands ripe before
     * it's brought in, and the cell-ticks a cell stands bare, seeds carried, before it's sown.
     */
    public static final double SIM_TICKS_A_CROP = 87.7;
    public static final double SIM_TICKS_A_SOWING = 1045;
    private int harvested, sown;
    private long ripeTicks, bareTicks;
    /** Whether the earthwork's next look for a stand takes one on the farmland, none off it having reached. */
    private boolean onFarmland;
    /** The last said of why the farm's earthwork took no cut. */
    private String earthworkSaid;
    /** What the field job under way uses: a hoe to till, seeds to sow - none for a gate opened. */
    private ItemClass fieldItem;

    /**
     * What it's brought about since last taken, for the ledger (backlog/simulator-fidelity.md), each kind's units and
     * the ticks there was work of that kind: "harvest", crops brought in over ticks its plot had ripe wheat; "sowing",
     * cells sown over ticks it carried seeds with farmland bare. A kind with nothing done isn't there.
     */
    public synchronized java.util.Map<String, int[]> takeYields() {
        java.util.Map<String, int[]> taken = new java.util.TreeMap<>();
        if (harvested > 0) {
            taken.put("harvest", new int[]{harvested, (int) Math.min(Integer.MAX_VALUE, ripeTicks)});
            harvested = 0;
            ripeTicks = 0;
        }
        if (sown > 0) {
            taken.put("sowing", new int[]{sown, (int) Math.min(Integer.MAX_VALUE, bareTicks)});
            sown = 0;
            bareTicks = 0;
        }
        return taken;
    }

    public synchronized void tick(Position soldier, WorldView world, Optional<FarmPlan> farm, boolean free) {
        this.soldier = soldier;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        unburier.pass();
        if (farm.isEmpty()) {
            passedOver.noWork();
        }
        if (!free || farm.isEmpty()) {
            if (active()) {
                // Said, so a job stopped over and over shows why: live, a harvest restarted every few seconds.
                LOG.info("{} leaves its farm work - {}", name, farm.isEmpty() ? "the farm is no longer its task" : "its hands are needed elsewhere");
            }
            stop();
            return;
        }
        harvestInPassing(soldier, world, farm.get());
        if (digger != null) {
            switch (digger.tick(soldier, world)) {
                case Digger.Progress.Working _ -> {
                }
                case Digger.Progress.Done _ -> {
                    harvested += digger.spot().target() == Digger.Target.WHEAT ? 1 : 0;
                    passedOver.worked(digger.spot().block().offset(0, -1, 0));
                    lastHarvest = null;
                    restarts = 0;
                    stopDigging();
                }
                // The crop gone before it got there is a squadmate's harvest, not a failure: live,
                // it was counted one, and soldiers reported CANTCO on a farm being worked.
                case Digger.Progress.Failed _ when !digger.spot().target().at(world, digger.spot().block()) -> stopDigging();
                case Digger.Progress.Failed(String why) -> fail(why);
            }
            return;
        }
        if (fieldWork != null && fieldItem != null && inventory.count(fieldItem) == 0) {
            // Its seeds all sown in passing on the way: the job's over, not failed - the next one takes what's carried.
            endFieldWork();
        }
        if (fieldWork != null) {
            switch (fieldWork.tick()) {
                case Script.Status.Running _ -> {
                }
                case Script.Status.Done _ -> {
                    if (claimed != null) {
                        passedOver.worked(claimed);
                    }
                    endFieldWork();
                }
                case Script.Status.Interrupted _ -> endFieldWork();
                case Script.Status.Failed(String step, String reason) -> fail(step + ": " + reason);
            }
            return;
        }
        if (unburier.active()) {
            unburier.tick(soldier, world, unburying(world, farm.get()), true);
            return;
        }
        if (watering != null) {
            fill(world);
            return;
        }
        if (placing != null) {
            place(world, farm.get());
            return;
        }
        if (cooldown > 0) {
            return;
        }
        next(world, farm.get());
    }

    /** Why the farm's cells aren't its next job: for a farmer stuck with work it doesn't start. */
    public synchronized String passedOver(WorldView world, FarmPlan farm) {
        List<BlockPos> plot = farm.plot();
        long claimed = plot.stream().filter(c -> claims.contains(c) || claims.contains(c.offset(0, 1, 0))).count();
        long barred = plot.stream().filter(c -> passedOver.barred(c, ticks)).count();
        long sharedBarred = plot.stream().filter(c -> shared.barred(c, ticks)).count();
        long tillable = plot.stream().filter(c -> world.isTillable(c.x(), c.y(), c.z()) && !world.isSolid(c.x(), c.y() + 1, c.z())).count();
        long farmland = plot.stream().filter(c -> world.isFarmland(c.x(), c.y(), c.z())).count();
        return plot.size() + " cells: " + claimed + " claimed, " + barred + " passed over, " + sharedBarred + " passed over by all, "
                + tillable + " to till, " + farmland + " farmland; " + inventory.count(ItemClass.SEEDS) + " seeds, "
                + inventory.count(ItemClass.HOE) + " hoes" + (cooldown > 0 ? ", cooling down" : "");
    }

    /** The most pressing job left on {@code farm}, started. */
    private void next(WorldView world, FarmPlan farm) {
        // A harvest claims the crop over its cell, not the cell: live, four farmers harvested one
        // ripe wheat in turn, each checking the ground cell a squadmate's harvest hadn't claimed.
        // And not a cell with no room for the head over it: live, a hut's wall stood over the farm's
        // edge, and its farmers tried for the cell, "no route", minutes on end.
        List<BlockPos> plot = farm.plot().stream().filter(c -> !claims.contains(c) && !claims.contains(c.offset(0, 1, 0))
                        && !passedOver.barred(c, ticks) && !shared.barred(c, ticks) && !world.isSolid(c.x(), c.y() + 2, c.z()))
                .sorted(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z()))).toList();
        // Its water holes first, with a bucket: the ground round each grows dry till its water's there.
        if (inventory.count(ItemClass.BUCKET) + inventory.count(ItemClass.WATER_BUCKET) > 0) {
            for (BlockPos h : farm.holes()) {
                if (world.isWaterSource(h.x(), h.y(), h.z()) || claims.contains(h) || passedOver.barred(h, ticks)) {
                    continue;
                }
                if (world.isSolid(h.x(), h.y(), h.z())) {
                    Optional<BlockPos> stand = WaterCarrier.beside(world, h);
                    if (stand.isPresent()) {
                        Digger.Target ground = Digger.Target.SOIL.at(world, h) ? Digger.Target.SOIL : Digger.Target.OTHER;
                        if (!ground.at(world, h)) {
                            passedOver.failed(h, ticks, "no digger takes the block at " + h + " for the water hole");
                            continue;
                        }
                        digger = new Digger(name, session, inventory, claims, new Digger.Spot(ground, stand.get(), h), Digger.walkTicks(soldier, stand.get()));
                        LOG.info("{} digs the farm's water hole at {}", name, h);
                        return;
                    }
                } else if (world.isSolid(h.x(), h.y() - 1, h.z()) && WaterCarrier.beside(world, h).isEmpty()) {
                    // No ground beside it to pour from - the farm's heaped ground standing round it: it waits on the
                    // earthwork, not failed. Live (field566), 614 failures at one such hole took Olga off the farm
                    // before its earthwork was ever reached.
                    continue;
                } else if (world.isSolid(h.x(), h.y() - 1, h.z())) {
                    watering = h;
                    fill(world);
                    return;
                } else if (inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT) > 0) {
                    // Its floor first: water poured over an open cell runs down into it - the operator (2026-11-20): something
                    // under the farm's water. A block of what it carries put under the hole, from the ground beside it.
                    if (holeFloor.tick(soldier, world, List.of(h.offset(0, -1, 0))) instanceof Builder.Progress.Failed(String why)) {
                        passedOver.failed(h, ticks, "no floor put under the farm's water hole at " + h + ": " + why);
                    }
                    return;
                }
            }
        }
        // Its trenches first: the ground beside each grows dry till its water's there.
        for (BlockPos c : trenchToCut(world, farm)) {
            Optional<BlockPos> stand = claims.contains(c) || passedOver.barred(c, ticks) ? Optional.empty() : trenchStand(world, c);
            if (stand.isPresent()) {
                // Farmland it's cut through is no SOIL to a digger: dug as any block. One no digger takes is passed over:
                // started on, its digger would find nothing there, over and over.
                Digger.Target soil = Digger.Target.SOIL.at(world, c) ? Digger.Target.SOIL : Digger.Target.OTHER;
                if (!soil.at(world, c)) {
                    passedOver.failed(c, ticks, "no digger takes the block at " + c + " for the trench");
                    continue;
                }
                digger = new Digger(name, session, inventory, claims, new Digger.Spot(soil, stand.get(), c), Digger.walkTicks(soldier, stand.get()));
                LOG.info("{} cuts the farm's trench at {} from {}", name, c, stand.get());
                return;
            }
        }
        // Earthwork first: a cell in a hollow walked into for a harvest or a sowing is trampled.
        List<BlockPos> earthwork = unburying(world, farm);
        if (!earthwork.isEmpty()) {
            unburier.tick(soldier, world, earthwork, true);
            if (unburier.active()) {
                return;
            }
            // Said when why changes: live (field570) a chunk farm's 92 blocks of earthwork stood a whole day, its
            // farmer at its other work, and nothing said why none was taken.
            if (unburier.idleReason().filter(r -> r.startsWith("nothing in reach")).isPresent()) {
                onFarmland = !onFarmland; // the next look from the farmland too, then off it again
            }
            String why = earthwork.size() + " blocks of the farm's earthwork, none taken: " + unburier.idleReason().orElse("its clearer resting")
                    + " - the first at " + earthwork.getFirst();
            if (!why.equals(earthworkSaid)) {
                LOG.info("{} - {}", name, why);
                earthworkSaid = why;
            }
        }
        // Then its chunk farms' ground filled, with what the earthwork gave it - not a cell passed over lately.
        List<BlockPos> fill = groundToFill(world, farm).stream().filter(c -> !passedOver.barred(c, ticks)).toList();
        if (!fill.isEmpty() && inventory.count(ItemClass.DIRT) + inventory.count(ItemClass.COBBLESTONE) > 0) {
            if (groundFill.tick(soldier, world, fill) instanceof Builder.Progress.Failed(String why)) {
                fill.forEach(c -> passedOver.failed(c, ticks, "the chunk farm's ground at " + c + " not filled: " + why));
            }
            return;
        }
        // Its chunk farms' fixtures, with what it carries: the fence, the gates - opened, the farm's way in - and the chest.
        for (ItemClass item : FIXTURES) {
            if (inventory.count(item) > 0 && !fixtures(world, farm, item, builders.get(item).skipped()).isEmpty()) {
                placing = item;
                place(world, farm);
                return;
            }
        }
        Optional<BlockPos> shut = farm.chunks().stream().flatMap(c -> c.gates().stream()).filter(g -> world.isGate(g.x(), g.y(), g.z())
                && world.isSolid(g.x(), g.y(), g.z()) && !claims.contains(g) && !passedOver.barred(g, ticks)).findFirst();
        if (shut.isPresent()) {
            open(world, shut.get());
            return;
        }
        Optional<BlockPos> ripe = plot.stream().filter(c -> world.wheatAge(c.x(), c.y() + 1, c.z()).orElse(-1) == Digger.RIPE).findFirst();
        if (ripe.isPresent()) {
            BlockPos crop = ripe.get().offset(0, 1, 0);
            // Started on again and again, never done: a failure, whatever stopped it each time.
            // Live, three farmers started on one ripe wheat 168 times in an hour, every start
            // interrupted before it could fail, and the crop never passed over.
            restarts = crop.equals(lastHarvest) ? restarts + 1 : 1;
            lastHarvest = crop;
            if (restarts > RESTARTS_BEFORE_PASSING_OVER) {
                passedOver.failed(ripe.get(), ticks, "the wheat at " + crop + " started on " + RESTARTS_BEFORE_PASSING_OVER + " times and never done");
                shared.failed(ripe.get(), ticks);
                lastHarvest = null;
                restarts = 0;
                next(world, farm); // the next job, the crop passed over now
                return;
            }
            digger = new Digger(name, session, inventory, claims, new Digger.Spot(Digger.Target.WHEAT, crop, crop), Digger.walkTicks(soldier, crop));
            LOG.info("{} harvests the wheat at {}", name, crop);
            return;
        }
        // The nearest: sown in passing on the way, a farmer going from one to the next sweeps the field.
        Optional<BlockPos> bare = plot.stream().filter(c -> world.isFarmland(c.x(), c.y(), c.z())
                && world.wheatAge(c.x(), c.y() + 1, c.z()).isEmpty() && !world.isSolid(c.x(), c.y() + 1, c.z()))
                .min(java.util.Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())));
        if (bare.isPresent() && inventory.count(ItemClass.SEEDS) > 0) {
            BlockPos cell = bare.get();
            start(cell, ItemClass.SEEDS, "plant seeds at " + cell, () -> world.wheatAge(cell.x(), cell.y() + 1, cell.z()).isPresent());
            return;
        }
        // Ground to till with room over it to stand in: a block put down on a plot cell - a pillar's,
        // a fill's - leaves no stand, and live, farmers sent to stand inside one reported no route.
        Optional<BlockPos> untilled = plot.stream().filter(c -> world.isTillable(c.x(), c.y(), c.z())
                && !world.isSolid(c.x(), c.y() + 1, c.z())).findFirst();
        if (untilled.isPresent() && inventory.count(ItemClass.HOE) > 0) {
            BlockPos cell = untilled.get();
            start(cell, ItemClass.HOE, "till " + cell, () -> world.isFarmland(cell.x(), cell.y(), cell.z()));
            return;
        }
        if (inventory.count(ItemClass.SEEDS) == 0 && (bare.isPresent() || untilled.isPresent())) {
            // Farther than a gatherer looks: a squad breaking grass for seeds soon clears what's near.
            Optional<Digger.Spot> grass = Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims,
                    Digger.Target.GRASS, SEEDS_RADIUS);
            if (grass.isEmpty()) {
                fail("no seeds, and no grass within " + SEEDS_RADIUS + " blocks to find them in");
                return;
            }
            digger = new Digger(name, session, inventory, claims, grass.get(), Digger.walkTicks(soldier, grass.get().stand()));
        }
    }

    /** A job on {@code cell}: stand on it, {@code item} in hand, use it on the cell's top - done when {@code done}. */
    private void start(BlockPos cell, ItemClass item, String job, BooleanSupplier done) {
        fieldItem = item;
        claimed = cell;
        claims.add(cell);
        standAt = new Position(cell.x() + 0.5, cell.y() + 1, cell.z() + 0.5);
        // In hand before the walk, so seeds are sown in passing on the way (sowInPassing).
        fieldWork = new Script(name, List.of(
                new Step(item + " in hand", () -> inHand(item), () -> takeInHand(item),
                        new Expect(item + " in hand", () -> inHand(item)), ANSWER_TICKS),
                new Step("walk to " + cell, done, () -> {
                }, new Expect("standing on " + cell + ", or its job done in passing", () -> on(cell) || done.getAsBoolean()),
                        Math.max(WALK_TICKS, Digger.walkTicks(soldier, cell))),
                new Step(job, done, () -> {
                    if (item == ItemClass.HOE) {
                        // A hoe tills only under open air: grass or a flower on the cell is broken
                        // first - a touch breaks it, an empty cell ignores the dig, and the server
                        // takes both in the order sent. Live, every till was refused for it.
                        session.startDigging(cell.x(), cell.y() + 1, cell.z());
                        session.finishDigging(cell.x(), cell.y() + 1, cell.z());
                    }
                    session.useBlockFace(cell.x(), cell.y(), cell.z(), BlockFace.UP);
                    sown += item == ItemClass.SEEDS ? 1 : 0;
                }, new Expect(job + " done", done), ANSWER_TICKS)));
    }

    /**
     * Standing on {@code cell}, landed: farmland's top is 15/16 of a block. Not mid-fall - tilled
     * under a soldier still coming down onto it, it's trampled as it lands.
     */
    private boolean on(BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6 && Math.abs(soldier.y() - (cell.y() + 1)) < 0.1;
    }

    private boolean inHand(ItemClass item) {
        return ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot())).equals(Optional.of(item));
    }

    /** Holds {@code item}: from the hotbar, or swapped into a hotbar slot holding no weapon. */
    private void takeInHand(ItemClass item) {
        int slot = inventory.slotOf(item);
        if (slot < 0) {
            throw new IllegalStateException("no " + item + " carried - " + inventory.summary());
        }
        int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : spareHotbarSlot();
        if (slot < Inventory.HOTBAR_FIRST) {
            session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
    }

    private int spareHotbarSlot() {
        return inventory.spareHotbarSlot(); // Inventory.spareHotbarSlot: one rule for every routine
    }

    /**
     * The walk to where it stands has no route: the cell is passed over now, and the next taken at
     * once - not after the walk's whole allowance and a cooldown. Live, farmers stood a minute and
     * more at a time over a farm's four or five cells nobody could reach.
     */
    public synchronized void unreachable(String why) {
        if (standAt().isEmpty()) {
            return;
        }
        if (digger == null && unburier.active()) {
            unburier.unreachable(why); // its earthwork's cut: passed over by the clearer, which holds it
            return;
        }
        fail(why);
        cooldown = 0;
    }

    private void fail(String why) {
        // Its cell out of reach - across water, behind a wall - or its job refused: the rest first.
        // A harvest is a dig, its spot the crop over the plot cell: live, with no cell passed over
        // for it, four soldiers walked at one ripe wheat they couldn't reach all day.
        // Or the water hole it's watering: live (field563), with none passed over, Olga started one 1,321 times in a
        // quarter of an hour, "no route" to the pond's bank each time.
        BlockPos cell = digger != null ? digger.spot().block().offset(0, -1, 0) : claimed != null ? claimed : watering;
        if (cell != null) {
            passedOver.failed(cell, ticks, why);
            shared.failed(cell, ticks);
        }
        LOG.warn("{} stops farming for a while - {}", name, why);
        failure = why;
        cooldown = COOLDOWN_TICKS;
        stop();
    }

    private void endFieldWork() {
        fieldWork = null;
        fieldItem = null;
        standAt = null;
        if (claimed != null) {
            claims.remove(claimed);
            claimed = null;
        }
    }

    private void stopDigging() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }

    /** Stops the job under way, its claims given up. */
    public synchronized void stop() {
        stopDigging();
        unburier.stop();
        carrier.stop();
        watering = null;
        if (placing != null) {
            builders.get(placing).stop();
            placing = null;
        }
        endFieldWork();
    }
}
