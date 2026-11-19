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

    /**
     * Each chest's last contents survive a restart too, so the stores after it are every chest's, not the first one
     * opened's: live (field480-484), the seeds read 702, 64, 0, 88, 0 across five runs as each restart's first chest
     * stood for all of them.
     */
    @Test
    void eachChestSurvivesARestartAndTheStoresAreAllOfThem() {
        java.util.Map<io.github.jayessdeesea.minecraft.bot.nav.BlockPos, Stockpile> chests = java.util.Map.of(
                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(-50, 76, 47), new Stockpile(10, 0, 0, 0, 0, 0, 0, 0, 0, 600, 0),
                new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(-3, 64, 13), new Stockpile(0, 0, 0, 0, 0, 0, 0, 0, 40, 102, 0));
        Stockpile all = new StoresBook(new WallStore(state, "chests")).keep(chests);

        assertEquals(702, all.seeds());
        StoresBook after = new StoresBook(new WallStore(state, "chests"));
        assertEquals(chests, after.chests());
        assertEquals(Optional.of(all), after.kept());
    }
}
