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

class ArmourerTest {

    private static final int GROUND = 63;
    /** Iron ore in the rock, beside a one-cell cave nothing reaches; and one on the surface beside open ground. */
    private static final BlockPos BURIED = new BlockPos(8, 58, 0), CAVE = new BlockPos(8, 58, 1), SURFACE = new BlockPos(5, GROUND + 1, 0);

    private static WorldView world(Set<BlockPos> ore) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos c = new BlockPos(x, y, z);
                return ore.contains(c) || (y <= GROUND && !c.equals(CAVE) && !c.equals(CAVE.offset(0, 1, 0)));
            }

            @Override
            public boolean isIronOre(int x, int y, int z) {
                return ore.contains(new BlockPos(x, y, z));
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

    /**
     * Ore it can't walk to isn't gone for: live, builders left the keep for iron seen 40 blocks off
     * through solid rock, and each stood 20 seconds at "no route" before it gave up, all day.
     */
    @Test
    void oreBesideACaveNothingReachesIsntGoneFor() {
        BlockPos feet = new BlockPos(0, GROUND + 1, 0);

        assertEquals(Optional.empty(), Armourer.spotFor(world(Set.of(BURIED)), feet, Set.of(), Digger.Target.IRON_ORE));
        Digger.Spot spot = Armourer.spotFor(world(Set.of(BURIED, SURFACE)), feet, Set.of(), Digger.Target.IRON_ORE).orElseThrow();
        assertEquals(SURFACE, spot.block(), "the ore on the surface, the one it can walk to");
    }
}
