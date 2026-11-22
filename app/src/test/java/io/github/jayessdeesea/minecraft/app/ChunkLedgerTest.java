package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.farm.TreeChunk;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The chunk register kept as the world shows it. */
class ChunkLedgerTest {

    @TempDir
    Path state;

    /** Grass at sea level everywhere: a tree chunk on it is at its first stage. */
    private final WorldView grass = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= 63;
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return y == 63;
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

    /** The camp's tree chunk kept with its stage, kept once while it stands still, and found again after a restart. */
    @Test
    void theTreeChunkIsKeptWithItsStage() {
        ChunkLedger ledger = new ChunkLedger(state.resolve("registers"));
        ledger.measure(grass, null, Optional.of(new TreeChunk(5, 5, 63)), 1_000);
        ledger.measure(grass, null, Optional.of(new TreeChunk(5, 5, 63)), 2_000);

        assertEquals(Optional.of(new ChunkRecord(5, 5, ChunkRecord.Kind.TREES, 63, Optional.of("PLANTED"), 1_000)), ledger.registers().chunk(5, 5),
                "kept at its first measure, its trunks' ground ready to plant; the second, unchanged, not kept again");
    }

    /** Pointed elsewhere once it's open: fails loud, not two registers. */
    @Test
    void pointedElsewhereOnceOpenFailsLoud() {
        ChunkLedger ledger = new ChunkLedger(state.resolve("registers"));
        ledger.registers();
        assertThrows(IllegalStateException.class, () -> ledger.keptIn(state.resolve("elsewhere")));
    }
}
