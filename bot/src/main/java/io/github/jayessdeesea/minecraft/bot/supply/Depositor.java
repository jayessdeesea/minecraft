package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Guard;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One soldier carrying gathered materials to the stockpile - the supply point's chest
 * (docs/military-doctrine.md §12): walk to it, open it, shift-click in the material it has a full
 * load of, close it. Only that: a soldier quarrying stone keeps the logs it carries, because its
 * wooden pickaxe wears out in 59 blocks and the next one is made from them. What the chest holds
 * once it's done is reported ({@code stockpileSeen}), so the chain of command knows the stockpile
 * without anyone counting it by hand. Driven like {@link SupplyRun}, by the soldier's tick and its
 * connection's container updates; synchronized for the same reason.
 *
 * <p>An expect script (docs/architecture.md § Behavior modeling): the chest is open when its contents arrive,
 * and the load is in when the inventory, as the chest's window shows it, holds none of it. A chest
 * too full to take it all fails the run loudly. A trip that stops being wanted on the way - before
 * the chest is open - is interrupted.
 */
public final class Depositor {

    private static final Logger LOG = LogManager.getLogger(Depositor.class);

    static final int WALK_TIMEOUT_TICKS = 600;
    static final int COOLDOWN_TICKS = 200;

    private final String name;
    private final Inventory inventory;
    private final ChestWindow chest;
    private final Consumer<Stockpile> stockpileSeen;

    private Script script;
    private int cooldown;
    private Position at;
    private Position soldierAt;
    private boolean wanted;
    private boolean walking;
    private Set<ItemClass> load = Set.of();

    public Depositor(String name, PlaySession session, Inventory inventory, Consumer<Stockpile> stockpileSeen) {
        this.name = name;
        this.inventory = inventory;
        this.chest = new ChestWindow(session, inventory);
        this.stockpileSeen = stockpileSeen;
    }

    public synchronized boolean active() {
        return script != null;
    }

    /** Where the soldier should go while it carries its load in. */
    public synchronized Optional<Position> standAt() {
        return script != null && walking ? Optional.of(at) : Optional.empty();
    }

    /**
     * Each tick: starts a trip to {@code stockpile} when {@code wanted} (a full load, nothing near),
     * and advances one under way.
     */
    public synchronized void tick(Position soldier, Optional<Position> stockpile, boolean wanted) {
        this.soldierAt = soldier;
        this.wanted = wanted;
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            if (!wanted || cooldown > 0 || stockpile.isEmpty()) {
                return;
            }
            at = stockpile.get();
            LOG.info("{} carries a load to the stockpile at {} - {}", name, at, inventory.summary());
            script = trip();
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                Stockpile seen = new Stockpile(chest.chestCount(ItemClass.LOG), chest.chestCount(ItemClass.COBBLESTONE));
                LOG.info("{} stockpiled its load - the chest holds {} logs, {} cobblestone", name, seen.logs(), seen.stone());
                stockpileSeen.accept(seen);
                finish();
            }
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't stockpile its load: {}: {}", name, failed.step(), failed.reason());
                finish();
            }
        }
    }

    private Script trip() {
        walking = true;
        return new Script(name, List.of(
                new Step("walk to the stockpile", () -> walking = true,
                        new Expect("the chest in reach", () -> ChestWindow.withinReach(soldierAt, at)), WALK_TIMEOUT_TICKS),
                new Step("open the chest", () -> {
                    walking = false;
                    chest.open(at);
                }, new Expect("the chest's contents", chest::isOpen), ChestWindow.ANSWER_TICKS),
                new Step("put the load in", this::deposit,
                        new Expect("the load in the chest", () -> load.stream().allMatch(c -> inventory.count(c) == 0)),
                        ChestWindow.ANSWER_TICKS)),
                List.of(new Guard("the trip is no longer wanted", () -> wanted || !walking)));
    }

    /** Shift-clicks across every stack of the material there's a full load of. */
    private void deposit() {
        load = EnumSet.noneOf(ItemClass.class);
        if (inventory.count(ItemClass.LOG) >= Gatherer.CARRY_LOGS) {
            load.add(ItemClass.LOG);
        }
        if (inventory.count(ItemClass.COBBLESTONE) >= Gatherer.CARRY_STONE) {
            load.add(ItemClass.COBBLESTONE);
        }
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            Optional<ItemClass> itemClass = ItemClassRegistry.classOf(inventory.itemAt(slot));
            if (itemClass.isPresent() && load.contains(itemClass.get())) {
                chest.quickMove(chest.windowSlotOf(slot));
            }
        }
    }

    public synchronized void onContainer(ContainerUpdate update) {
        if (script != null) {
            chest.onContainer(update);
        }
    }

    private void finish() {
        chest.close();
        script = null;
        walking = false;
        load = Set.of();
        cooldown = COOLDOWN_TICKS;
    }
}
