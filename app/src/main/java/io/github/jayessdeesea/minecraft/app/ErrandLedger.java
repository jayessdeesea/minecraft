package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * What each errand yields against the walk it took (operator, 2026-11-21: this is killing productivity - alerts when a
 * worker isn't meeting expectations, and why: the table not close enough, the ground to be bettered; Ned shouldn't
 * suffer in silence). Each spell a routine holds a soldier's hands for - bake, deposit, draw, harvest, hand-off, craft -
 * is accounted: the blocks walked, the seconds, and the items it moved (the inventory's change, the more of in and out). A
 * spell that walked {@link #FAR_BLOCKS} or more for {@link #LITTLE_ITEMS} or fewer is said as a {@code [Productivity]}
 * line: who, which routine, from where, to where farthest, what it moved - the farthest point being the place it walked
 * to, so the line says what moving that place nearer would save. Every {@link #SUMMARY_MILLIS} the totals by routine.
 * Pure: positions, routines, inventories and the clock handed in, lines back.
 */
final class ErrandLedger {

    /** A spell's walk this long or longer... */
    static final double FAR_BLOCKS = 40;
    /** ...for this many items moved or fewer is a trip that didn't pay. */
    static final int LITTLE_ITEMS = 3;
    /** How often the totals by routine are said. */
    static final long SUMMARY_MILLIS = 10 * 60_000;

    /** A spell under way: its routine, where it began, the walk so far, its farthest point, and what was carried at its start. */
    private static final class Spell {
        final String routine;
        final Position from;
        final long since;
        final Map<String, Integer> carried;
        Position last;
        Position farthest;
        double walked;

        Spell(String routine, Position from, long since, Map<String, Integer> carried) {
            this.routine = routine;
            this.from = from;
            this.since = since;
            this.carried = carried;
            this.last = from;
            this.farthest = from;
        }
    }

    /** A routine's totals since the last summary. */
    private static final class Totals {
        int spells;
        int unpaid;
        double walked;
        int moved;
    }

    private final Map<String, Spell> spells = new HashMap<>();
    private final Map<String, Totals> totals = new TreeMap<>();
    /** The day's totals by routine, for dawn's review. */
    private final Map<String, Totals> day = new TreeMap<>();
    /** The day's unpaid trips by soldier and routine, and the farthest point each walked to - the place it paid for. */
    private final Map<String, Integer> dayUnpaid = new TreeMap<>();
    private final Map<String, String> dayWhere = new HashMap<>();
    private long saidAt = Long.MIN_VALUE;

    /**
     * {@code soldier} at {@code at} and {@code now}, its hands {@code routine}'s or free, carrying {@code carried} - item
     * name to count. Lines back: a spell that didn't pay, as it ends; the totals, when due.
     */
    synchronized List<String> tick(String soldier, Optional<?> routine, Position at, Map<String, Integer> carried, long now) {
        List<String> lines = new ArrayList<>();
        String now_ = routine.map(Object::toString).orElse(null);
        Spell spell = spells.get(soldier);
        if (spell != null) {
            spell.walked += Math.hypot(at.x() - spell.last.x(), at.z() - spell.last.z()) + Math.abs(at.y() - spell.last.y());
            spell.last = at;
            if (distance(at, spell.from) > distance(spell.farthest, spell.from)) {
                spell.farthest = at;
            }
        }
        if (spell != null && !spell.routine.equals(now_)) {
            spells.remove(soldier);
            int moved = moved(spell.carried, carried);
            Totals t = totals.computeIfAbsent(spell.routine, r -> new Totals());
            Totals d = day.computeIfAbsent(spell.routine, r -> new Totals());
            for (Totals x : List.of(t, d)) {
                x.spells++;
                x.walked += spell.walked;
                x.moved += moved;
            }
            if (spell.walked >= FAR_BLOCKS && moved <= LITTLE_ITEMS) {
                t.unpaid++;
                d.unpaid++;
                String who = soldier + "'s " + spell.routine;
                dayUnpaid.merge(who, 1, Integer::sum);
                dayWhere.put(who, at(spell.farthest) + ", " + Math.round(distance(spell.farthest, spell.from)) + " out");
                lines.add(String.format(Locale.ROOT, "%s's %s: %.0f blocks walked in %.0f s for %d item(s) moved%s - from %s, farthest %s, %.0f blocks out",
                        soldier, spell.routine, spell.walked, (now - spell.since) / 1000.0, moved, changes(spell.carried, carried),
                        at(spell.from), at(spell.farthest), distance(spell.farthest, spell.from)));
            }
        }
        if (now_ != null && !spells.containsKey(soldier)) {
            spells.put(soldier, new Spell(now_, at, now, Map.copyOf(carried)));
        }
        if (saidAt == Long.MIN_VALUE) {
            saidAt = now;
        } else if (now - saidAt >= SUMMARY_MILLIS && !totals.isEmpty()) {
            saidAt = now;
            StringBuilder line = new StringBuilder("errands in the last " + SUMMARY_MILLIS / 60_000 + " min, by routine:");
            totals.forEach((r, t) -> line.append(String.format(Locale.ROOT, " %s %d spells, %.0f blocks and %.1f items a spell, %d unpaid;",
                    r, t.spells, t.walked / t.spells, (double) t.moved / t.spells, t.unpaid)));
            totals.clear();
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * The day's errands for dawn's review, as lines: each routine's spells and yield a spell, and the trips that didn't pay
     * most often, by soldier, with the last place each walked to for little - what moving it nearer would save. Starts again.
     */
    synchronized List<String> review() {
        List<String> lines = new ArrayList<>();
        lines.add("errands, by routine - spells, blocks and items a spell, trips that didn't pay:");
        day.forEach((r, t) -> lines.add(String.format(Locale.ROOT, "  %s: %d, %.0f blocks, %.1f items, %d unpaid", r, t.spells,
                t.walked / t.spells, (double) t.moved / t.spells, t.unpaid)));
        dayUnpaid.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5).forEach(e -> lines.add("  unpaid: " + e.getKey() + " " + e.getValue() + " times, last out to " + dayWhere.get(e.getKey())));
        day.clear();
        dayUnpaid.clear();
        dayWhere.clear();
        return lines;
    }

    /**
     * The items moved between {@code before} and {@code after}: the more of what came in and what went out - a load put
     * away is its count, a draw its count, and a bake of a loaf from three wheat three, not four.
     */
    static int moved(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> all = new TreeMap<>(before);
        after.forEach((k, v) -> all.merge(k, 0, Integer::sum));
        int in = 0, out = 0;
        for (String k : all.keySet()) {
            int d = after.getOrDefault(k, 0) - before.getOrDefault(k, 0);
            in += Math.max(0, d);
            out += Math.max(0, -d);
        }
        return Math.max(in, out);
    }

    private static String changes(Map<String, Integer> before, Map<String, Integer> after) {
        Map<String, Integer> all = new TreeMap<>(before);
        after.forEach((k, v) -> all.merge(k, 0, Integer::sum));
        StringBuilder s = new StringBuilder();
        all.keySet().forEach(k -> {
            int d = after.getOrDefault(k, 0) - before.getOrDefault(k, 0);
            if (d != 0) {
                s.append(s.isEmpty() ? " (" : ", ").append(d > 0 ? "+" : "").append(d).append(' ').append(k);
            }
        });
        return s.isEmpty() ? "" : s.append(')').toString();
    }

    private static double distance(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private static String at(Position p) {
        return String.format(Locale.ROOT, "(%.0f, %.0f, %.0f)", p.x(), p.y(), p.z());
    }

    /** What {@code inventory} carries, by class of item, as the ledger counts it. */
    static Map<String, Integer> carried(io.github.jayessdeesea.minecraft.bot.world.Inventory inventory) {
        Map<String, Integer> carried = new TreeMap<>();
        for (io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass c : io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass.values()) {
            int n = inventory.count(c);
            if (n > 0) {
                carried.put(c.name().toLowerCase(Locale.ROOT), n);
            }
        }
        return carried;
    }
}
