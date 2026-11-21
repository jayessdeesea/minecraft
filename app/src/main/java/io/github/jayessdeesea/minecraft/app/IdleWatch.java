package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
    private final String word;

    /** Watching for soldiers stuck: "stationary". */
    IdleWatch() {
        this("stationary");
    }

    /** Watching for soldiers stood still, said as {@code word} in the alert. */
    IdleWatch(String word) {
        this.word = word;
    }

    /**
     * Whether a soldier {@code resting} is in fact without work: holding where it was sent, by day, while its squad has a
     * task to do - not hiding, sheltering or down the mine for the night. Live (field472), half the platoon stood in
     * position on farm and wood objectives with nothing to do, and no alert said so (operator, 2026-11-18: people
     * standing around everywhere; are you tracking soldiers not getting commands?).
     */
    static boolean withoutWork(boolean resting, Optional<io.github.jayessdeesea.minecraft.command.Objective> squadTask, boolean nightBelow) {
        return resting && !nightBelow && squadTask.map(io.github.jayessdeesea.minecraft.command.Objective::task)
                .filter(t -> !(t instanceof io.github.jayessdeesea.minecraft.command.Objective.Task.PatrolBase)
                        && !(t instanceof io.github.jayessdeesea.minecraft.command.Objective.Task.None)).isPresent();
    }

    /** The nearest of {@code hostiles} to {@code at}, said for an alert: what, where and how far. */
    static String nearestHostile(java.util.List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> hostiles, Position at) {
        return hostiles.stream().findFirst().map(h -> ", nearest hostile " + io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry.nameOf(h.typeId())
                + " at " + h.position() + String.format(Locale.ROOT, " (%.1f off)", Math.sqrt(Math.pow(h.position().x() - at.x(), 2)
                + Math.pow(h.position().y() - at.y(), 2) + Math.pow(h.position().z() - at.z(), 2)))).orElse(", no hostile within 24");
    }

    /**
     * Whether a soldier at {@code at} under {@code order} is holding where it was sent - within the
     * order's tolerances, a block's slack - so standing there is the order, not being stuck. One sent
     * somewhere it never got to isn't holding: live, a builder waist-deep in gravel 60 blocks from his
     * squad leader's point was read as waiting there, and never alerted, for a day.
     */
    static boolean holdingWhereSent(io.github.jayessdeesea.minecraft.command.Order order, Position at) {
        return order instanceof io.github.jayessdeesea.minecraft.command.Order.MoveTo m
                && Math.hypot(m.x() - at.x(), m.z() - at.z()) <= m.horizontalTolerance() + 1
                && Math.abs(m.y() - at.y()) <= m.verticalTolerance() + 1;
    }

    /** How long {@code soldier} has stood still at {@code now}: 0 while it rests, or before its first update. */
    synchronized long stillFor(String soldier, long now) {
        Spell spell = spells.get(soldier);
        return spell == null ? 0 : now - spell.since();
    }

    /** Whether {@code soldier} has stood still long enough for the alert, and hasn't moved on since. */
    synchronized boolean alarmed(String soldier) {
        Spell spell = spells.get(soldier);
        return spell != null && spell.alarmed();
    }

    /**
     * {@code soldier} is at {@code position} at {@code now}; {@code resting} whether its squad is
     * hiding or sleeping. {@code doing} describes what it's at, for the alert. A line when the alert
     * goes off, or when a soldier alerted moves on.
     */
    synchronized Optional<String> update(String soldier, Position position, long now, boolean resting, Supplier<String> doing) {
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
            return Optional.of(String.format(Locale.ROOT, "%s %s %.0f s at (%.1f, %.1f, %.1f): %s", soldier, word,
                    (now - spell.since()) / 1000.0, position.x(), position.y(), position.z(), doing.get()));
        }
        return Optional.empty();
    }
}
