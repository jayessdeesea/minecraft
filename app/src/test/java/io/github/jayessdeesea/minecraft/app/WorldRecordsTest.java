package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldRecordsTest {

    @TempDir
    Path state;

    /** A soldier's ground kept, read back, and replayed as the squad simulation's terrain, cell for cell. */
    @Test
    void aSoldiersGroundKeptReplaysInTheSimulation() throws IOException {
        WorldRecords.States world = (x, y, z) -> y < 60 ? OptionalInt.of(SimServer.STONE) : x == 3 && y == 60 ? OptionalInt.of(SimServer.DIRT)
                : y > 70 ? OptionalInt.empty() : OptionalInt.of(SimServer.AIR);
        WorldRecords records = new WorldRecords(state);

        records.record(world, "Eli", new Position(0.5, 60, 0.5), 1000);
        records.record(world, "Eli", new Position(9.5, 60, 0.5), 2000); // within the half hour: not kept again

        List<Path> kept;
        try (var files = Files.list(state.resolve("worlds"))) {
            kept = files.toList();
        }
        assertEquals(1, kept.size(), "" + kept);
        SimServer.Terrain terrain = SimWorlds.replay(WorldRecords.load(kept.getFirst()));
        assertEquals(SimServer.DIRT, terrain.at(3, 60, 0));
        assertEquals(SimServer.STONE, terrain.at(-5, 59, 7));
        assertEquals(SimServer.AIR, terrain.at(0, 61, 0));
        assertEquals(SimServer.AIR, terrain.at(0, 75, 0), "unknown: air");
    }
}
