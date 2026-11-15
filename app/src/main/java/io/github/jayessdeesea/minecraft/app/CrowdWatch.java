package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Soldiers on top of one another - the space bubble (docs/military-doctrine.md § 3; operator,
 * 2026-11-15: everyone needs their space bubble), the idle watch's twin: one within
 * a body's width ({@value #CROWD_BLOCKS} blocks across, {@value #CROWD_HEIGHT} up or down) of
 * another for {@value #CROWD_MILLIS} ms - moving or not - other than while its squad rests, raises
 * an alert once a spell, and is logged again when it has room. Two soldiers in one cell that long
 * are working one job, or one is walking into the other: live, four squads' miners cut one mine
 * and stood on one another, and no alert said so (operator, 2026-11-15). Pure: the clock and the
 * positions are handed in, and lines come back.
 */
final class CrowdWatch {

    static final long CROWD_MILLIS = 10_000;
    static final double CROWD_BLOCKS = 0.6;
    static final double CROWD_HEIGHT = 1.5;

    private record Spell(long since, boolean alarmed) {
    }

    private final Map<String, Spell> spells = new HashMap<>();

    /**
     * {@code soldier} is at {@code position} at {@code now}, the others where {@code others} says;
     * {@code resting} whether its squad hides or sleeps. {@code doing} describes what it's at. A
     * line when the alert goes off, or when a soldier alerted has room again.
     */
    synchronized Optional<String> update(String soldier, Position position, long now, boolean resting,
                                         Map<String, Position> others, Supplier<String> doing) {
        Optional<Map.Entry<String, Position>> nearest = others.entrySet().stream()
                .filter(e -> !e.getKey().equals(soldier))
                .filter(e -> Math.hypot(e.getValue().x() - position.x(), e.getValue().z() - position.z()) < CROWD_BLOCKS
                        && Math.abs(e.getValue().y() - position.y()) < CROWD_HEIGHT)
                .min(Comparator.comparingDouble(e -> Math.hypot(e.getValue().x() - position.x(), e.getValue().z() - position.z())));
        Spell spell = spells.get(soldier);
        if (resting || nearest.isEmpty()) {
            spells.remove(soldier);
            return spell != null && spell.alarmed()
                    ? Optional.of(String.format(Locale.ROOT, "%s has room again after %.0f s", soldier, (now - spell.since()) / 1000.0))
                    : Optional.empty();
        }
        if (spell == null) {
            spells.put(soldier, new Spell(now, false));
            return Optional.empty();
        }
        if (!spell.alarmed() && now - spell.since() >= CROWD_MILLIS) {
            spells.put(soldier, new Spell(spell.since(), true));
            return Optional.of(String.format(Locale.ROOT, "%s crowded %.0f s within a body's width of %s at (%.1f, %.1f, %.1f): %s",
                    soldier, (now - spell.since()) / 1000.0, nearest.get().getKey(), position.x(), position.y(), position.z(), doing.get()));
        }
        return Optional.empty();
    }
}
