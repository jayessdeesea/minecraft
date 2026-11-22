package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Supply;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The force's burn of food and wood, kept across restarts under the state directory ({@code burn.json}): the moving
 * average is three days, and a deploy every few minutes started it again each time - live (field638), "Class I
 * (rations): 302, no burn yet at 0 a day", the supply gates' reorder and safety levels 0 and days of food unknown
 * (operator, 2026-11-21: how much food do we have - enough for a day?). Loaded once; saved after each look at the stocks.
 */
final class BurnStore {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;
    private final Supply.Burn rations;
    private final Supply.Burn logs;

    /** The burns kept in {@code dir}, an in-game day {@code dayMillis} long; none kept yet, they start fresh. */
    BurnStore(Path dir, long dayMillis) {
        this.file = dir.resolve("burn.json");
        this.rations = new Supply.Burn(dayMillis);
        this.logs = new Supply.Burn(dayMillis);
        if (Files.exists(file)) {
            try {
                Map<?, ?> kept = JSON.readValue(file.toFile(), Map.class);
                rations.restore(JSON.convertValue(kept.get("rations"), long[][].class));
                logs.restore(JSON.convertValue(kept.get("logs"), long[][].class));
            } catch (IOException e) {
                throw new UncheckedIOException("the burns kept at " + file + " unreadable", e);
            }
        }
    }

    Supply.Burn rations() {
        return rations;
    }

    Supply.Burn logs() {
        return logs;
    }

    /** Both burns written to their file. */
    void save() {
        Map<String, long[][]> kept = new LinkedHashMap<>();
        kept.put("rations", rations.snapshot());
        kept.put("logs", logs.snapshot());
        try {
            Files.createDirectories(file.getParent());
            JSON.writeValue(file.toFile(), kept);
        } catch (IOException e) {
            throw new UncheckedIOException("the burns not kept at " + file, e);
        }
    }
}
