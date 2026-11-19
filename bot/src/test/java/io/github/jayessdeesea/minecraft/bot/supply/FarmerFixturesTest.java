package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chunk farm's fixtures, and what its farmer asks to have made for them. */
class FarmerFixturesTest {

    private static final int GROUND = 63;
    private final Set<BlockPos> placed = new HashSet<>();
    private final ChunkFarm chunk = new ChunkFarm(0, 0, GROUND);
    private final FarmPlan farm = new FarmPlan(chunk.holes().getFirst(), chunk.ground(), List.of(), List.of(chunk));

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= GROUND || placed.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    /** Every post, gate and chest cell still open is left to put up; one put up, or given up on, isn't. */
    @Test
    void theFixturesLeftAreTheOpenCellsNotGivenUpOn() {
        assertEquals(60, Farmer.fixtures(world, farm, ItemClass.FENCE, Set.of()).size());
        assertEquals(chunk.gates(), Farmer.fixtures(world, farm, ItemClass.FENCE_GATE, Set.of()));
        assertEquals(List.of(chunk.chest()), Farmer.fixtures(world, farm, ItemClass.CHEST, Set.of()));
        placed.add(chunk.chest());
        BlockPos skipped = chunk.fence().getFirst();
        assertEquals(List.of(), Farmer.fixtures(world, farm, ItemClass.CHEST, Set.of()), "the chest stands");
        assertTrue(!Farmer.fixtures(world, farm, ItemClass.FENCE, Set.of(skipped)).contains(skipped), "a post given up on");
    }

    /** A hoe, a bucket for the dry holes, and each fixture it carries none of. */
    @Test
    void itWantsWhatItsFarmNeedsAndItDoesntCarry() {
        Inventory inventory = new Inventory();
        assertEquals(Set.of(Recipe.WOODEN_HOE, Recipe.BUCKET, Recipe.FENCE, Recipe.FENCE_GATE, Recipe.CHEST), Farmer.wants(world, farm, inventory));
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.FENCE), 3});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item(ItemClass.BUCKET), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item(ItemClass.HOE), 1});
        assertEquals(Set.of(Recipe.FENCE_GATE, Recipe.CHEST), Farmer.wants(world, farm, inventory));
        assertEquals(Set.of(), Farmer.wants(world, new FarmPlan(chunk.holes().getFirst(), chunk.ground()), inventory), "a farm with no chunk farm");
    }
}
