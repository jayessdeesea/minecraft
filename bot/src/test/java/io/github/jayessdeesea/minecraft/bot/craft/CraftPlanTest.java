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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CraftPlanTest {

    @Test
    void twentyTorchesAreOneFurnaceLoadAndOneRoundInTheGrid() {
        CraftPlan plan = CraftPlan.of(Map.of(ItemClass.TORCH, 20), Map.of(ItemClass.LOG, 8, ItemClass.COBBLESTONE, 8), Set.of());

        assertTrue(plan.feasible(), plan.short_().toString());
        assertEquals(List.of(
                new Session(Station.GRID, List.of(new Batch(Recipe.PLANKS, 3), new Batch(Recipe.CRAFTING_TABLE, 1), new Batch(Recipe.STICKS, 2))),
                new Session(Station.TABLE, List.of(new Batch(Recipe.FURNACE, 1))),
                new Session(Station.FURNACE, List.of(new Batch(Recipe.CHARCOAL, 5))),
                new Session(Station.GRID, List.of(new Batch(Recipe.TORCHES, 5)))), plan.sessions());
    }

    @Test
    void aSwordAndAPickaxeAreMadeAtOneOpeningOfTheTable() {
        CraftPlan plan = CraftPlan.of(Map.of(ItemClass.WEAPON, 1, ItemClass.PICKAXE, 1), Map.of(ItemClass.LOG, 5), Set.of());

        assertTrue(plan.feasible(), plan.short_().toString());
        assertEquals(1, plan.sessions().stream().filter(s -> s.station() == Station.TABLE).count());
        assertEquals(Set.of(new Batch(Recipe.WOODEN_SWORD, 1), new Batch(Recipe.WOODEN_PICKAXE, 1)),
                Set.copyOf(plan.sessions().stream().filter(s -> s.station() == Station.TABLE).findFirst().orElseThrow().batches()));
    }

    @Test
    void aStationInReachIsUsedNotMadeAndWhatsCarriedIsntMadeAgain() {
        CraftPlan plan = CraftPlan.of(Map.of(ItemClass.WEAPON, 1), Map.of(ItemClass.PLANKS, 2, ItemClass.STICK, 1),
                Set.of(Workstation.CRAFTING_TABLE));

        assertEquals(List.of(new Session(Station.TABLE, List.of(new Batch(Recipe.WOODEN_SWORD, 1)))), plan.sessions());
    }

    @Test
    void whatNothingCarriedCanMakeIsShort() {
        CraftPlan plan = CraftPlan.of(Map.of(ItemClass.TORCH, 20), Map.of(ItemClass.LOG, 1), Set.of());

        assertEquals(Map.of(ItemClass.LOG, 7, ItemClass.COBBLESTONE, 8), plan.short_());
    }

    @Test
    void nothingToDoWhenItsAlreadyCarried() {
        assertTrue(CraftPlan.of(Map.of(ItemClass.TORCH, 4), Map.of(ItemClass.TORCH, 9), Set.of()).nothingToDo());
    }
}
