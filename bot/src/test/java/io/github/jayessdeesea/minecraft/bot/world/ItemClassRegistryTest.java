package io.github.jayessdeesea.minecraft.bot.world;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemClassRegistryTest {

    /** Cobbled deepslate counts as cobblestone: what a miner below y=0 floors, walls and makes stone kit with. */
    @Test
    void cobbledDeepslateIsCobblestone() {
        int id = IntStream.range(0, 4096).filter(i -> ItemClassRegistry.nameOf(i).equals(Optional.of("cobbled_deepslate"))).findFirst().orElseThrow();
        assertEquals(Optional.of(ItemClassRegistry.ItemClass.COBBLESTONE), ItemClassRegistry.classOf(id));
    }
}
