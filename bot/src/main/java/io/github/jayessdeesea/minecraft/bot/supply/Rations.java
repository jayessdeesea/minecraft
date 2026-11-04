package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Guard;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * One soldier eating (docs/military-doctrine.md §4, Class I): below the food level health
 * regenerates at, out of contact, with food in its pack, it takes the food in hand, eats it -
 * the server times the meal - and takes its weapon back. The food goes into a hotbar slot holding
 * no weapon or tool, as {@link Builder}'s blocks do, so the soldier stays armed.
 *
 * <p>An expect script (AGENTS.md § Behavior modeling): the food is in hand when the server's
 * resent inventory says so, and eaten when its food level rises. Driven by the soldier's tick.
 */
public final class Rations {

    private static final Logger LOG = LogManager.getLogger(Rations.class);

    /** For the server to resend the inventory after a swap. */
    static final int ANSWER_TICKS = 40;
    /** For a meal to raise the food level: vanilla's 32-tick meal, with a margin for the server's own timing. */
    static final int EAT_TICKS = 50;
    /** Before trying again after a meal that failed. */
    static final int COOLDOWN_TICKS = 200;

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private Script script;
    private int cooldown;
    private int previousHotbar = -1;
    private int food;
    private boolean free;
    private float yaw;
    private float pitch;

    public Rations(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    public synchronized boolean active() {
        return script != null;
    }

    /**
     * Each tick: starts a meal when {@code food} (the server's 0-20) is below the regeneration level
     * and the soldier is {@code free} to stop; advances one under way. A meal interrupted - the
     * soldier no longer free - ends with the weapon back in hand and no backoff.
     */
    public synchronized void tick(int food, boolean free, float yaw, float pitch) {
        this.food = food;
        this.free = free;
        this.yaw = yaw;
        this.pitch = pitch;
        if (cooldown > 0) {
            cooldown--;
        }
        if (script == null) {
            if (!free || cooldown > 0 || food >= Condition.REGENERATION_FOOD || inventory.slotOf(ItemClass.FOOD) < 0) {
                return;
            }
            script = meal(food);
        }
        switch (script.tick()) {
            case Script.Status.Running _ -> {
            }
            case Script.Status.Done _ -> {
                LOG.info("{} ate - food now {}", name, this.food);
                finish(0);
            }
            case Script.Status.Interrupted _ -> finish(0);
            case Script.Status.Failed failed -> {
                LOG.warn("{} couldn't eat: {}: {}", name, failed.step(), failed.reason());
                finish(COOLDOWN_TICKS);
            }
        }
    }

    private Script meal(int foodBefore) {
        previousHotbar = inventory.heldHotbarSlot();
        return new Script(name, List.of(
                new Step("take food in hand", this::foodInHand, () -> {
                    int slot = inventory.slotOf(ItemClass.FOOD);
                    if (slot < 0) {
                        throw new IllegalStateException("no food in the pack - " + inventory.summary());
                    }
                    int hotbar = spareHotbarSlot();
                    if (slot != Inventory.HOTBAR_FIRST + hotbar) {
                        session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
                    }
                    session.selectHotbarSlot(hotbar);
                    inventory.selectHotbar(hotbar);
                }, new Expect("food in hand", this::foodInHand), ANSWER_TICKS),
                new Step("eat", () -> session.useItem(yaw, pitch),
                        new Expect("the food level to rise above " + foodBefore, () -> food > foodBefore), EAT_TICKS)),
                List.of(new Guard("no longer free to stop", () -> free)));
    }

    private boolean foodInHand() {
        int held = inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot());
        return ItemClassRegistry.classOf(held).equals(Optional.of(ItemClass.FOOD));
    }

    /** A hotbar slot holding no weapon or tool - the last such - for the food. */
    private int spareHotbarSlot() {
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || held.get() != ItemClass.WEAPON && held.get() != ItemClass.PICKAXE) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon or a tool");
    }

    private void finish(int cooldownTicks) {
        if (previousHotbar >= 0) {
            session.selectHotbarSlot(previousHotbar);
            inventory.selectHotbar(previousHotbar);
            previousHotbar = -1;
        }
        cooldown = cooldownTicks;
        script = null;
    }
}
