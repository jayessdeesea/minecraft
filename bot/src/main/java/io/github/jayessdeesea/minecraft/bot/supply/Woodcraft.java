package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A soldier making itself wooden tools (docs/military-doctrine.md §11, §12): a sword when it's
 * bare-handed, a pickaxe when it's sent for stone - both at one opening of the table when it needs
 * both. It chops the logs the plan is short of, then a {@link Crafter} makes the rest, and a sword
 * is taken in hand. Driven by the soldier's tick ({@link #tick}) and its connection's container
 * updates ({@link #onContainer}); synchronized because those are two threads.
 *
 * <p>A hostile near pauses it, chopping or crafting - the soldier fights with what it has, and
 * picks up where it left off once clear. The exception is the sword itself, finished first when the
 * table is already open (see {@link Crafter}). A tree it can't find or reach is a fault: it says
 * why ({@link #failure}) and waits {@link #COOLDOWN_TICKS} before looking again - unless the wood
 * turns up meanwhile (a squadmate hands some over, §13).
 */
public final class Woodcraft {

    private static final Logger LOG = LogManager.getLogger(Woodcraft.class);

    /** What it can make, and what class of item that is. */
    public enum Tool {
        /** Held once made. */
        WOODEN_SWORD(ItemClass.WEAPON),
        WOODEN_PICKAXE(ItemClass.PICKAXE);

        final ItemClass makes;

        Tool(ItemClass makes) {
            this.makes = makes;
        }
    }

    static final int COOLDOWN_TICKS = 1200;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Runnable kitChanged;
    private final Set<BlockPos> claims;
    private final Crafter crafter;

    private Digger digger;
    private Set<Tool> tools = Set.of();
    private int cooldown;
    private String failure;

    public Woodcraft(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Runnable kitChanged) {
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

    /** The tools being made, while {@link #active}. */
    public synchronized Set<Tool> tools() {
        return active() ? tools : Set.of();
    }

    /** Where the soldier should be standing while the routine runs, if it needs to be somewhere. */
    public synchronized Optional<Position> standAt() {
        return digger == null ? Optional.empty() : Optional.of(digger.standAt());
    }

    /** Logs it's short of to make {@code tools} from what it carries, with no table to hand. */
    public static int logsShort(Inventory inventory, Set<Tool> tools) {
        return CraftPlan.of(wanted(tools), Crafter.carried(inventory), Set.of()).short_().getOrDefault(ItemClass.LOG, 0);
    }

    /**
     * Each tick: makes {@code wanted} - empty when the soldier needs nothing, or isn't free to make
     * it - chopping first if it's short of wood. {@code threatened} pauses it.
     */
    public synchronized void tick(Position soldier, WorldView world, Set<Tool> wanted, boolean threatened) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (!wanted.equals(tools)) {
            stopChopping();
            tools = Set.copyOf(wanted);
        }
        Map<ItemClass, Integer> items = wanted(tools);
        if (crafter.active() || tools.isEmpty()) {
            made(crafter.tick(soldier, world, items, threatened));
            return;
        }
        if (threatened) {
            stopChopping(); // back to it once clear
            return;
        }
        CraftPlan plan = Crafter.plan(items, inventory, world, soldier);
        if (plan.nothingToDo()) {
            return;
        }
        Map<ItemClass, Integer> short_ = plan.short_();
        if (short_.isEmpty()) {
            stopChopping();
            made(crafter.tick(soldier, world, items, false));
        } else if (!short_.keySet().equals(Set.of(ItemClass.LOG))) {
            giveUp("short of " + short_ + " - " + inventory.summary());
        } else if (digger != null || cooldown == 0) {
            chop(soldier, world, short_.get(ItemClass.LOG));
        }
    }

    public synchronized void onContainer(ContainerUpdate update) {
        crafter.onContainer(update);
    }

    private void made(Crafter.Outcome outcome) {
        if (outcome != Crafter.Outcome.MADE) {
            return;
        }
        if (tools.contains(Tool.WOODEN_SWORD)) {
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
        }
        LOG.info("{} made {}", name, tools);
        failure = null;
        kitChanged.run();
    }

    /** The next log, toward the {@code logs} it's short of. */
    private void chop(Position soldier, WorldView world, int logs) {
        if (digger == null) {
            Optional<Digger.Spot> next = Digger.findSpot(world, BlockPos.containing(soldier.x(), soldier.y(), soldier.z()), claims,
                    Digger.Target.LOG);
            if (next.isEmpty()) {
                giveUp("no tree within " + Digger.SEARCH_RADIUS + " blocks");
                return;
            }
            LOG.info("{} goes to chop wood for {} - {} logs short - at {}", name, tools, logs, next.get().block());
            digger = new Digger(session, inventory, claims, next.get());
        }
        switch (digger.tick(soldier, world)) {
            case Digger.Progress.Working working -> {
            }
            case Digger.Progress.Done done -> stopChopping(); // the next tick plans again from what's carried now
            case Digger.Progress.Failed(String why) -> giveUp(why);
        }
    }

    private void stopChopping() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }

    private void giveUp(String why) {
        LOG.warn("{} gives up making {} - {}", name, tools, why);
        failure = "can't make " + tools.stream().map(t -> t.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '))
                .collect(java.util.stream.Collectors.joining(" and ")) + ": " + why;
        stopChopping();
        cooldown = COOLDOWN_TICKS;
    }

    static Map<ItemClass, Integer> wanted(Set<Tool> tools) {
        Map<ItemClass, Integer> items = new EnumMap<>(ItemClass.class);
        tools.forEach(t -> items.put(t.makes, 1));
        return items;
    }
}
