package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodcraftTest {

    private static final int GROUND = 64;

    /** Flat ground below y=64; the given cells are logs (and solid). */
    private static WorldView forest(Set<BlockPos> logs) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y < GROUND || logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isLog(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    @Test
    void choosesTheNearestTreeAndACellBesideItsTrunk() {
        BlockPos near = new BlockPos(5, GROUND, 0);
        BlockPos far = new BlockPos(12, GROUND, 0);
        WorldView world = forest(Set.of(near, near.offset(0, 1, 0), far));

        Optional<Woodcraft.Tree> tree = Woodcraft.findTree(world, new BlockPos(0, GROUND, 0));

        assertEquals(Optional.of(new Woodcraft.Tree(new BlockPos(4, GROUND, 0), near)), tree,
                "stand west of the near trunk, chop its lowest log first");
    }

    @Test
    void noTreeInReachIsNoTree() {
        assertEquals(Optional.empty(), Woodcraft.findTree(forest(Set.of(new BlockPos(40, GROUND, 0))), new BlockPos(0, GROUND, 0)));
    }
}
