package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Stockpile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StoresBookTest {

    @TempDir
    Path state;

    /** The stores as last seen survive a restart: the platoon leader isn't blind till a chest is opened. */
    @Test
    void theStoresLastSeenSurviveARestart() {
        Stockpile seen = new Stockpile(0, 80, 3, 5, 85, 0, 0, 0, 2, 235, 1);
        new StoresBook(new WallStore(state, "chests")).keep(seen);
        assertEquals(Optional.of(seen), new StoresBook(new WallStore(state, "chests")).kept());
        assertEquals(Optional.empty(), new StoresBook(new WallStore(state.resolve("fresh"), "chests")).kept(), "none kept: none");
    }
}
