package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every block put above sea level is counted by what put it; below it, or a tool used on a block, isn't. */
class PlacementAuditTest {

    @Test
    void aBlockPutAboveSeaLevelIsCountedByItsRoutineAndItem() {
        List<String> passed = new ArrayList<>();
        PlaySession server = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(), new Class<?>[]{PlaySession.class},
                (proxy, method, args) -> {
                    passed.add(method.getName());
                    return null;
                });
        Inventory inventory = new Inventory();
        hold(inventory, "cobblestone");
        PlacementAudit audit = new PlacementAudit(() -> 0);
        PlaySession session = audit.wrap("Ann", server, inventory);

        audit.routine("Ann", Optional.empty());
        session.useBlockFace(0, SoldierRunner.SEA_LEVEL, 0, BlockFace.UP); // the block at sea level + 1
        audit.routeStep("Ann");
        session.useBlockFace(0, SoldierRunner.SEA_LEVEL + 4, 0, BlockFace.NORTH);
        session.useBlockFace(0, SoldierRunner.SEA_LEVEL - 1, 0, BlockFace.UP); // at sea level: flat ground
        hold(inventory, "iron_hoe");
        session.useBlockFace(0, SoldierRunner.SEA_LEVEL + 4, 0, BlockFace.UP); // a tool, nothing put

        assertEquals(Map.of("none / cobblestone", 1, "route step / cobblestone", 1), audit.counts());
        assertEquals(4, passed.stream().filter("useBlockFace"::equals).count(), "every use still reaches the server");
    }

    private static void hold(Inventory inventory, String item) {
        int id = ItemClassRegistry.idOf(item).orElseThrow();
        inventory.setSlot(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot(), new int[]{id, 16});
    }
}
