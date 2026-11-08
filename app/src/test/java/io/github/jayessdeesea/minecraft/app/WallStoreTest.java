package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WallStoreTest {

    @TempDir
    Path directory;

    @Test
    void aLayoutKeptIsTheLayoutReadBackAfterARestart() {
        List<BlockPos> layout = List.of(new BlockPos(-56, 67, 36), new BlockPos(-56, 68, 36), new BlockPos(-57, 68, 36));
        new WallStore(directory).save("-48,44", layout);

        assertEquals(Optional.of(layout), new WallStore(directory).load("-48,44"));
        assertEquals(Optional.empty(), new WallStore(directory).load("0,0"), "no wall laid out there");
    }

    @Test
    void aLayoutThatWontReadIsAFault() throws Exception {
        Files.writeString(directory.resolve("wall_-48_44.json"), "not json");

        assertThrows(java.io.UncheckedIOException.class, () -> new WallStore(directory).load("-48,44"));
    }
}
