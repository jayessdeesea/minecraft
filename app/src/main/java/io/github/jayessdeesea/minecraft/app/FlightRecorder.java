package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One soldier's last seconds, kept so a death can be read afterwards (docs/military-doctrine.md
 * § 6): a sample every few ticks of where it stood, what it reported, its health, which routine had
 * its hands and the nearest hostile. The server's death message comes with the death; the recorder
 * adds what led up to it - a fall's height, the routine that walked it off the edge, what was
 * closing in. Pure: samples are handed in and the casualty report comes back as text.
 */
final class FlightRecorder {

    /** Ticks between samples: four a second. */
    static final int EVERY_TICKS = 5;
    /** Samples kept: the last 15 seconds. */
    static final int KEPT = 60;
    /** Samples shown in a casualty report: the last 5 seconds, most recent last. */
    static final int SHOWN = 20;

    /**
     * What the soldier was doing at one moment.
     *
     * @param routine  which routine had the soldier's hands, empty for none - the soldier moving to its order
     * @param hostile  the nearest hostile in sight, as its kind and distance, empty for none
     */
    record Sample(long millis, Position position, String report, float health, int food, Optional<String> routine,
                  Optional<String> hostile) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long ticks;

    /** Whether this tick takes a sample. */
    synchronized boolean due() {
        return ticks++ % EVERY_TICKS == 0;
    }

    synchronized void record(Sample sample) {
        samples.addLast(sample);
        while (samples.size() > KEPT) {
            samples.removeFirst();
        }
    }

    /**
     * The casualty report for {@code name}, killed as {@code message} says (the server's death
     * message key and arguments): the cause, the fall leading up to it if there was one - the
     * highest it stood in the last 15 seconds above where it died - then its last 5 seconds. The
     * record starts again: a respawned soldier's next death is its own.
     */
    synchronized String casualty(String name, String message) {
        List<Sample> kept = List.copyOf(samples);
        samples.clear();
        if (kept.isEmpty()) {
            return name + " KIA: " + message + " - nothing recorded";
        }
        Sample last = kept.getLast();
        double highest = kept.stream().mapToDouble(s -> s.position().y()).max().orElseThrow();
        StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "%s KIA: %s at %s, %s", name, message, at(last.position()),
                last.routine().map(r -> "hands on " + r).orElse("hands free")));
        if (highest - last.position().y() >= 3) {
            Sample top = kept.stream().filter(s -> s.position().y() == highest).reduce((a, b) -> b).orElseThrow();
            report.append(String.format(Locale.ROOT, " - %.1f blocks below where it stood at %s %.1f s before, %s", highest - last.position().y(),
                    at(top.position()), (last.millis() - top.millis()) / 1000.0, top.routine().map(r -> "hands on " + r).orElse("hands free")));
        }
        for (Sample sample : kept.subList(Math.max(0, kept.size() - SHOWN), kept.size())) {
            report.append(String.format(Locale.ROOT, "%n  -%.2f s %s %s health %.1f food %d%s%s", (last.millis() - sample.millis()) / 1000.0,
                    at(sample.position()), sample.report(), sample.health(), sample.food(), sample.routine().map(r -> " " + r).orElse(""),
                    sample.hostile().map(h -> ", " + h).orElse("")));
        }
        return report.toString();
    }

    private static String at(Position position) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", position.x(), position.y(), position.z());
    }
}
