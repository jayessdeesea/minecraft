package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The garrison's scoreboard: one line a day, at dawn, kept on disk across restarts
 * (`scoreboard.jsonl` in the state directory) - how far the camp has come, measured, and what the
 * day cost. Read day over day it says whether the garrison is getting better: the earliest open
 * standard is the next thing to work on (docs/conventions.md, the tenets review). A file that won't
 * read is a fault, not a fresh start.
 */
final class Scoreboard {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FILE = "scoreboard.jsonl";

    /**
     * One day's score: when, which day of its run, the earliest phase not met (0 with every phase
     * met), standards met of all, those open with how far along, deaths, the shares of soldier-time
     * standing about (Idle, InPosition) and unable (CANTCO), and the soldiers hungry at dawn.
     */
    record Score(long at, int day, int phase, int met, int standards, List<String> open, int deaths,
                 int idlePercent, int cantcoPercent, int hungry, int soldiers) {
        public Score {
            open = List.copyOf(open);
        }

        String line() {
            return String.format(Locale.ROOT, "phase %s: %d/%d standards met, %d dead, idle %d%%, CANTCO %d%%, %d/%d hungry",
                    phase == 0 ? "all" : phase, met, standards, deaths, idlePercent, cantcoPercent, hungry, soldiers);
        }
    }

    private final Path file;

    Scoreboard(Path directory) {
        this.file = directory.resolve(FILE);
    }

    /** Every day scored so far, oldest first. */
    List<Score> days() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).map(l -> {
                try {
                    return JSON.readValue(l, Score.class);
                } catch (IOException e) {
                    throw new UncheckedIOException("a line of the scoreboard in " + file + " won't read: " + l, e);
                }
            }).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("the scoreboard in " + file + " won't read", e);
        }
    }

    /** Keeps {@code score} as the latest day, and says how it compares with the day before. */
    String add(Score score) {
        Optional<Score> before = days().stream().reduce((a, b) -> b);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, JSON.writeValueAsString(score) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("the scoreboard won't write to " + file, e);
        }
        return score.line() + before.map(b -> " - the day before: " + b.line()).orElse(" - the first day scored")
                + (score.open().isEmpty() ? "" : "; earliest open: " + score.open().getFirst());
    }
}
