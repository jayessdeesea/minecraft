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

    /** Minerals aren't junk: a nearly full pack sheds rotten flesh, never redstone or lapis (operator, 2026-11-15). */
    @Test
    void aNearlyFullPackKeepsItsMinerals() {
        Inventory inventory = new Inventory();
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.TORCH), 16});
        }
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.MINERAL), 1});

        assertEquals(Optional.empty(), Pack.unused(inventory), "the redstone kept");
    }

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

    /**
     * Seeds past what it keeps, thrown and picked straight back up, aren't thrown again for a while: live (field617), Pete
     * stood farming and threw 62 seeds every 3 to 6 s, 22 times, picking each throw back up where it fell.
     */
    @Test
    void surplusSeedsThrownAndPickedBackUpArentThrownAgainForAWhile() {
        Inventory inventory = new Inventory();
        for (int slot = Inventory.MAIN_FIRST; slot < Inventory.MAIN_FIRST + 5; slot++) {
            inventory.setSlot(slot, new int[]{item(ItemClass.SEEDS), 64});
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
        Pack pack = new Pack("Pete", session, inventory);

        for (int tick = 0; tick < 2000; tick++) {
            pack.tick(true);
        }

        assertTrue(throws_.size() <= 2, "thrown once or so in 100 s, not every time it came back: " + throws_.size());
    }

    /**
     * The hotbar as the operator keeps it: the sword first, the pickaxe second, food second to
     * last, torches last; an emerald in the slots between goes to the pack.
     */
    /**
     * The tools of the trade a switch away (operator, 2026-11-22): from a pack in no order, the sword, the pickaxe, the
     * shovel and the axe each in its slot, dirt - before cobblestone - in the blocks' slot, and the layout then kept.
     */
    @Test
    void theHotbarHoldsTheToolsOfTheTradeAndBlocksToStopAFlow() {
        Inventory inventory = new Inventory();
        int sword = DiggerTest.item("stone_sword"), pickaxe = DiggerTest.item("stone_pickaxe"), shovel = DiggerTest.item("stone_shovel"),
                axe = DiggerTest.item("stone_axe");
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{axe, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item(ItemClass.COBBLESTONE), 64});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{shovel, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 3, new int[]{item(ItemClass.DIRT), 12});
        inventory.setSlot(Inventory.MAIN_FIRST + 4, new int[]{pickaxe, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 5, new int[]{sword, 1});

        int swaps = 0;
        for (; swaps < 20; swaps++) {
            Optional<Hotbar.Swap> swap = Hotbar.next(inventory);
            if (swap.isEmpty()) {
                break;
            }
            int a = swap.get().slot(), b = Inventory.HOTBAR_FIRST + swap.get().hotbar();
            int[] at = inventory.itemAt(a) < 0 ? null : new int[]{inventory.itemAt(a), inventory.countAt(a)};
            int[] to = inventory.itemAt(b) < 0 ? null : new int[]{inventory.itemAt(b), inventory.countAt(b)};
            inventory.setSlot(a, to);
            inventory.setSlot(b, at);
        }

        assertTrue(swaps < 20, "the layout settles");
        assertEquals(sword, inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.WEAPON));
        assertEquals(pickaxe, inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.PICKAXE));
        assertEquals(shovel, inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.SHOVEL));
        assertEquals(axe, inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.AXE));
        assertEquals(item(ItemClass.DIRT), inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.BLOCKS), "dirt before cobblestone");
    }

    /** With no sword, the axe is the weapon and stays in the weapon's slot: the axe's slot doesn't take it, back and forth. */
    @Test
    void anAxeThatIsTheWeaponStaysInTheWeaponsSlot() {
        Inventory inventory = new Inventory();
        int axe = DiggerTest.item("stone_axe");
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{axe, 1});

        Hotbar.Swap first = Hotbar.next(inventory).orElseThrow();
        inventory.setSlot(first.slot(), null);
        inventory.setSlot(Inventory.HOTBAR_FIRST + first.hotbar(), new int[]{axe, 1});

        assertEquals(Hotbar.WEAPON, first.hotbar());
        assertEquals(Optional.empty(), Hotbar.next(inventory), "kept there");
    }

    @Test
    void theHotbarIsLaidOutSwordPickaxeFoodTorchesAndNothingElse() {
        Inventory inventory = new Inventory();
        int sword = IntStream.range(0, 4096).filter(id -> ItemClassRegistry.meleeRank(id).isPresent()).findFirst().orElseThrow();
        int pickaxe = IntStream.range(0, 4096).filter(id -> ItemClassRegistry.miningTier(id).isPresent()).findFirst().orElseThrow();
        int emerald = IntStream.range(1, 4096).filter(id -> ItemClassRegistry.classOf(id).isEmpty()).findFirst().orElseThrow();
        inventory.setSlot(Inventory.HOTBAR_FIRST, new int[]{item(ItemClass.TORCH), 16});
        inventory.setSlot(Inventory.HOTBAR_FIRST + 3, new int[]{emerald, 1});
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{sword, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{pickaxe, 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item(ItemClass.FOOD), 8});

        for (int i = 0; i < 10; i++) {
            Optional<Hotbar.Swap> swap = Hotbar.next(inventory);
            if (swap.isEmpty()) {
                break;
            }
            int a = swap.get().slot(), b = Inventory.HOTBAR_FIRST + swap.get().hotbar();
            int[] at = inventory.itemAt(a) < 0 ? null : new int[]{inventory.itemAt(a), inventory.countAt(a)};
            int[] to = inventory.itemAt(b) < 0 ? null : new int[]{inventory.itemAt(b), inventory.countAt(b)};
            inventory.setSlot(a, to);
            inventory.setSlot(b, at);
        }

        assertEquals(sword, inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.WEAPON));
        assertEquals(pickaxe, inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.PICKAXE));
        assertEquals(item(ItemClass.FOOD), inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.FOOD));
        assertEquals(item(ItemClass.TORCH), inventory.itemAt(Inventory.HOTBAR_FIRST + Hotbar.TORCHES));
        assertTrue(IntStream.rangeClosed(2, 6).noneMatch(h -> inventory.itemAt(Inventory.HOTBAR_FIRST + h) == emerald), "the emerald in the pack");
        assertEquals(Optional.empty(), Hotbar.next(inventory), "kept: nothing more to swap");
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
