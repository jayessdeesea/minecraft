package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

/**
 * One soldier eating (docs/military-doctrine.md §4, Class I): below the food level health
 * regenerates at, out of contact, with food in its pack, it takes the food in hand, eats it -
 * the server times the meal - and takes its weapon back. The food goes into a hotbar slot holding
 * no weapon or tool, as {@link Builder}'s blocks do, so the soldier stays armed. Driven by the
 * soldier's tick.
 */
public final class Rations {

    private static final Logger LOG = LogManager.getLogger(Rations.class);

    /** Ticks to hold the food: vanilla's 32-tick meal, with a margin for the server's own timing. */
    static final int EAT_TICKS = 40;
    static final int SETTLE_TICKS = 6;
    /** Before trying again after a meal that didn't raise the food level. */
    static final int COOLDOWN_TICKS = 200;

    private enum State { IDLE, TO_HAND, EATING }

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;

    private State state = State.IDLE;
    private int ticksInState;
    private int cooldown;
    private int previousHotbar = -1;
    private int foodBefore;

    public Rations(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
    }

    public synchronized boolean active() {
        return state != State.IDLE;
    }

    /**
     * Each tick: starts a meal when {@code food} (the server's 0-20) is below the regeneration level
     * and the soldier is {@code free} to stop; advances one under way. A meal interrupted - the
     * soldier no longer free - is abandoned and its weapon taken back.
     */
    public synchronized void tick(int food, boolean free, float yaw, float pitch) {
        ticksInState++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (state != State.IDLE && !free) {
            finish(0);
            return;
        }
        switch (state) {
            case IDLE -> {
                int slot = inventory.slotOf(ItemClass.FOOD);
                if (free && cooldown == 0 && food < Condition.REGENERATION_FOOD && slot >= 0) {
                    int hotbar = spareHotbarSlot();
                    previousHotbar = inventory.heldHotbarSlot();
                    if (slot != Inventory.HOTBAR_FIRST + hotbar) {
                        session.swapWithHotbar(0, 0, slot, hotbar);
                    }
                    session.selectHotbarSlot(hotbar);
                    inventory.selectHotbar(hotbar);
                    foodBefore = food;
                    enter(State.TO_HAND);
                }
            }
            case TO_HAND -> {
                if (ticksInState >= SETTLE_TICKS) {
                    int held = inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot());
                    if (!ItemClassRegistry.classOf(held).equals(Optional.of(ItemClass.FOOD))) {
                        LOG.warn("{}: no food in hand to eat", name);
                        finish(COOLDOWN_TICKS);
                        return;
                    }
                    session.useItem(yaw, pitch);
                    enter(State.EATING);
                }
            }
            case EATING -> {
                if (ticksInState >= EAT_TICKS) {
                    if (food > foodBefore) {
                        LOG.info("{} ate - food {} -> {}", name, foodBefore, food);
                        finish(0);
                    } else {
                        LOG.warn("{} ate but food stayed at {}", name, food);
                        finish(COOLDOWN_TICKS);
                    }
                }
            }
        }
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
        enter(State.IDLE);
    }

    private void enter(State next) {
        state = next;
        ticksInState = 0;
    }
}
