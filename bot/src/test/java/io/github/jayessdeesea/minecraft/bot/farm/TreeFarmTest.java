package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Room to grow, up and round (operator, 2026-11-21): leaves overhead decay; a block in the crown's way doesn't. */
class TreeFarmTest {

    private static final int GROUND = 63;
    private static final BlockPos CELL = new BlockPos(2, GROUND + 1, 2);

    private static WorldView with(Set<BlockPos> leaves, Set<BlockPos> stone) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND || leaves.contains(b) || stone.contains(b);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return y == GROUND;
            }

            @Override
            public Optional<String> leaves(int x, int y, int z) {
                return leaves.contains(new BlockPos(x, y, z)) ? Optional.of("birch") : Optional.empty();
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
    }

    @Test
    void aStumpUnderItsOwnDecayingLeavesIsPlantable() {
        assertTrue(TreeFarm.plantable(with(Set.of(CELL.offset(0, 4, 0), CELL.offset(1, 5, 0)), Set.of()), CELL),
                "the felled tree's leaves overhead: replanted at once, the leaves gone in minutes");
    }

    @Test
    void aBlockInTheTrunksWayOrTheCrownsIsNot() {
        assertFalse(TreeFarm.plantable(with(Set.of(), Set.of(CELL.offset(0, 4, 0))), CELL), "stone over it, in the trunk's way");
        assertFalse(TreeFarm.plantable(with(Set.of(), Set.of(CELL.offset(1, 5, 1))), CELL), "stone in the crown's ring");
        assertTrue(TreeFarm.plantable(with(Set.of(), Set.of(CELL.offset(1, 1, 0))), CELL), "a block beside the trunk's foot, under the crown: fine");
    }
}
