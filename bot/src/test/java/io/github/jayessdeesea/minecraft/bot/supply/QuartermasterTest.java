package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuartermasterTest {

    private static final int BOW = 1008;
    private static final int STONE_SWORD = 1035;
    private static final int IRON_SWORD = 1045;
    private static final int IRON_AXE = 1048;
    private static final int IRON_HELMET = 1080;
    private static final int IRON_BOOTS = 1083;
    private static final int LEATHER_HELMET = 1068;
    private static final int ARROW = 1009;

    @Test
    void aBareSoldierTakesTheBestMeleeWeaponAndOnePieceForEachArmourSlot() {
        List<Integer> chest = List.of(BOW, STONE_SWORD, IRON_AXE, IRON_SWORD, IRON_HELMET, LEATHER_HELMET, IRON_BOOTS, ARROW, -1);

        assertEquals(List.of(3, 4, 6), Quartermaster.take(chest, new Inventory()));
    }

    @Test
    void aSoldierTakesOnlyWhatFillsAGap() {
        Inventory inventory = new Inventory();
        inventory.setSlot(40, new int[]{STONE_SWORD, 1}); // armed, if poorly
        inventory.setSlot(5, new int[]{LEATHER_HELMET, 1}); // head covered
        inventory.setSlot(20, new int[]{IRON_BOOTS, 1});   // boots carried

        assertEquals(List.of(), Quartermaster.take(List.of(IRON_SWORD, IRON_HELMET, IRON_BOOTS), inventory));
    }

    @Test
    void equipWearsCarriedArmourAndHoldsTheWeapon() {
        Inventory inventory = new Inventory();
        inventory.setSlot(42, new int[]{IRON_BOOTS, 1});
        inventory.setSlot(43, new int[]{IRON_HELMET, 1});
        inventory.setSlot(44, new int[]{IRON_SWORD, 1});

        assertEquals(List.of(new Quartermaster.Step.Wear(42), new Quartermaster.Step.Wear(43), new Quartermaster.Step.Hold(8)),
                Quartermaster.equip(inventory));
    }

    @Test
    void aWeaponInTheMainInventoryIsBroughtToHand() {
        Inventory inventory = new Inventory();
        inventory.selectHotbar(2);
        inventory.setSlot(9, new int[]{IRON_AXE, 1});

        assertEquals(List.of(new Quartermaster.Step.BringToHand(9, 2)), Quartermaster.equip(inventory));
    }
}
