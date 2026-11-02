package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RationsTest {

    private final List<String> calls = new ArrayList<>();
    private final Inventory inventory = new Inventory();
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = stack(slot);
                        inventory.setSlot(slot, stack(hotbar));
                        inventory.setSlot(hotbar, moving);
                        calls.add("swap " + slot + " to hotbar " + args[3]);
                    }
                    case "selectHotbarSlot" -> calls.add("select " + args[0]);
                    case "useItem" -> calls.add("eat");
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private int[] stack(int slot) {
        return inventory.itemAt(slot) < 0 ? null : new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
    }

    private static int itemOf(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    RationsTest() {
        inventory.setSlot(Inventory.HOTBAR_FIRST, new int[]{itemOf(ItemClass.WEAPON), 1});
        inventory.setSlot(20, new int[]{itemOf(ItemClass.FOOD), 5});
    }

    @Test
    void aHungrySoldierEatsAndTakesItsWeaponBack() {
        Rations rations = new Rations("Alice", session, inventory);
        int food = 14;
        for (int tick = 0; tick < Rations.SETTLE_TICKS + Rations.EAT_TICKS + 2; tick++) {
            if (calls.contains("eat")) {
                food = 20; // the server finished the meal
            }
            rations.tick(food, true, 0, 0);
        }

        assertEquals(List.of("swap 20 to hotbar 8", "select 8", "eat", "select 0"), calls);
        assertFalse(rations.active());
        assertEquals(0, inventory.heldHotbarSlot());
    }

    @Test
    void aFedSoldierOrOneThatIsntFreeDoesntEat() {
        Rations rations = new Rations("Alice", session, inventory);

        rations.tick(19, true, 0, 0);
        rations.tick(10, false, 0, 0);

        assertEquals(List.of(), calls);
    }

    @Test
    void aMealInterruptedPutsTheWeaponBackInHand() {
        Rations rations = new Rations("Alice", session, inventory);

        rations.tick(10, true, 0, 0);
        assertTrue(rations.active());
        rations.tick(10, false, 0, 0); // a hostile came near

        assertEquals(List.of("swap 20 to hotbar 8", "select 8", "select 0"), calls);
        assertFalse(rations.active());
    }
}
