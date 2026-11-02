package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Kit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryTest {

    private static final int IRON_SWORD = 1045;
    private static final int ARROW = 1009;
    private static final int IRON_HELMET = 1080;
    private static final int ROTTEN_FLESH = 1264;

    @Test
    void anEmptyInventoryIsABareHandedSoldier() {
        assertEquals(new Kit(Optional.empty(), 0, 0), new Inventory().kit());
    }

    @Test
    void countsTheHeldWeaponArmourWornAndArrowsAnywhere() {
        Inventory inventory = new Inventory();
        List<int[]> slots = new ArrayList<>();
        for (int i = 0; i < Inventory.SLOTS; i++) {
            slots.add(null);
        }
        slots.set(5, new int[]{IRON_HELMET, 1});   // worn
        slots.set(20, new int[]{IRON_HELMET, 1});  // carried, not worn
        slots.set(12, new int[]{ARROW, 16});
        slots.set(40, new int[]{ARROW, 5});
        slots.set(37, new int[]{IRON_SWORD, 1});   // hotbar slot 1
        slots.set(36, new int[]{ROTTEN_FLESH, 3}); // hotbar slot 0

        assertEquals(new Kit(Optional.empty(), 21, 1), inventory.replaceAll(slots), "holding rotten flesh");
        assertEquals(new Kit(Optional.of("iron_sword"), 21, 1), inventory.selectHotbar(1));
        assertEquals(new Kit(Optional.empty(), 21, 1), inventory.setSlot(37, null), "sword dropped");
    }

    @Test
    void featherFallingAndProtectionOnWornArmourProtectFromFalls() {
        Inventory inventory = new Inventory();
        inventory.setSlot(8, new int[]{IRON_HELMET, 1}, java.util.Map.of("minecraft:feather_falling", 4, "minecraft:protection", 2));
        inventory.setSlot(20, new int[]{IRON_HELMET, 1}, java.util.Map.of("minecraft:protection", 4)); // carried: doesn't count

        Kit kit = inventory.kit();

        assertEquals(14, kit.fallProtection());
        assertEquals(1 - 14 / 25.0, kit.fallDamageFactor(), 1e-9);
        inventory.setSlot(8, null);
        assertEquals(0, inventory.kit().fallProtection());
    }
}
