package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm;
import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The camp's farm takes in the next chunk farm once every one it has is past SURVEYED (operator, 2026-11-20: the next
 * chunk farm once one is tended, as soon as can be - farms are what there is to forage; and grow more farms).
 */
class CampFarmTest {

    private static final int GRASS = 9, STONE = 1, WATER = 89, AIR = 0;
    private static final BlockPos CAMP = new BlockPos(8, 64, 8);

    @TempDir
    Path dir;

    /** Flat grass at y=63 for chunks round the camp, a pond by the camp, and {@code open} cells dug out over a floor. */
    private static WorldView ground(Set<BlockPos> open) {
        return RecordedGroundEfficiencyTest.world(-48, -48, 112, 112, (x, z) -> 0, (x, yz) -> {
            int y = yz >> 16, z = (short) (yz & 0xFFFF);
            if (open.contains(new BlockPos(x, y, z))) {
                return AIR;
            }
            boolean pond = Math.abs(x - 8) <= 1 && Math.abs(z - 12) <= 1;
            return y < 63 ? STONE : y == 63 ? (pond ? WATER : GRASS) : AIR;
        });
    }

    @Test
    void theNextChunkFarmIsSitedOnceTheFirstsGroundIsDone() {
        CampFarm farms = new CampFarm(new WallStore(dir, "farm"), 20_000);
        FarmPlan first = farms.kept(ground(Set.of()), CAMP, "8,8", 4, true);
        assertNotNull(first, "a farm by the pond");
        assertEquals(1, first.chunks().size(), "its first chunk farm: " + first.chunks());

        // Its holes still to dig - SURVEYED: no second yet.
        assertEquals(1, farms.kept(ground(Set.of()), CAMP, "8,8", 4, true).chunks().size(), "the first still SURVEYED");

        // Its holes dug over their floors - GROUND: the next is taken in.
        Set<BlockPos> holes = Set.copyOf(first.chunks().getFirst().holes());
        FarmPlan next = farms.kept(ground(holes), CAMP, "8,8", 4, true);
        assertEquals(2, next.chunks().size(), "the next chunk farm: " + next.chunks());
        assertEquals(2, next.chunks().stream().map(c -> ChunkFarm.key(c.chunkX(), c.chunkZ())).collect(Collectors.toSet()).size(), "two chunks");
    }
}
