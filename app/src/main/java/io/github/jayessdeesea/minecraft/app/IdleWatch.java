package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Soldiers stuck in place (docs/military-doctrine.md § 3): one that hasn't moved
 * {@value #MOVED_BLOCKS} blocks any way in {@value #IDLE_MILLIS} ms, other than while its squad
 * rests - hiding in its patrol base or sleeping - raises an alert, once a spell, and is logged again
 * when it moves on. There's always work, by night too (the mine), so a soldier stood still that long
 * is at a job that keeps it in place (a table, a furnace), at one the chain of command hasn't given
 * it, or stuck: live, one jumped in place in a pocket for a quarter of an hour. The alert says which.
 * Pure: the clock and positions are handed in, and lines come back.
 */
final class IdleWatch {

    static final long IDLE_MILLIS = 60_000;
    static final double MOVED_BLOCKS = 2;

    private record Spell(Position anchor, long since, boolean alarmed) {
    }

    private final Map<String, Spell> spells = new HashMap<>();

    /**
     * {@code soldier} is at {@code position} at {@code now}; {@code resting} whether its squad is
     * hiding or sleeping. {@code doing} describes what it's at, for the alert. A line when the alert
     * goes off, or when a soldier alerted moves on.
     */
    synchronized Optional<String> update(String soldier, Position position, long now, boolean resting, java.util.function.Supplier<String> doing) {
        Spell spell = spells.get(soldier);
        if (resting) {
            spells.remove(soldier);
            return Optional.empty();
        }
        if (spell == null || Math.hypot(position.x() - spell.anchor().x(), position.z() - spell.anchor().z()) > MOVED_BLOCKS
                || Math.abs(position.y() - spell.anchor().y()) > MOVED_BLOCKS) {
            spells.put(soldier, new Spell(position, now, false));
            return spell != null && spell.alarmed()
                    ? Optional.of(String.format(Locale.ROOT, "%s moving again after %.0f s", soldier, (now - spell.since()) / 1000.0))
                    : Optional.empty();
        }
        if (!spell.alarmed() && now - spell.since() >= IDLE_MILLIS) {
            spells.put(soldier, new Spell(spell.anchor(), spell.since(), true));
            return Optional.of(String.format(Locale.ROOT, "%s stationary %.0f s at (%.1f, %.1f, %.1f): %s", soldier,
                    (now - spell.since()) / 1000.0, position.x(), position.y(), position.z(), doing.get()));
        }
        return Optional.empty();
    }
}
