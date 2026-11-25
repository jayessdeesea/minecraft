package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where a soldier stands for a chest, on hand-built ground: today's answers. */
class ChestStandTest {

    private final Set<BlockPos> blocks = new HashSet<>();
    private final Set<BlockPos> chests = new HashSet<>();

    /** Flat ground with its top at y=63, and the blocks and chests a test puts on it. */
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            BlockPos p = new BlockPos(x, y, z);
            return y <= 63 || blocks.contains(p) || chests.contains(p);
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }

        @Override
        public boolean isChest(int x, int y, int z) {
            return chests.contains(new BlockPos(x, y, z));
        }
    };

    private static Position at(double x, double y, double z) {
        return new Position(x, y, z);
    }

    private Optional<Position> stand(Position from, Position chest, List<Position> soldiers) {
        return ChestStand.at(world, from, chest, soldiers);
    }

    private void wall(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    @Test
    void openGroundBesideAChest() {
        chests.add(new BlockPos(10, 64, 10));
        assertEquals(Optional.of(at(8.5, 64, 8.5)), stand(at(5.5, 64, 5.5), at(10.5, 64, 10.5), List.of()));
    }

    @Test
    void aChestWalledOnThreeSides() {
        chests.add(new BlockPos(10, 64, 10));
        wall(9, 11, 64, 65, 9, 9);
        wall(9, 9, 64, 65, 10, 11);
        wall(11, 11, 64, 65, 10, 11);
        assertEquals(Optional.of(at(10.5, 64, 13.5)), stand(at(10.5, 64, 15.5), at(10.5, 64, 10.5), List.of()));
    }

    @Test
    void aStandAnotherSoldierHoldsIsStillChosenWhenItIsTheOnlyOne() {
        chests.add(new BlockPos(10, 64, 10));
        wall(7, 13, 64, 66, 7, 13);
        blocks.remove(new BlockPos(12, 64, 10));
        blocks.remove(new BlockPos(12, 65, 10));
        blocks.remove(new BlockPos(12, 66, 10));
        blocks.removeAll(Set.of(new BlockPos(10, 64, 10)));
        assertEquals(Optional.of(at(12.5, 64, 10.5)), stand(at(12.5, 64, 10.5), at(10.5, 64, 10.5), List.of(at(12.7, 64, 10.5))));
    }

    @Test
    void aStandTakenIsChosenLastNotRuledOut() {
        chests.add(new BlockPos(10, 64, 10));
        Optional<Position> free = stand(at(5.5, 64, 5.5), at(10.5, 64, 10.5), List.of());
        assertEquals(Optional.of(at(8.5, 64, 9.5)), stand(at(5.5, 64, 5.5), at(10.5, 64, 10.5), List.of(free.get())));
    }

    @Test
    void nothingReachableIsEmpty() {
        chests.add(new BlockPos(10, 64, 10));
        wall(4, 6, 64, 66, 4, 6);
        wall(3, 7, 64, 66, 3, 3);
        wall(3, 7, 64, 66, 7, 7);
        wall(3, 3, 64, 66, 3, 7);
        wall(7, 7, 64, 66, 3, 7);
        blocks.remove(new BlockPos(5, 64, 5));
        blocks.remove(new BlockPos(5, 65, 5));
        assertEquals(Optional.empty(), stand(at(5.5, 64, 5.5), at(10.5, 64, 10.5), List.of()));
    }

    @Test
    void aCellOnTopOfAChestIsNeverChosen() {
        chests.add(new BlockPos(10, 64, 10));
        wall(7, 13, 64, 66, 7, 13);
        blocks.remove(new BlockPos(10, 65, 10));
        blocks.remove(new BlockPos(10, 66, 10));
        blocks.remove(new BlockPos(10, 67, 10));
        assertEquals(Optional.empty(), stand(at(10.5, 65, 10.5), at(10.5, 64, 10.5), List.of()));
    }

    @Test
    void aStandTwoBelowTheChestIsNeverChosen() {
        chests.add(new BlockPos(10, 66, 10));
        assertEquals(Optional.empty(), stand(at(8.5, 64, 10.5), at(10.5, 66, 10.5), List.of()));
    }
}
