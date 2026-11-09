package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.supply.Fitter;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FitterTest {

    private static final Position SOLDIER = new Position(0.5, 64, 0.5);
    private static final Workshop WORKSHOP = new Workshop(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(0, 64, 1));

    private final FakeCraftingServer server = new FakeCraftingServer();
    private final Fitter fitter = new Fitter("Dana", server.session, server.inventory, new java.util.HashMap<>());

    FitterTest() {
        server.client = fitter::onContainer;
        server.give(Inventory.HOTBAR_FIRST, ItemClass.WEAPON, 1);
    }

    private boolean fit(Set<Workstation> wanted, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.tick();
            if (!fitter.tick(SOLDIER, server.world, WORKSHOP, wanted, false)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void aMissingTableIsMadeInTheGridAndPutIntoTheCellTheWorkshopNames() {
        server.give(9, ItemClass.LOG, 1);

        assertTrue(fit(Set.of(Workstation.CRAFTING_TABLE), 400), "fitted in time");

        assertEquals(java.util.Optional.empty(), fitter.failure());
        assertEquals(Workstation.CRAFTING_TABLE, server.stations.get(WORKSHOP.table()));
        assertEquals(1, server.stations.size(), "nothing else put down");
    }

    @Test
    void aFurnaceWantedFitsTheTableFirstThenTheFurnaceMadeAtIt() {
        server.give(9, ItemClass.LOG, 1).give(10, ItemClass.COBBLESTONE, 8);

        assertTrue(fit(Set.of(Workstation.FURNACE), 800), "fitted in time");

        assertEquals(java.util.Optional.empty(), fitter.failure());
        assertEquals(Workstation.CRAFTING_TABLE, server.stations.get(WORKSHOP.table()));
        assertEquals(Workstation.FURNACE, server.stations.get(WORKSHOP.furnace()));
    }

    @Test
    void shortOfTheMakingsItSaysWhatItLacks() {
        assertTrue(fit(Set.of(Workstation.CRAFTING_TABLE), 10));

        assertTrue(fitter.failure().orElseThrow().contains("LOG"), fitter.failure().orElseThrow());
        assertFalse(fitter.active());
    }
}
