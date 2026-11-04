package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One soldier opening a doorway out of the squad's patrol base (docs/military-doctrine.md §14):
 * breaking the wall blocks in the way, from inside, where it stands. Which blocks is
 * {@code command.PatrolBase}'s; this only breaks them. With a pickaxe a cobblestone wall block takes
 * 1.5 seconds; bare-handed, 10 - the server's break time, with a margin, since it times every dig.
 *
 * <p>An expect script (AGENTS.md § Behavior modeling): the pickaxe is in hand when the server's
 * resent inventory says so, and a block is broken when the server's block update says so. A block
 * the server won't break fails the run loudly; it's tried again after a pause. Driven by the
 * soldier's tick.
 */
public final class Doorway {

    private static final Logger LOG = LogManager.getLogger(Doorway.class);

    /** Cobblestone, hardness 2: a wooden pickaxe (speed 2) breaks it in 30 ticks. */
    static final int PICKAXE_TICKS = 40;
    /** By hand, the wrong tool: speed 1 over hardness 2, at a hundredth a tick - 200 ticks. */
    static final int HAND_TICKS = 220;
    /** For the server to answer: the inventory resent, a block update. */
    static final int ANSWER_TICKS = 40;
    /** Before trying again after a block the server wouldn't break. */
    static final int COOLDOWN_TICKS = 100;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private Script script;
    private List<BlockPos> scripted;
    private WorldView world;
    private int cooldown;
    private int previousHotbar = -1;
    private int ticks;
    private int digStarted;
    private int digTicks;

    public Doorway(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    /** Each tick, breaking the first of {@code cells} still solid; true once none is. */
    public synchronized boolean tick(WorldView world, List<BlockPos> cells) {
        this.world = world;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (cells.stream().noneMatch(this::solid)) {
            stop();
            return true;
        }
        if (script != null && !cells.equals(scripted)) {
            stop();
        }
        if (script == null) {
            if (cooldown > 0) {
                return false;
            }
            script = open(cells);
            scripted = List.copyOf(cells);
        }
        switch (script.tick()) {
            case Script.Status.Running _, Script.Status.Interrupted _ -> {
            }
            case Script.Status.Done _ -> stop(); // a block put back meanwhile gets a fresh run
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't open the doorway: {}: {}", name, failed.step(), failed.reason());
                stop();
                cooldown = COOLDOWN_TICKS;
            }
        }
        return false;
    }

    private Script open(List<BlockPos> cells) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("pickaxe in hand", () -> inventory.slotOf(ItemClass.PICKAXE) < 0 || pickaxeInHand(), () -> {
            int pickaxe = inventory.slotOf(ItemClass.PICKAXE);
            previousHotbar = inventory.heldHotbarSlot();
            int hotbar = pickaxe >= Inventory.HOTBAR_FIRST ? pickaxe - Inventory.HOTBAR_FIRST : spareHotbarSlot();
            if (pickaxe < Inventory.HOTBAR_FIRST) {
                session.swapWithHotbar(0, 0, pickaxe, hotbar); // stale state: the server resends the inventory
            }
            session.selectHotbarSlot(hotbar);
            inventory.selectHotbar(hotbar);
        }, new Expect("the pickaxe in hand", this::pickaxeInHand), ANSWER_TICKS));
        for (BlockPos cell : cells) {
            // The server times the dig itself: finishing early is refused, so wait out its break time.
            steps.add(new Step("dig " + cell, () -> !solid(cell), () -> {
                digTicks = pickaxeInHand() ? PICKAXE_TICKS : HAND_TICKS;
                digStarted = ticks;
                session.startDigging(cell.x(), cell.y(), cell.z());
            }, new Expect("the break time to pass", () -> ticks - digStarted >= digTicks), HAND_TICKS + 1));
            steps.add(new Step("break " + cell, () -> !solid(cell), () -> session.finishDigging(cell.x(), cell.y(), cell.z()),
                    new Expect(cell + " broken", () -> !solid(cell)), ANSWER_TICKS));
        }
        return new Script(name, steps);
    }

    private boolean solid(BlockPos cell) {
        return world.isSolid(cell.x(), cell.y(), cell.z());
    }

    private boolean pickaxeInHand() {
        return ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()))
                .equals(Optional.of(ItemClass.PICKAXE));
    }

    /** Stops: whatever was in hand before goes back. */
    public synchronized void stop() {
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
        script = null;
        scripted = null;
    }

    private int spareHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || held.get() != ItemClass.WEAPON) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon");
    }
}
