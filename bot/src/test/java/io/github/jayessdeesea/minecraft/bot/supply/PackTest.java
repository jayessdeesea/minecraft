package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackTest {

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    /** An item no routine uses: one of no class. */
    private static final int JUNK = IntStream.range(1, 4096).filter(id -> ItemClassRegistry.classOf(id).isEmpty()).findFirst().orElseThrow();

    @Test
    void aNearlyFullPackShedsTheSmallestStackOfWhatNothingUses() {
        Inventory inventory = new Inventory();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.TORCH), 16});
        }
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{JUNK, 12});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{JUNK, 3});
        inventory.setSlot(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot(), new int[]{JUNK, 1}); // in hand: kept

        assertEquals(0, Pack.freeSlots(inventory));
        assertEquals(Optional.of(Inventory.MAIN_FIRST + 1), Pack.unused(inventory), "the three, not the torches, nor what's in hand");
    }

    @Test
    void whatItThrewAndPickedBackUpIsntThrownAgainForAWhile() {
        Inventory inventory = new Inventory();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.TORCH), 16});
        }
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{JUNK, 3});
        java.util.List<Integer> throws_ = new java.util.ArrayList<>();
        io.github.jayessdeesea.minecraft.protocol.PlaySession session = (io.github.jayessdeesea.minecraft.protocol.PlaySession)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{io.github.jayessdeesea.minecraft.protocol.PlaySession.class}, (proxy, method, args) -> {
                            if (method.getName().equals("throwStack")) {
                                throws_.add((Integer) args[2]); // thrown, and picked straight back up: the slot stays as it was
                            }
                            return null;
                        });
        Pack pack = new Pack("Carol", session, inventory);

        for (int tick = 0; tick < 2000; tick++) {
            pack.tick(true);
        }

        assertEquals(1, throws_.size(), "thrown once in 100 s, not every time it came back");
    }
}
