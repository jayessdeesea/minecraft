package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FootingTest {

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> null);

    @Test
    void aStepIsBuiltOfDirtBeforeCobblestone() {
        Inventory inventory = new Inventory();
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.COBBLESTONE), 64});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item(ItemClass.DIRT), 8});

        assertEquals(Optional.of(ItemClass.DIRT), new Footing(session, inventory).placeStep(new BlockPos(0, 64, 0), BlockFace.DOWN),
                "scaffolding of dirt: the cobblestone is the floor's and the wall's");
    }

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(i -> ItemClassRegistry.classOf(i).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }
}
