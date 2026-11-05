package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.craft.Stock;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A soldier making its own kit (docs/military-doctrine.md §11, §12): the weapon, tools and armour
 * it's asked for - by the equipment ladder ({@link io.github.jayessdeesea.minecraft.bot.craft.Ladder})
 * or by an order that needs a pickaxe - several at one opening of the table when it's asked for
 * several. It digs what the plan is short of and it can dig itself - logs by hand, cobblestone
 * and iron ore with a good enough pickaxe - then a {@link Crafter} makes the rest, and whatever
 * was made is put on: armour worn, the best weapon in hand. Driven by the soldier's tick
 * ({@link #tick}) and its connection's container updates ({@link #onContainer}); synchronized
 * because those are two threads.
 *
 * <p>A hostile near pauses it, digging or crafting - the soldier fights with what it has, and
 * picks up where it left off once clear. The exception is a first weapon, finished first when the
 * table is already open (see {@link Crafter}). Something it can't find or reach to dig is a fault:
 * it says why ({@link #failure}) and waits {@link #COOLDOWN_TICKS} before looking again - unless
 * the materials turn up meanwhile (a squadmate hands some over, §13).
 */
public final class Armourer {

    private static final Logger LOG = LogManager.getLogger(Armourer.class);

    static final int COOLDOWN_TICKS = 1200;

    /** What it digs for each material it can fetch itself, in the order it fetches them (the classes' order). */
    private static final Map<ItemClass, Digger.Target> DUG = new EnumMap<>(Map.of(
            ItemClass.LOG, Digger.Target.LOG, ItemClass.COBBLESTONE, Digger.Target.STONE, ItemClass.RAW_IRON, Digger.Target.IRON_ORE));

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Runnable kitChanged;
    private final Set<BlockPos> claims;
    private final Crafter crafter;

    private Digger digger;
    private Set<Recipe> making = Set.of();
    private int cooldown;
    private String failure;

    public Armourer(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Runnable kitChanged) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.kitChanged = kitChanged;
        this.crafter = new Crafter(name, session, inventory);
    }

    public synchronized boolean active() {
        return digger != null || crafter.active();
    }

    /**
     * Why the last attempt failed, while the soldier waits out its cooldown - so it can say it
     * can't make what it needs rather than look idle.
     */
    public synchronized Optional<String> failure() {
        return cooldown > 0 && failure != null ? Optional.of(failure) : crafter.failure();
    }

    /** What's being made, while {@link #active}. */
    public synchronized Set<Recipe> making() {
        return active() ? making : Set.of();
    }

    /** Where the soldier should be standing while the routine runs, if it needs to be somewhere. */
    public synchronized Optional<Position> standAt() {
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /** Logs it's short of to make {@code wanted} from what it carries, with no table to hand. */
    public static int logsShort(Inventory inventory, Set<Recipe> wanted) {
        return CraftPlan.of(once(wanted), Stock.of(inventory), Set.of()).short_().getOrDefault(ItemClass.LOG, 0);
    }

    /**
     * Whether the soldier could fetch {@code material} itself: logs always - a tree that can't be
     * found is the dig's fault, said when it happens - and stone or ore when its pickaxe is good
     * enough for it and some lies within reach of {@code soldier}.
     */
    public static boolean canDig(ItemClass material, Inventory inventory, WorldView world, Position soldier, Set<BlockPos> claims) {
        Digger.Target target = DUG.get(material);
        if (target == null || !target.canDig(inventory)) {
            return false;
        }
        return target == Digger.Target.LOG
                || Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims, target).isPresent();
    }

    /**
     * Each tick: makes {@code wanted} - empty when the soldier needs nothing, or isn't free to make
     * it - digging first for what it's short of. {@code threatened} pauses it.
     */
    public synchronized void tick(Position soldier, WorldView world, Set<Recipe> wanted, boolean threatened) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!wanted.equals(making)) {
            stopDigging();
            making = Set.copyOf(wanted);
        }
        Map<Recipe, Integer> items = once(making);
        if (crafter.active() || making.isEmpty()) {
            made(crafter.tick(soldier, world, items, threatened));
            return;
        }
        if (threatened) {
            stopDigging(); // back to it once clear
            return;
        }
        CraftPlan plan = Crafter.plan(items, inventory, world, soldier);
        if (plan.nothingToDo()) {
            return;
        }
        Map<ItemClass, Integer> short_ = plan.short_();
        if (short_.isEmpty()) {
            stopDigging();
            made(crafter.tick(soldier, world, items, false));
            return;
        }
        Optional<ItemClass> next = DUG.keySet().stream().filter(short_::containsKey).findFirst();
        if (!short_.keySet().stream().allMatch(DUG::containsKey) || next.isEmpty()) {
            giveUp("short of " + short_ + " - " + inventory.summary());
        } else if (digger != null || cooldown == 0) {
            dig(soldier, world, DUG.get(next.get()), short_.get(next.get()));
        }
    }

    public synchronized void onContainer(ContainerUpdate update) {
        crafter.onContainer(update);
    }

    private void made(Crafter.Outcome outcome) {
        if (outcome != Crafter.Outcome.MADE) {
            return;
        }
        for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
            switch (step) {
                case Quartermaster.Step.Wear(int slot) -> session.quickMove(0, 0, slot);
                case Quartermaster.Step.Hold(int hotbarSlot) -> {
                    session.selectHotbarSlot(hotbarSlot);
                    inventory.selectHotbar(hotbarSlot);
                }
                case Quartermaster.Step.BringToHand(int slot, int hotbarSlot) -> session.swapWithHotbar(0, 0, slot, hotbarSlot);
            }
        }
        LOG.info("{} made {}", name, describe(making));
        failure = null;
        kitChanged.run();
    }

    /** The next block of {@code target}, toward the {@code count} it's short of. */
    private void dig(Position soldier, WorldView world, Digger.Target target, int count) {
        if (digger == null) {
            if (!target.canDig(inventory)) {
                giveUp("no pickaxe good enough to dig " + target + " - " + inventory.summary());
                return;
            }
            Optional<Digger.Spot> spot = Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims, target);
            if (spot.isEmpty()) {
                giveUp("no " + target + " within " + Digger.SEARCH_RADIUS + " blocks");
                return;
            }
            LOG.info("{} goes to dig {} for {} - {} short - at {}", name, target, describe(making), count, spot.get().block());
            digger = new Digger(name, session, inventory, claims, spot.get());
        }
        switch (digger.tick(soldier, world)) {
            case Digger.Progress.Working _ -> {
            }
            case Digger.Progress.Done _ -> stopDigging(); // the next tick plans again from what's carried now
            case Digger.Progress.Failed(String why) -> giveUp(why);
        }
    }

    private void stopDigging() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }

    private void giveUp(String why) {
        LOG.warn("{} gives up making {} - {}", name, describe(making), why);
        failure = "can't make " + describe(making) + ": " + why;
        stopDigging();
        cooldown = COOLDOWN_TICKS;
    }

    private static String describe(Set<Recipe> recipes) {
        return recipes.stream().map(Recipe::name).sorted().collect(Collectors.joining(" and "));
    }

    private static Map<Recipe, Integer> once(Set<Recipe> recipes) {
        Map<Recipe, Integer> items = new LinkedHashMap<>();
        recipes.forEach(r -> items.put(r, 1));
        return items;
    }
}
