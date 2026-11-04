package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * One soldier drawing kit from the supply point's chest: open it, take what
 * {@link Quartermaster#take} picks, close it, then put the kit on ({@link Quartermaster#equip}).
 * Driven by the soldier's tick ({@link #tick}) and by its connection's container updates
 * ({@link #onContainer}); synchronized because those are two threads.
 *
 * <p>An expect script (AGENTS.md § Behavior modeling): the chest is open when its contents arrive,
 * the kit is drawn when the slots taken show empty, and it's on when the server's resent inventory
 * leaves {@link Quartermaster#equip} nothing to do. The server applies each shift-click itself, so
 * the clicks go out back to back and one answer confirms them all.
 *
 * <p>After a run - kit drawn, or a fault - the soldier waits {@link #COOLDOWN_TICKS} before trying
 * again, so an empty chest isn't hammered.
 */
public final class SupplyRun {

    private static final Logger LOG = LogManager.getLogger(SupplyRun.class);

    /** Close enough to use the chest. */
    public static final double REACH = ChestWindow.REACH;
    static final int COOLDOWN_TICKS = 600;

    private final String name;
    private final Inventory inventory;
    private final PlaySession session;
    private final ChestWindow chest;
    private final Runnable kitChanged;

    private Script script;
    private int cooldown;
    private List<Integer> taking = List.of();

    public SupplyRun(String name, PlaySession session, Inventory inventory, Runnable kitChanged) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.chest = new ChestWindow(session, inventory);
        this.kitChanged = kitChanged;
    }

    /** Whether a run is under way - while it is, the soldier isn't free to do anything else with its hands. */
    public synchronized boolean active() {
        return script != null;
    }

    /**
     * Each tick: starts a run when the soldier stands within {@link #REACH} of {@code supplyPoint}
     * and {@code wanted} (it's short of kit and free to draw it), and advances a run in progress.
     */
    public synchronized void tick(Position soldier, Optional<Position> supplyPoint, boolean wanted) {
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            if (!wanted || cooldown > 0 || supplyPoint.isEmpty() || !ChestWindow.withinReach(soldier, supplyPoint.get())) {
                return;
            }
            LOG.info("{} draws kit at the supply point {}", name, supplyPoint.get());
            script = draw(supplyPoint.get());
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> finish();
            case Script.Status.Interrupted _ -> finish();
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't draw kit: {}: {}", name, failed.step(), failed.reason());
                finish();
            }
        }
    }

    private Script draw(Position at) {
        return new Script(name, List.of(
                new Step("open the supply chest", () -> chest.open(at),
                        new Expect("the chest's contents", chest::isOpen), ChestWindow.ANSWER_TICKS),
                new Step("take the kit", () -> {
                    List<Integer> items = chest.chestItems();
                    taking = Quartermaster.take(items, inventory);
                    LOG.info("{} takes chest slot(s) {} of {}", name, taking, items);
                    taking.forEach(chest::quickMove);
                }, new Expect("the slots taken emptied", () -> taking.stream().allMatch(slot -> chest.chestItems().get(slot) < 0)),
                        ChestWindow.ANSWER_TICKS),
                new Step("put the kit on", () -> Quartermaster.equip(inventory).isEmpty(), this::equip,
                        new Expect("the kit worn and the weapon in hand", () -> Quartermaster.equip(inventory).isEmpty()),
                        ChestWindow.ANSWER_TICKS)));
    }

    public synchronized void onContainer(ContainerUpdate update) {
        if (script != null && chest.onContainer(update)) {
            kitChanged.run();
        }
    }

    private void equip() {
        chest.close();
        for (Quartermaster.Step step : Quartermaster.equip(inventory)) {
            LOG.info("{} {}", name, step);
            switch (step) {
                // State ID 0 is almost certainly stale: the server then resends the whole
                // inventory, which confirms what the soldier is wearing and holding.
                case Quartermaster.Step.Wear(int slot) -> session.quickMove(0, 0, slot);
                case Quartermaster.Step.Hold(int hotbarSlot) -> {
                    session.selectHotbarSlot(hotbarSlot);
                    inventory.selectHotbar(hotbarSlot);
                }
                case Quartermaster.Step.BringToHand(int slot, int hotbarSlot) -> session.swapWithHotbar(0, 0, slot, hotbarSlot);
            }
        }
    }

    private void finish() {
        chest.close();
        kitChanged.run();
        script = null;
        taking = List.of();
        cooldown = COOLDOWN_TICKS;
    }
}
