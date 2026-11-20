package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForageTest {

    /** Ground at y=63; ripe wheat over x 40..49 at z=0, young wheat at z=1, and ripe wheat 200 off - out of reach. */
    private static final WorldView FIELDS = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63;
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public OptionalInt wheatAge(int x, int y, int z) {
            if (y != 64) {
                return OptionalInt.empty();
            }
            return x >= 40 && x <= 49 && z == 0 || x == 200 && z == 0 ? OptionalInt.of(Digger.RIPE)
                    : x >= 40 && x <= 49 && z == 1 ? OptionalInt.of(3) : OptionalInt.empty();
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    @Test
    void ripeWheatInReachIsFoundAndAForagerGetsTheNearestNotASquadmates() {
        List<BlockPos> ripe = Forage.ripe(FIELDS, new BlockPos(0, 64, 0), Forage.REACH, Forage.DEPTH);

        assertEquals(10, ripe.size(), "the ripe wheat in reach, not the young nor the far: " + ripe);
        assertTrue(ripe.stream().allMatch(c -> c.y() == 63 && c.z() == 0), "the farmland under it");
        FarmPlan plot = Forage.plot(ripe, new Position(45.5, 64, 5.5), Set.of(new BlockPos(45, 63, 0))).orElseThrow();
        assertEquals(9, plot.plot().size(), "every cell but the squadmate's");
        assertTrue(plot.plot().getFirst().x() == 44 || plot.plot().getFirst().x() == 46, "the nearest first: " + plot.plot());
        assertTrue(Forage.plot(ripe, new Position(0, 64, 0), Set.copyOf(ripe)).isEmpty(), "none left that's not taken");
    }
}
