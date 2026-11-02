package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceSurveyorTest {

    private static WorldView logsAt(Set<BlockPos> logs) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return logs.contains(new BlockPos(x, y, z));
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

    private static void trunk(Set<BlockPos> logs, int x, int z) {
        for (int y = 64; y < 68; y++) {
            logs.add(new BlockPos(x, y, z));
        }
    }

    @Test
    void groupsLogsIntoStandsNearestFirstAndSkipsALoneStump() {
        Set<BlockPos> logs = new HashSet<>();
        trunk(logs, 20, 0);   // a stand of two trees, 20 east
        trunk(logs, 22, 2);
        trunk(logs, -40, 0);  // one tree, 40 west
        logs.add(new BlockPos(5, 64, 5)); // a lone stump: not worth a trip

        List<ResourceSite> stands = ResourceSurveyor.survey(logsAt(logs), new Position(0.5, 64, 0.5));

        assertEquals(List.of(new ResourceSite(Resource.WOOD, new Position(21, 66, 1), 8),
                new ResourceSite(Resource.WOOD, new Position(-40, 66, 0), 4)), stands);
    }

    @Test
    void stoneCountsUnderTheSkyButNotInACave() {
        Set<BlockPos> stone = new HashSet<>();
        for (int x = 10; x < 14; x++) {
            for (int y = 64; y < 66; y++) {
                stone.add(new BlockPos(x, y, 0));      // a wall in the open
                stone.add(new BlockPos(x, y, 30));     // a cave wall...
                stone.add(new BlockPos(x, y + 4, 31)); // ...under a roof
            }
        }
        Set<BlockPos> roof = new HashSet<>();
        for (int x = 8; x < 16; x++) {
            for (int z = 28; z < 33; z++) {
                roof.add(new BlockPos(x, 70, z));
            }
        }
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return stone.contains(new BlockPos(x, y, z)) || roof.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isStone(int x, int y, int z) {
                return stone.contains(new BlockPos(x, y, z));
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };

        List<ResourceSite> sites = ResourceSurveyor.survey(world, new Position(0.5, 64, 0.5));

        assertEquals(List.of(new ResourceSite(Resource.STONE, new Position(12, 65, 0), 8)), sites);
    }
}
