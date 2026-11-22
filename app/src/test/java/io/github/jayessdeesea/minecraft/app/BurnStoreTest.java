package io.github.jayessdeesea.minecraft.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A restart keeps the burn: the days before it still counted (field638: "no burn yet at 0 a day" after every deploy). */
class BurnStoreTest {

    private static final long DAY = 20 * 60_000;

    @Test
    void aRestartKeepsTheDaysBurnt() {
        Path dir = tempDir;
        BurnStore before = new BurnStore(dir, DAY);
        before.rations().stock(300, 0);
        before.rations().stock(270, DAY);
        before.save();
        double perDay = before.rations().perDay();
        assertEquals(30, perDay, 1e-9, "30 eaten over a day");

        BurnStore after = new BurnStore(dir, DAY);
        after.rations().stock(270, DAY + 60_000);
        assertEquals(30, after.rations().perDay(), 2, "after the restart, still about 30 a day - not 0");
    }

    @TempDir
    Path tempDir;
}
