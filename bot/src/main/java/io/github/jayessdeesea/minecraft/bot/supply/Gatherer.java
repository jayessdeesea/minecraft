package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
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

    /** Trees this soldier failed at (out of reach, or the log wouldn't break) - not tried again. */
    private final Set<BlockPos> avoid = new HashSet<>();
    private int retry;
    private boolean reportedFull;
    private boolean exhausted;

    public Gatherer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return digger != null;
    }

    /** Whether the woods around the objective are worked out: the last search found nothing in reach. */
    public synchronized boolean exhausted() {
        return exhausted;
    }

    /** Where the soldier should be standing while it gathers, if anywhere. */
    public synchronized Optional<Position> standAt() {
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /**
     * Each tick: gathers {@code resource} around {@code around} while {@code wanted}; empty
     * {@code resource} means the squad isn't gathering.
     */
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
        if (inventory.count(item) >= load) {
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
                digger.release();
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
        // Stands it can get to from where it is - on foot, on the blocks it carries, or cutting steps,
        // as its route there would: a stand on paper can be shut off on every side, and one down a
        // pit it was filling gets out on its blocks.
        int span = (int) Math.ceil(Math.hypot(around.x() - soldier.x(), around.z() - soldier.z())) + WIDER[WIDER.length - 1] + 4;
        Set<BlockPos> walkable = Pathfinder.climbs(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), span, REACH_BUDGET,
                inventory.count(ItemClass.COBBLESTONE) + inventory.count(ItemClass.DIRT), inventory.slotOf(ItemClass.PICKAXE) >= 0).keySet();
        excluded.addAll(avoid);
        // Nearest first, then further out: none within reach of the objective is a reason to look
        // wider before asking for help - live, a camp long since cleared of trees had a wood a
        // minute's walk off, and its squad stood reporting CANTCO.
        Optional<Digger.Spot> spot = Optional.empty();
        // Not for dirt, which is everywhere: one with none near isn't sent a long way for it. Live,
        // a soldier digging dirt for its squad's hut by night went forty blocks off for it, met a
        // witch out there, and died.
        int[] rings = target == Digger.Target.SOIL ? new int[]{WIDER[0]} : WIDER;
        for (int radius : rings) {
            spot = Digger.findSpot(world, BlockPos.containing(around.x(), around.y(), around.z()), excluded, target, radius, walkable::contains);
            if (spot.isPresent()) {
                break;
            }
        }
        if (spot.isEmpty()) {
            if (digger != null || retry == 0) {
                LOG.info("{}: no {} within {} blocks of the objective", name, target, rings[rings.length - 1]);
            }
            digger = null;
            retry = RETRY_TICKS;
            exhausted = true;
            return;
        }
        exhausted = false;
        digger = new Digger(name, session, inventory, claims, spot.get());
    }
}
