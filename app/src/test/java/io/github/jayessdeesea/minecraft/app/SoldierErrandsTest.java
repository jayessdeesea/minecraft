package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoldierErrandsTest {

    private static final BlockPos DEPOT = new BlockPos(-50, 76, 47), FARM = new BlockPos(13, 64, 29), OTHER_FARM = new BlockPos(29, 64, 29);
    private static final Map<BlockPos, Map<ItemClass, Integer>> STOCK = Map.of(
            DEPOT, Map.of(ItemClass.SEEDS, 100, ItemClass.LOG, 30),
            FARM, Map.of(ItemClass.SEEDS, 200, ItemClass.WHEAT, 40),
            OTHER_FARM, Map.of(ItemClass.SEEDS, 50));

    /** Seed counted apart: the general stockpile's without the farms', a farm's chest its own - each to its own 256. */
    @Test
    void seedIsCountedByTheChestItsBoundFor() {
        Set<BlockPos> seen = Set.of(DEPOT, FARM, OTHER_FARM), farms = Set.of(FARM, OTHER_FARM);

        assertEquals(100, SoldierErrands.stocked(STOCK, seen, ItemClass.SEEDS, farms, Optional.empty()), "the general stockpile's");
        assertEquals(100, SoldierErrands.stocked(STOCK, seen, ItemClass.SEEDS, farms, Optional.of(DEPOT)));
        assertEquals(200, SoldierErrands.stocked(STOCK, seen, ItemClass.SEEDS, farms, Optional.of(FARM)), "that farm's chest alone");
        assertEquals(40, SoldierErrands.stocked(STOCK, seen, ItemClass.WHEAT, farms, Optional.of(DEPOT)), "any other kind, every chest's");
        assertEquals(100, SoldierErrands.stocked(STOCK, Set.of(DEPOT), ItemClass.SEEDS, farms, Optional.of(FARM)) + 100,
                "a farm's chest not seen holds none of the count");
    }
}
