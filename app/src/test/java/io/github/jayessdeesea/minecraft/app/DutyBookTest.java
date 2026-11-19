package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.DutyOrder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DutyBookTest {

    /**
     * Two squads on one farm share its plot as one: no two harvesters on the same columns, one baker. Live (field473),
     * Alpha's and Delta's first harvesters were both given x -31..-20 of the camp's farm.
     */
    @Test
    void twoSquadsOnOneFarmShareItsPlotAsOne() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int z = 0; z < 4; z++) {
                plot.add(new BlockPos(x, 63, z));
            }
        }
        FarmPlan farm = new FarmPlan(new BlockPos(6, 62, 2), plot);
        Map<String, FarmPlan> farming = Map.of("Alice", farm, "Bob", farm, "Mia", farm, "Olga", farm);

        Map<String, DutyOrder> alpha = DutyBook.given(List.of("Alice", "Bob"), Map.of(), farming, Optional.empty());
        Map<String, DutyOrder> delta = DutyBook.given(List.of("Mia", "Olga"), Map.of(), farming, Optional.empty());

        assertEquals(alpha, delta, "each squad's leader reckons the one set of shares");
        assertNotEquals(alpha.get("Alice").ground(), alpha.get("Mia").ground());
        assertEquals(1, alpha.values().stream().filter(o -> o.duty() == DutyOrder.Duty.BAKER).count(), "one baker to the farm");
    }

    /**
     * A squad's strips found cut out are its own woodcutters', found after each was given its strip. Live (field476),
     * every squad marked Charlie's strips as its own.
     */
    @Test
    void aSquadsStripsCutOutAreItsOwnWoodcuttersFoundAfterTheyWereGiven() {
        DutyBook book = new DutyBook();
        io.github.jayessdeesea.minecraft.command.Box strip = new io.github.jayessdeesea.minecraft.command.Box(8, 56, -24, 24, 88, 24);
        book.assign("Charlie", List.of("Ivy"), Map.of("Ivy", new DutyOrder("Ivy", DutyOrder.Duty.WOODCUTTER, Optional.of(strip), Optional.empty())),
                Optional.of("WOOD"), 100);
        Optional<io.github.jayessdeesea.minecraft.command.Objective> wood = Optional.of(new io.github.jayessdeesea.minecraft.command.Objective(
                "Wood", new io.github.jayessdeesea.minecraft.command.Position(0, 64, 0), "logs",
                new io.github.jayessdeesea.minecraft.command.Objective.Task.Gather(io.github.jayessdeesea.minecraft.command.Resource.WOOD)));

        assertEquals(List.of(), book.cutOut("Charlie", List.of("Ivy"), wood, n -> 50), "found nothing before it was given the strip");
        assertEquals(List.of(), book.cutOut("Alpha", List.of("Alice"), wood, n -> 200), "Charlie's woodcutter isn't Alpha's");
        assertEquals(List.of(strip), book.cutOut("Charlie", List.of("Ivy"), wood, n -> 200));
    }
}
