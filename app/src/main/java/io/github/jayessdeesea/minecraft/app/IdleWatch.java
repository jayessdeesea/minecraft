package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Soldiers standing about by day (docs/military-doctrine.md § 4): one that hasn't moved
 * {@value #MOVED_BLOCKS} blocks in {@value #IDLE_MILLIS} ms of daylight raises an alarm, once a
 * spell, and is logged again when it moves on. By day there's always work - a wall, a farm, wood,
 * stone - so a soldier stood still that long is either at a job that keeps it in place (a table, a
 * furnace) or one the chain of command hasn't given it. The alarm says which. Pure: the clock and
 * positions are handed in, and lines come back.
 */
final class IdleWatch {

    static final long IDLE_MILLIS = 120_000;
    static final double MOVED_BLOCKS = 2;

    private record Spell(Position anchor, long since, boolean alarmed) {
    }

    private final Map<String, Spell> spells = new HashMap<>();

    /**
     * {@code soldier} is at {@code position} at {@code now}; {@code day} whether it's light.
     * {@code doing} describes what it's at, for the alarm. A line when the alarm goes off, or when
     * a soldier alarmed moves on.
     */
    synchronized Optional<String> update(String soldier, Position position, long now, boolean day, java.util.function.Supplier<String> doing) {
        Spell spell = spells.get(soldier);
        if (!day) {
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
            return Optional.of(String.format(Locale.ROOT, "%s stationary %.0f s by day at (%.1f, %.1f, %.1f): %s", soldier,
                    (now - spell.since()) / 1000.0, position.x(), position.y(), position.z(), doing.get()));
        }
        return Optional.empty();
    }
}
