package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan.Batch;
import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan.Session;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe.Station;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftPlanTest {

    /** A workshop's stations, both in reach. */
    private static final Set<Workstation> WORKSHOP = Set.of(Workstation.CRAFTING_TABLE, Workstation.FURNACE);

    @Test
    void twentyTorchesAreOneFurnaceLoadAndOneRoundInTheGrid() {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.TORCHES, 20), Stock.of(Map.of(ItemClass.LOG, 7)), WORKSHOP);

        assertTrue(plan.feasible(), plan.short_().toString());
        assertEquals(List.of(
                new Session(Station.GRID, List.of(new Batch(Recipe.PLANKS, 2), new Batch(Recipe.STICKS, 2))),
                new Session(Station.FURNACE, List.of(new Batch(Recipe.CHARCOAL, 5))),
                new Session(Station.GRID, List.of(new Batch(Recipe.TORCHES, 5)))), plan.sessions());
    }

    @Test
    void aStationNotInReachIsMissingNotMadeAndThePlanWaitsForTheWorkshop() {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.WOODEN_PICKAXE, 1), Stock.of(Map.of(ItemClass.LOG, 3)), Set.of());

        assertFalse(plan.feasible());
        assertEquals(Map.of(), plan.short_());
        assertEquals(Set.of(Workstation.CRAFTING_TABLE), plan.missing());
        assertFalse(plan.makes(ItemClass.CRAFTING_TABLE), "no table made to put down");
    }

    @Test
    void aSwordAndAPickaxeAreMadeAtOneOpeningOfTheTable() {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.WOODEN_SWORD, 1, Recipe.WOODEN_PICKAXE, 1), Stock.of(Map.of(ItemClass.LOG, 5)), WORKSHOP);

        assertTrue(plan.feasible(), plan.short_().toString());
        assertEquals(1, plan.sessions().stream().filter(s -> s.station() == Station.TABLE).count());
        assertEquals(Set.of(new Batch(Recipe.WOODEN_SWORD, 1), new Batch(Recipe.WOODEN_PICKAXE, 1)),
                Set.copyOf(plan.sessions().stream().filter(s -> s.station() == Station.TABLE).findFirst().orElseThrow().batches()));
    }

    @Test
    void aStationInReachIsUsedNotMadeAndWhatsCarriedIsntMadeAgain() {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.WOODEN_SWORD, 1), Stock.of(Map.of(ItemClass.PLANKS, 2, ItemClass.STICK, 1)),
                Set.of(Workstation.CRAFTING_TABLE));

        assertEquals(List.of(new Session(Station.TABLE, List.of(new Batch(Recipe.WOODEN_SWORD, 1)))), plan.sessions());
    }

    @Test
    void whatNothingCarriedCanMakeIsShort() {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.TORCHES, 20), Stock.of(Map.of(ItemClass.LOG, 1)), WORKSHOP);

        assertEquals(Map.of(ItemClass.LOG, 6), plan.short_());
    }

    @Test
    void aGradedItemCountsOnlyItselfNotItsClass() {
        Stock woodenSword = new Stock(Map.of(ItemClass.WEAPON, 1, ItemClass.COBBLESTONE, 2, ItemClass.STICK, 1), Map.of("wooden_sword", 1));

        CraftPlan plan = CraftPlan.of(Map.of(Recipe.STONE_SWORD, 1), woodenSword, Set.of(Workstation.CRAFTING_TABLE));

        assertEquals(List.of(new Session(Station.TABLE, List.of(new Batch(Recipe.STONE_SWORD, 1)))), plan.sessions());
        assertTrue(CraftPlan.of(Map.of(Recipe.WOODEN_SWORD, 1), woodenSword, Set.of()).nothingToDo());
    }

    @Test
    void anIronChestplateIsEightOreSmeltedAtOneLoadThenCraftedAtTheTable() {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.IRON_CHESTPLATE, 1),
                Stock.of(Map.of(ItemClass.RAW_IRON, 8, ItemClass.LOG, 3)), WORKSHOP);

        assertTrue(plan.feasible(), plan.short_().toString());
        assertEquals(List.of(new Batch(Recipe.IRON_INGOT, 8)), plan.sessions().stream()
                .filter(s -> s.station() == Station.FURNACE).flatMap(s -> s.batches().stream()).toList());
        assertEquals(Station.TABLE, plan.sessions().getLast().station());
        assertEquals(Map.of(ItemClass.RAW_IRON, 1), CraftPlan.of(Map.of(Recipe.IRON_CHESTPLATE, 1),
                Stock.of(Map.of(ItemClass.RAW_IRON, 7, ItemClass.LOG, 3)), WORKSHOP).short_());
    }

    @Test
    void nothingToDoWhenItsAlreadyCarried() {
        assertTrue(CraftPlan.of(Map.of(Recipe.TORCHES, 4), Stock.of(Map.of(ItemClass.TORCH, 9)), Set.of()).nothingToDo());
    }
}
