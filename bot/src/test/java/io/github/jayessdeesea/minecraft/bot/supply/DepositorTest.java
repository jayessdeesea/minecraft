package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepositorTest {

    private static final Position CHEST = new Position(0, 64, 0);
    private static final Position BESIDE = new Position(1.5, 64, 0.5);
    private static final Position FAR = new Position(20, 64, 0);

    private final FakeChestServer server = new FakeChestServer();
    private final List<Stockpile> seen = new ArrayList<>();
    private final Depositor depositor = new Depositor("Bob", server.session, server.inventory, seen::add);

    DepositorTest() {
        server.connect(depositor::onContainer);
    }

    private static int itemOf(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    private void run(int ticks, Position soldier, boolean wanted) {
        for (int i = 0; i < ticks; i++) {
            server.tick();
            depositor.tick(soldier, Optional.of(CHEST), wanted);
        }
    }

    @Test
    void walksOverAndPutsInTheFullLoadOnlyThenReportsTheStockpile() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), 20});
        server.inventory.setSlot(11, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE - 20});
        server.inventory.setSlot(12, new int[]{itemOf(ItemClass.LOG), 3}); // pickaxe wood: kept
        server.chestItems[0] = itemOf(ItemClass.LOG);
        server.chestCounts[0] = 10;

        run(3, FAR, true);
        assertEquals(Optional.of(CHEST), depositor.standAt());
        run(10, BESIDE, true);

        assertFalse(depositor.active());
        assertEquals(0, server.inventory.count(ItemClass.COBBLESTONE));
        assertEquals(3, server.inventory.count(ItemClass.LOG));
        assertEquals(List.of(new Stockpile(10, Gatherer.CARRY_STONE)), seen);
    }

    @Test
    void aTripNoLongerWantedOnTheWayIsDropped() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE});

        run(2, FAR, true);
        assertTrue(depositor.active());
        run(1, FAR, false);

        assertFalse(depositor.active());
        assertEquals(List.of(), server.log);
    }

    @Test
    void aChestTooFullToTakeTheLoadFailsLoudly() {
        server.inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), Gatherer.CARRY_STONE});
        for (int slot = 0; slot < FakeChestServer.CHEST_SLOTS; slot++) {
            server.chestItems[slot] = itemOf(ItemClass.LOG);
            server.chestCounts[slot] = 64;
        }

        run(ChestWindow.ANSWER_TICKS + 5, BESIDE, true);

        assertFalse(depositor.active());
        assertEquals(List.of(), seen);
        assertEquals(Gatherer.CARRY_STONE, server.inventory.count(ItemClass.COBBLESTONE));
    }
}
