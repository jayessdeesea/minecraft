package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Full, a pack sheds dirt beyond two stacks before anything else it carries - never the iron
     * (operator, 2026-11-15: a soldier with twelve raw iron carried 586 dirt); with the dirt down to
     * its keep, cobblestone beyond five stacks.
     */
    @Test
    void aFullPackShedsDirtBeforeCobblestoneAndNeverTheIron() {
        Inventory inventory = new Inventory();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.COBBLESTONE), 64});
        }
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.RAW_IRON), 12});
        for (int slot = Inventory.MAIN_FIRST + 1; slot <= Inventory.MAIN_FIRST + 3; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.DIRT), 64});
        }

        Optional<Integer> first = Pack.surplus(inventory, ItemClass.DIRT, Pack.DIRT_KEPT);
        assertTrue(first.isPresent() && inventory.itemAt(first.get()) == item(ItemClass.DIRT), "a stack of the three of dirt");
        inventory.setSlot(first.get(), null);
        assertEquals(Optional.empty(), Pack.surplus(inventory, ItemClass.DIRT, Pack.DIRT_KEPT), "two stacks kept");
        Optional<Integer> next = Pack.surplus(inventory, ItemClass.COBBLESTONE, Pack.COBBLESTONE_KEPT);
        assertTrue(next.isPresent() && inventory.itemAt(next.get()) == item(ItemClass.COBBLESTONE));
        assertTrue(inventory.itemAt(next.get()) != item(ItemClass.RAW_IRON) && inventory.itemAt(first.get()) != item(ItemClass.RAW_IRON),
                "the iron stays");
    }

    /** Two part-stacks of dirt merge, the smaller onto the larger; two pickaxes, which don't stack, never do. */
    @Test
    void partStacksMergeAndToolsAreLeftAlone() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.DIRT), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 5, new int[]{item(ItemClass.DIRT), 40});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item(ItemClass.PICKAXE), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 3, new int[]{item(ItemClass.PICKAXE), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 4, new int[]{item(ItemClass.COBBLESTONE), 64});
        inventory.setSlot(Inventory.MAIN_FIRST + 6, new int[]{item(ItemClass.COBBLESTONE), 64});

        int[] merge = Pack.mergeable(inventory, (a, b) -> false).orElseThrow();
        assertEquals(Inventory.MAIN_FIRST, merge[0], "the one dirt");
        assertEquals(Inventory.MAIN_FIRST + 5, merge[1], "onto the forty");
        assertEquals(Optional.empty(), Pack.mergeable(inventory, (a, b) -> a == Inventory.MAIN_FIRST),
                "nothing else: the pickaxes don't stack, the cobblestone's stacks are full");
    }

    /** Dirt thrown from a full pack and picked straight back up isn't thrown again for a while. */
    @Test
    void bulkThrownAndPickedBackUpIsntThrownAgainForAWhile() {
        Inventory inventory = new Inventory();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.DIRT), 64});
        }
        java.util.List<Integer> throws_ = new java.util.ArrayList<>();
        io.github.jayessdeesea.minecraft.protocol.PlaySession session = (io.github.jayessdeesea.minecraft.protocol.PlaySession)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{io.github.jayessdeesea.minecraft.protocol.PlaySession.class}, (proxy, method, args) -> {
                            if (method.getName().equals("throwStack")) {
                                throws_.add((Integer) args[2]); // picked straight back up: the pack stays as it was
                            }
                            return null;
                        });
        Pack pack = new Pack("Dana", session, inventory);

        for (int tick = 0; tick < 2000; tick++) {
            pack.tick(true);
        }

        assertTrue(throws_.size() <= 2, "thrown once or so in 100 s, not every time it came back: " + throws_.size());
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
