package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LadderTest {

    private static final Predicate<ItemClass> LOGS_ONLY = c -> c == ItemClass.LOG;
    private static final Predicate<ItemClass> ANYTHING = c -> true;
    private static final Set<Workstation> NONE = Set.of();

    private final Inventory inventory = new Inventory();
    private int nextSlot = Inventory.MAIN_FIRST;

    private LadderTest carrying(String item, int count) {
        int id = IntStream.range(0, 4096).filter(i -> ItemClassRegistry.nameOf(i).equals(Optional.of(item))).findFirst().orElseThrow();
        inventory.setSlot(nextSlot++, new int[]{id, count});
        return this;
    }

    private LadderTest wearing(String item, int slot) {
        int id = IntStream.range(0, 4096).filter(i -> ItemClassRegistry.nameOf(i).equals(Optional.of(item))).findFirst().orElseThrow();
        inventory.setSlot(slot, new int[]{id, 1});
        return this;
    }

    @Test
    void aBareHandedSoldierWithCobblestoneMakesAStoneSwordNotAWoodenOne() {
        carrying("oak_log", 2).carrying("cobblestone", 16);

        assertEquals(Optional.of(Recipe.STONE_SWORD), Ladder.weapon(inventory, Map.of(), NONE, LOGS_ONLY));
    }

    @Test
    void withoutCobblestoneItMakesAWoodenSword() {
        carrying("oak_log", 2);

        assertEquals(Optional.of(Recipe.WOODEN_SWORD), Ladder.weapon(inventory, Map.of(), NONE, LOGS_ONLY));
    }

    @Test
    void cobblestoneSetAsideForThePatrolBaseIsntSpentOnASword() {
        carrying("oak_log", 2).carrying("cobblestone", 10);

        assertEquals(Optional.of(Recipe.WOODEN_SWORD), Ladder.weapon(inventory, Map.of(ItemClass.COBBLESTONE, 10), NONE, LOGS_ONLY));
    }

    @Test
    void anArmedSoldierNeedsNoWeaponButClimbsToStone() {
        carrying("wooden_sword", 1).carrying("oak_log", 2).carrying("cobblestone", 16);

        assertEquals(Optional.empty(), Ladder.weapon(inventory, Map.of(), NONE, LOGS_ONLY));
        assertEquals(Optional.of(Recipe.STONE_SWORD), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY));
    }

    @Test
    void aBetterWeaponHoldsTheRungsBelowIt() {
        carrying("iron_sword", 1).carrying("oak_log", 2).carrying("cobblestone", 16);

        assertEquals(Optional.of(Recipe.STONE_PICKAXE), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY));
    }

    @Test
    void ironWaitsOnOreItCanDig() {
        carrying("stone_sword", 1).carrying("stone_pickaxe", 1).carrying("oak_log", 4).carrying("cobblestone", 16);

        assertEquals(Optional.empty(), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY), "no ore in reach: nothing more to make");
        assertEquals(Optional.of(Recipe.BOW), Ladder.next(inventory, Map.of(), NONE, ANYTHING), "string to be had, a bow first");
        assertEquals(Optional.of(Recipe.SHIELD), Ladder.next(inventory, Map.of(), NONE, c -> c != ItemClass.STRING));
    }

    @Test
    void aBowWaitsOnStringCarried() {
        carrying("stone_sword", 1).carrying("oak_log", 2).carrying("string", 3);

        assertEquals(Optional.of(Recipe.BOW), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY));
    }

    @Test
    void armourGoesOnChestplateFirstAndWhatsWornCounts() {
        carrying("stone_sword", 1).carrying("stone_pickaxe", 1).carrying("shield", 1).carrying("iron_ingot", 30).carrying("oak_log", 4);

        assertEquals(Optional.of(Recipe.IRON_CHESTPLATE), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY));
        wearing("iron_chestplate", 6).wearing("diamond_leggings", 7);
        assertEquals(Optional.of(Recipe.IRON_BOOTS), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY));
        wearing("leather_boots", 8);
        assertEquals(Optional.of(Recipe.IRON_BOOTS), Ladder.next(inventory, Map.of(), NONE, LOGS_ONLY), "leather isn't as good as iron");
    }

    @Test
    void noHigherThanTheRungItsToldToStopAt() {
        carrying("stone_sword", 1).carrying("stone_pickaxe", 1).carrying("iron_ingot", 30).carrying("oak_log", 4);

        assertEquals(Optional.empty(), Ladder.upTo(inventory, Map.of(), NONE, LOGS_ONLY, Ladder.rungOf(Recipe.STONE_PICKAXE)),
                "a squadmate still wants a stone pickaxe: nothing above it");
        assertEquals(Optional.of(Recipe.SHIELD), Ladder.upTo(inventory, Map.of(), NONE, LOGS_ONLY, Ladder.rungOf(Recipe.SHIELD)));
    }

    @Test
    void aRungThatCantBeHadIsPassedOverNotWaitedOn() {
        // No cobblestone to be had for a stone pickaxe, but iron in hand for what comes after.
        carrying("stone_sword", 1).carrying("shield", 1).carrying("iron_ingot", 4).carrying("oak_log", 4);

        assertEquals(Optional.of(Recipe.IRON_BOOTS), Ladder.next(inventory, Map.of(), Set.of(Workstation.CRAFTING_TABLE), LOGS_ONLY),
                "short of ingots for a chestplate or leggings, but boots take four");
    }
}
