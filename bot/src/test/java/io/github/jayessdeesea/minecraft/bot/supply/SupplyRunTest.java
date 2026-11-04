package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyRunTest {

    private static final Position CHEST = new Position(0, 64, 0);
    private static final Position BESIDE = new Position(1.5, 64, 0.5);

    private final FakeChestServer server = new FakeChestServer();
    private int kitChanges;
    private final SupplyRun run = new SupplyRun("Alice", server.session, server.inventory, () -> kitChanges++);

    SupplyRunTest() {
        server.connect(run::onContainer);
    }

    private static int sword() {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.meleeRank(id).isPresent()).findFirst().orElseThrow();
    }

    private static int helmet() {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.armourSlot(id).isPresent()).findFirst().orElseThrow();
    }

    private void run(int ticks) {
        for (int i = 0; i < ticks; i++) {
            server.tick();
            run.tick(BESIDE, Optional.of(CHEST), true);
        }
    }

    @Test
    void drawsAWeaponAndArmourAndPutsThemOn() {
        server.chestItems[3] = sword();
        server.chestCounts[3] = 1;
        server.chestItems[5] = helmet();
        server.chestCounts[5] = 1;

        run(10);

        assertFalse(run.active());
        assertEquals(-1, server.chestItems[3]);
        assertEquals(-1, server.chestItems[5]);
        assertTrue(ItemClassRegistry.meleeRank(server.inventory.itemAt(Inventory.HOTBAR_FIRST + server.inventory.heldHotbarSlot())).isPresent(),
                "the sword in hand");
        int worn = ItemClassRegistry.armourSlot(helmet()).getAsInt();
        assertEquals(helmet(), server.inventory.itemAt(worn));
        assertTrue(kitChanges > 0);
    }

    @Test
    void aChestThatNeverOpensFailsLoudlyAndIsntHammered() {
        server.silent = true;

        run(ChestWindow.ANSWER_TICKS + 2);
        assertFalse(run.active());
        run(SupplyRun.COOLDOWN_TICKS - 10);

        assertEquals(1, server.log.stream().filter(call -> call.startsWith("useBlock")).count());
    }
}
