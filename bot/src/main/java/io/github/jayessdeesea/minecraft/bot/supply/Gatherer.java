package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * One soldier gathering a material around its squad's objective (docs/military-doctrine.md §12):
 * dig the nearest log - or stone, with a pickaxe it makes first if it has none ({@link Armourer}) -
 * within {@link Digger#SEARCH_RADIUS} of the objective, again and again, until it carries its load. Ticked by the soldier's tick loop; synchronized
 * like the other routines in this package.
 *
 * <p>Gathering stops - quietly, the logs kept - when it's no longer wanted (contact, a new
 * objective without a gather). When there's nothing left in reach it waits {@link #RETRY_TICKS}
 * before looking again, rather than searching every tick.
 */
public final class Gatherer {

    private static final Logger LOG = LogManager.getLogger(Gatherer.class);

    /** What one soldier carries before it stops: loads it can still fight with. */
    public static final int CARRY_LOGS = 16;
    public static final int CARRY_STONE = 32;
    /** Dirt dug for a patrol base: a soldier's share of a hut, and a few over for the misses. */
    public static final int CARRY_SOIL = 16;
    /** Flint enough for eight arrows: some twenty gravel blocks dug. */
    public static final int CARRY_FLINT = 2;
    static final int RETRY_TICKS = 600;
    /** How far round the objective it looks, nearest ring first. */
    static final int[] WIDER = {Digger.SEARCH_RADIUS, 2 * Digger.SEARCH_RADIUS, 3 * Digger.SEARCH_RADIUS};

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;

    private Digger digger;
    /** How far the flood for stands it can walk to looks, in cells expanded. */
    static final int REACH_BUDGET = 40_000;
    /** The same, for trees looked for out to three times the usual distance. */
    static final int FAR_REACH_BUDGET = 150_000;

    /** Trees this soldier failed at (out of reach, or the log wouldn't break) - not tried again. */
    private final Set<BlockPos> avoid = new HashSet<>();
    /**
     * The tree it's felling: its logs still standing, every one cut before the next tree, the load
     * or not - a tree left half-standing holds its leaves, which then never fall for their saplings
     * (operator, 2026-11-17: get all the wood, so the trees fall, so you get saplings and replant).
     * Live, cutters took what was in reach and walked on, and nothing was replanted.
     */
    private final Set<BlockPos> tree = new HashSet<>();
    /** Where the tree it's felling stood - its lowest log - replanted once the last log is down. */
    private BlockPos stump;
    /** The replanting under way: to the stump, a sapling in hand, planted. */
    private Script replanting;
    private Position soldierAt;
    /** How far across from the log first cut a tree's logs are looked for: a spruce's or an oak's spread. */
    static final int TREE_REACH = 4;
    /** The most logs taken as one tree: a dark oak's or a giant spruce's, not a forest's. */
    static final int TREE_LOGS = 96;
    static final int PLANT_WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    private int retry;
    private boolean reportedFull;
    private boolean exhausted;

    public Gatherer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this(name, session, inventory, claims, Set.of());
    }

    /**
     * As above, with {@code steps} the squad's own standing steps and pillars, never dug for their
     * dirt: in the squad simulation a gatherer dug a squadmate's steps out from under it as soil,
     * and the wall over the slope went short of dirt by the block it climbed on.
     */
    public Gatherer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Set<BlockPos> steps) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.steps = steps;
    }

    /** The squad's steps and pillars - a live view the runner keeps - left standing. */
    private final Set<BlockPos> steps;

    public synchronized boolean active() {
        return digger != null || replanting != null;
    }

    /** Whether the woods around the objective are worked out: the last search found nothing in reach. */
    public synchronized boolean exhausted() {
        return exhausted;
    }

    /** Where the soldier should be standing while it gathers, if anywhere. */
    public synchronized Optional<Position> standAt() {
        if (replanting != null && plantAt != null) {
            return Optional.of(new Position(plantAt.x() + 0.5, plantAt.y(), plantAt.z() + 0.5));
        }
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /** The stump being replanted. */
    private BlockPos plantAt;

    /**
     * Each tick: gathers {@code resource} around {@code around} while {@code wanted}; empty
     * {@code resource} means the squad isn't gathering.
     */
    /** What it was last given to gather: the squad's objective's, or a ground work run's. */
    private Resource gathering;

    /** What it's gathering, while it's at it: empty when it isn't, or never was. */
    public synchronized Optional<Resource> gathering() {
        return active() ? Optional.ofNullable(gathering) : Optional.empty();
    }

    public synchronized void tick(Position soldier, WorldView world, Optional<Resource> resource, Position around,
                                  boolean wanted) {
        tick(soldier, world, resource, around, wanted, OptionalInt.empty());
    }

    /**
     * As {@link #tick(Position, WorldView, Optional, Position, boolean)}, carrying {@code amount}
     * when it's given - an {@code Order.Gather}'s - rather than the resource's usual load.
     */
    public synchronized void tick(Position soldier, WorldView world, Optional<Resource> resource, Position around,
                                  boolean wanted, OptionalInt amount) {
        if (retry > 0) {
            retry--;
        }
        if (resource.isEmpty() || !wanted) {
            stop();
            exhausted = false;
            return;
        }
        gathering = resource.get();
        ItemClass item = switch (resource.get()) {
            case WOOD -> ItemClass.LOG;
            case SOIL -> ItemClass.DIRT;
            case FLINT -> ItemClass.FLINT;
            default -> ItemClass.COBBLESTONE;
        };
        int load = amount.orElse(switch (resource.get()) {
            case WOOD -> CARRY_LOGS;
            case SOIL -> CARRY_SOIL;
            case FLINT -> CARRY_FLINT;
            default -> CARRY_STONE;
        });
        if (resource.get() == Resource.STONE && inventory.slotOf(ItemClass.PICKAXE) < 0) {
            stop(); // a pickaxe first - Armourer makes one
            return;
        }
        soldierAt = soldier;
        if (replanting != null) {
            switch (replanting.tick()) {
                case Script.Status.Running _ -> {
                    return;
                }
                case Script.Status.Done _, Script.Status.Interrupted _ -> {
                }
                case Script.Status.Failed(String step, String reason) -> LOG.info("{} couldn't replant the tree at {}: {}: {}", name, plantAt, step, reason);
            }
            replanting = null;
            plantAt = null;
        }
        // A tree started is felled whole, the load or not.
        if (inventory.count(item) >= load && tree.isEmpty()) {
            if (!reportedFull) {
                LOG.info("{} carries {} {} - done gathering", name, inventory.count(item), item);
                reportedFull = true;
            }
            stop();
            return;
        }
        reportedFull = false;
        Digger.Target target = switch (resource.get()) {
            case WOOD -> Digger.Target.LOG;
            case SOIL -> Digger.Target.SOIL;
            case FLINT -> Digger.Target.GRAVEL;
            default -> Digger.Target.STONE;
        };
        if (digger == null) {
            if (retry == 0) {
                next(world, soldier, around, target);
            }
            return;
        }
        switch (digger.tick(soldier, world)) {
            case Digger.Progress.Working working -> {
            }
            case Digger.Progress.Done done -> {
                BlockPos cut = digger.spot().block();
                digger.release();
                if (target == Digger.Target.LOG) {
                    felled(world, cut);
                }
                next(world, soldier, around, target);
            }
            case Digger.Progress.Failed(String why) -> {
                LOG.info("{} leaves that spot - {}", name, why);
                avoid.add(digger.spot().block());
                avoid.add(digger.spot().stand());
                digger.release();
                digger = null;
                retry = RETRY_TICKS / 10; // try another tree soon
            }
        }
    }

    /** Stops gathering: frees its claims and takes its weapon back in hand. */
    private void stop() {
        tree.clear();
        stump = null;
        replanting = null;
        plantAt = null;
        if (digger == null) {
            return;
        }
        digger.release();
        digger = null;
        for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
            if (step instanceof Quartermaster.Step.Hold(int hotbarSlot)) {
                session.selectHotbarSlot(hotbarSlot);
                inventory.selectHotbar(hotbarSlot);
            } else if (step instanceof Quartermaster.Step.BringToHand(int slot, int hotbarSlot)) {
                session.swapWithHotbar(0, 0, slot, hotbarSlot);
            }
        }
    }

    private void next(WorldView world, Position soldier, Position around, Digger.Target target) {
        Set<BlockPos> excluded = new HashSet<>(claims);
        excluded.addAll(steps);
        // Stands it can get to from where it is - on foot, on the blocks it carries, or cutting steps,
        // as its route there would: a stand on paper can be shut off on every side, and one down a
        // pit it was filling gets out on its blocks.
        // Trees further still: ground gathered over for days is bare a long way out - live, a squad
        // sent for wood beside the keep found none within 48 blocks, with forest in sight - and a
        // load of logs is worth a two-minute walk. Not for a handle's log, which the armourer looks for.
        int[] rings = target == Digger.Target.SOIL ? new int[]{WIDER[0]}
                : target == Digger.Target.LOG ? new int[]{WIDER[0], WIDER[1], WIDER[2], 2 * WIDER[2], 3 * WIDER[2]} : WIDER;
        int span = (int) Math.ceil(Math.hypot(around.x() - soldier.x(), around.z() - soldier.z())) + rings[rings.length - 1] + 4;
        Set<BlockPos> walkable = Pathfinder.climbs(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), span,
                target == Digger.Target.LOG ? FAR_REACH_BUDGET : REACH_BUDGET,
                inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT), inventory.slotOf(ItemClass.PICKAXE) >= 0).keySet();
        excluded.addAll(avoid);
        // The rest of the tree it's felling first, from wherever it can get to - up its own steps, on the
        // blocks it carries - and only then another tree; with the tree down, its stump replanted.
        if (target == Digger.Target.LOG) {
            tree.removeIf(c -> !world.isLog(c.x(), c.y(), c.z()));
            if (!tree.isEmpty()) {
                Set<BlockPos> others = new HashSet<>(excluded);
                int minX = tree.stream().mapToInt(BlockPos::x).min().orElseThrow() - 1, maxX = tree.stream().mapToInt(BlockPos::x).max().orElseThrow() + 1;
                int minY = tree.stream().mapToInt(BlockPos::y).min().orElseThrow() - 1, maxY = tree.stream().mapToInt(BlockPos::y).max().orElseThrow() + 1;
                int minZ = tree.stream().mapToInt(BlockPos::z).min().orElseThrow() - 1, maxZ = tree.stream().mapToInt(BlockPos::z).max().orElseThrow() + 1;
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if (world.isLog(x, y, z) && !tree.contains(new BlockPos(x, y, z))) {
                                others.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
                BlockPos base = stump != null ? stump : tree.iterator().next();
                Optional<Digger.Spot> rest = Digger.findSpotAmong(world, base, others, target, TREE_REACH + 2, walkable);
                if (rest.isPresent()) {
                    exhausted = false;
                    digger = new Digger(name, session, inventory, claims, rest.get(), Digger.walkTicks(soldier, rest.get().stand()));
                    return;
                }
                LOG.info("{} leaves {} log(s) of the tree at {} out of reach", name, tree.size(), base);
                tree.clear();
            }
            if (stump != null) {
                replant(world);
                if (replanting != null) {
                    digger = null;
                    return;
                }
            }
        }
        // Nearest first, then further out: none within reach of the objective is a reason to look
        // wider before asking for help - live, a camp long since cleared of trees had a wood a
        // minute's walk off, and its squad stood reporting CANTCO.
        Optional<Digger.Spot> spot = Optional.empty();
        // Not for dirt, which is everywhere: one with none near isn't sent a long way for it. Live,
        // a soldier digging dirt for its squad's hut by night went forty blocks off for it, met a
        // witch out there, and died.
        for (int radius : rings) {
            // A tree from wherever it can walk to, at any height; soil, stone and the rest near the
            // height asked, as before - in the squad simulation dirt taken from anywhere it could walk
            // came off the rim of a farm's hollow, and the farmer stepped down onto its crop.
            BlockPos asked = BlockPos.containing(around.x(), around.y(), around.z());
            spot = target == Digger.Target.LOG ? Digger.findSpotAmong(world, asked, excluded, target, radius, walkable)
                    : Digger.findSpot(world, asked, excluded, target, radius, walkable::contains);
            if (spot.isPresent()) {
                break;
            }
        }
        if (spot.isEmpty()) {
            if (digger != null || retry == 0) {
                LOG.info("{}: no {} within {} blocks of the objective, looked for from {} stands it can walk to", name, target,
                        rings[rings.length - 1], walkable.size());
            }
            digger = null;
            retry = RETRY_TICKS;
            exhausted = true;
            return;
        }
        exhausted = false;
        digger = new Digger(name, session, inventory, claims, spot.get(), Digger.walkTicks(soldier, spot.get().stand()));
    }

    /**
     * A log of a tree cut at {@code cut}: the first of a tree takes in the rest of it - its logs joined
     * to it, within {@link #TREE_REACH} across - and its stump, the lowest; each after it comes off.
     */
    private void felled(WorldView world, BlockPos cut) {
        tree.remove(cut);
        if (!tree.isEmpty() || stump != null) {
            return;
        }
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>(List.of(cut));
        Set<BlockPos> seen = new HashSet<>(List.of(cut));
        BlockPos lowest = cut;
        while (!open.isEmpty() && tree.size() < TREE_LOGS) {
            BlockPos at = open.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = at.offset(dx, dy, dz);
                        if (Math.abs(next.x() - cut.x()) > TREE_REACH || Math.abs(next.z() - cut.z()) > TREE_REACH
                                || !seen.add(next) || !world.isLog(next.x(), next.y(), next.z())) {
                            continue;
                        }
                        tree.add(next);
                        open.add(next);
                        if (next.y() < lowest.y()) {
                            lowest = next;
                        }
                    }
                }
            }
        }
        stump = lowest;
        if (!tree.isEmpty()) {
            LOG.info("{} fells the tree at {}: {} more log(s)", name, stump, tree.size());
        }
    }

    /** Plants a sapling where the felled tree stood, on its soil, if it carries one. */
    private void replant(WorldView world) {
        BlockPos cell = stump;
        stump = null;
        if (!world.isSoil(cell.x(), cell.y() - 1, cell.z()) || world.isSolid(cell.x(), cell.y(), cell.z()) || TreeFarm.planted(world, cell)) {
            return;
        }
        int slot = -1;
        for (int s = 0; s < Inventory.SLOTS && slot < 0; s++) {
            if (ItemClassRegistry.classOf(inventory.itemAt(s)).equals(Optional.of(ItemClass.SAPLING))) {
                slot = s;
            }
        }
        if (slot < 0) {
            LOG.info("{} has no sapling to replant the tree at {} - its leaves will drop some as they fall", name, cell);
            return;
        }
        int item = inventory.itemAt(slot);
        String sapling = ItemClassRegistry.nameOf(item).orElse("sapling");
        plantAt = cell;
        replanting = new Script(name, List.of(
                new Step("walk to the stump at " + cell, () -> {
                }, new Expect("standing on the stump", () -> soldierAt != null
                        && Math.hypot(soldierAt.x() - (cell.x() + 0.5), soldierAt.z() - (cell.z() + 0.5)) < 0.6
                        && Math.abs(soldierAt.y() - cell.y()) < 0.6), PLANT_WALK_TICKS),
                new Step(sapling + " in hand", () -> heldItem() == item, () -> takeInHand(item),
                        new Expect(sapling + " in hand", () -> heldItem() == item), ANSWER_TICKS),
                new Step("plant " + sapling + " at " + cell, () -> TreeFarm.planted(world, cell), () -> {
                    session.useBlockFace(cell.x(), cell.y() - 1, cell.z(), BlockFace.UP);
                    LOG.info("{} replants the tree at {} with {}", name, cell, sapling);
                }, new Expect("a sapling at " + cell, () -> TreeFarm.planted(world, cell)), ANSWER_TICKS)));
    }

    private int heldItem() {
        return inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot());
    }

    /** Holds {@code item}: from the hotbar, or swapped into a spare hotbar slot. */
    private void takeInHand(int item) {
        int slot = -1;
        for (int s = 0; s < Inventory.SLOTS && slot < 0; s++) {
            if (inventory.itemAt(s) == item) {
                slot = s;
            }
        }
        if (slot < 0) {
            throw new IllegalStateException("no " + ItemClassRegistry.nameOf(item).orElse("sapling") + " carried - " + inventory.summary());
        }
        int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : inventory.spareHotbarSlot();
        if (slot < Inventory.HOTBAR_FIRST) {
            session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
    }
}
