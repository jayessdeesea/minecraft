package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * A server with one 27-slot chest, answering as the real one does, a tick after it's asked: opening
 * it sends the window and its contents; a shift-click moves the stack and sends both slots; a click
 * in the inventory window with a stale state id resends the whole inventory. A {@link #silent}
 * server never answers.
 */
final class FakeChestServer {

    static final int CHEST_SLOTS = 27;
    private static final int WINDOW = 7;

    final Inventory inventory = new Inventory();
    final int[] chestItems = new int[CHEST_SLOTS];
    final int[] chestCounts = new int[CHEST_SLOTS];
    final List<String> log = new ArrayList<>();
    boolean silent;
    private final List<Runnable> answers = new ArrayList<>();
    private Consumer<ContainerUpdate> client = update -> {
    };
    private int stateId = 1;

    FakeChestServer() {
        java.util.Arrays.fill(chestItems, -1);
    }

    void connect(Consumer<ContainerUpdate> client) {
        this.client = client;
    }

    /** Sends what's due this tick. */
    void tick() {
        List<Runnable> due = List.copyOf(answers);
        answers.clear();
        due.forEach(Runnable::run);
    }

    final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                log.add(method.getName() + (args == null ? "" : " " + java.util.Arrays.toString(args)));
                if (silent) {
                    return null;
                }
                switch (method.getName()) {
                    case "useBlock" -> answers.add(() -> {
                        client.accept(new ContainerUpdate.Opened(WINDOW, 2));
                        List<Optional<ItemSlot>> slots = new ArrayList<>();
                        for (int slot = 0; slot < CHEST_SLOTS + 36; slot++) {
                            slots.add(windowSlot(slot));
                        }
                        client.accept(new ContainerUpdate.Contents(WINDOW, stateId, slots, true));
                    });
                    case "quickMove" -> {
                        int window = (int) args[0];
                        int slot = (int) args[2];
                        answers.add(window == WINDOW ? () -> moveInChest(slot) : () -> wear(slot));
                    }
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        answers.add(() -> {
                            int[] a = stack(slot);
                            inventory.setSlot(slot, stack(hotbar));
                            inventory.setSlot(hotbar, a);
                        });
                    }
                    case "closeContainer", "selectHotbarSlot" -> {
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private int[] stack(int slot) {
        return inventory.itemAt(slot) < 0 ? null : new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
    }

    private Optional<ItemSlot> windowSlot(int slot) {
        int item = slot < CHEST_SLOTS ? chestItems[slot] : inventory.itemAt(slot - CHEST_SLOTS + Inventory.MAIN_FIRST);
        int count = slot < CHEST_SLOTS ? chestCounts[slot] : inventory.countAt(slot - CHEST_SLOTS + Inventory.MAIN_FIRST);
        return item < 0 ? Optional.empty() : Optional.of(new ItemSlot(item, count, 0, false, Map.of()));
    }

    /** A shift-click in the chest's window: chest to inventory (hotbar end first), or inventory to chest. */
    private void moveInChest(int slot) {
        if (slot < CHEST_SLOTS) {
            for (int to = Inventory.HOTBAR_LAST; to >= Inventory.MAIN_FIRST; to--) {
                if (inventory.itemAt(to) < 0) {
                    int[] moving = {chestItems[slot], chestCounts[slot]};
                    chestItems[slot] = -1;
                    chestCounts[slot] = 0;
                    send(slot);
                    // Only the client's window learns of it; the client applies it to the inventory.
                    client.accept(new ContainerUpdate.Slot(WINDOW, ++stateId, to - Inventory.MAIN_FIRST + CHEST_SLOTS,
                            Optional.of(new ItemSlot(moving[0], moving[1], 0, false, Map.of()))));
                    return;
                }
            }
            return; // the inventory's full: nothing moves
        }
        int from = slot - CHEST_SLOTS + Inventory.MAIN_FIRST;
        for (int to = 0; to < CHEST_SLOTS; to++) {
            if (chestItems[to] < 0) {
                chestItems[to] = inventory.itemAt(from);
                chestCounts[to] = inventory.countAt(from);
                send(to);
                client.accept(new ContainerUpdate.Slot(WINDOW, ++stateId, slot, Optional.empty()));
                return;
            }
        }
    }

    private void send(int chestSlot) {
        client.accept(new ContainerUpdate.Slot(WINDOW, ++stateId, chestSlot, windowSlot(chestSlot)));
    }

    /** A shift-click on armour in the inventory window: onto the body, the whole inventory resent. */
    private void wear(int slot) {
        OptionalInt fits = ItemClassRegistry.armourSlot(inventory.itemAt(slot));
        if (fits.isPresent()) {
            inventory.setSlot(fits.getAsInt(), stack(slot));
            inventory.setSlot(slot, null);
        }
    }
}
