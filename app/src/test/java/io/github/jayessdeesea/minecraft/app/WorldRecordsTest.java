package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

        records.record(world, eli(new Position(0.5, 60, 0.5)), 1000);
        records.record(world, eli(new Position(9.5, 60, 0.5)), 2000); // within the half hour: not kept again

        List<Path> kept;
        try (var files = Files.list(state.resolve("worlds"))) {
            kept = files.filter(f -> !f.getFileName().toString().endsWith(".case.json")).toList();
        }
        assertEquals(1, kept.size(), "" + kept);
        SimServer.Terrain terrain = SimWorlds.replay(WorldRecords.load(kept.getFirst()));
        assertEquals(SimServer.DIRT, terrain.at(3, 60, 0));
        assertEquals(SimServer.STONE, terrain.at(-5, 59, 7));
        assertEquals(SimServer.AIR, terrain.at(0, 61, 0));
        assertEquals(SimServer.AIR, terrain.at(0, 75, 0), "unknown: air");
    }

    /** Beside the ground, a draft of the stuck soldier's case: where it stood, its kit rebuilt, the goal of its walk. */
    @Test
    void aStuckSoldiersCaseIsDraftedBesideItsGround() throws IOException {
        WorldRecords.States world = (x, y, z) -> OptionalInt.of(y < 60 ? SimServer.STONE : SimServer.AIR);
        new WorldRecords(state).record(world, eli(new Position(0.5, 60, 0.5)), 1000);

        StuckCase draft = new com.fasterxml.jackson.databind.ObjectMapper().readValue(state.resolve("worlds").resolve("Eli-1000.case.json").toFile(),
                StuckCase.class);
        assertEquals("worlds/Eli-1000.json", draft.world());
        assertEquals(List.of(12.5, 60.0, 0.5), List.of(draft.goal()[0], draft.goal()[1], draft.goal()[2]));
        assertEquals(List.of("stone_sword", "stone_pickaxe", "bread", "cobblestone", "cobblestone"),
                draft.kit().stream().map(StuckCase.Item::item).toList());
        assertEquals(true, draft.knownFailure());
    }

    private static SoldierStatus eli(Position at) {
        Kit kit = new Kit(Optional.of("stone_sword"), 0, 0, 0, 0, 100, 16, false, true, 0, false, 0, 1, 0);
        return new SoldierStatus("Eli", at, new Order.MoveTo(12.5, 60, 0.5, 0, 1, Risk.LOW),
                new Report.Cantco("no route from (0, 60, 0) to within 0 of (12, 60, 0)"), Optional.of(new Condition(20, 20, 0, Optional.of(kit))));
    }
}
