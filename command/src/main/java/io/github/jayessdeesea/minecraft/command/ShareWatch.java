package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The squad leader's watch on its producers' shares (operator, 2026-11-18: the squad leader alarms if the soldiers
 * aren't completing their task in a timely way, and the platoon leader too): each spell of {@link #DUE_MILLIS}, a
 * soldier on a duty order is held to what its share should yield in it ({@link DutyYield}) against what the errand
 * ledger counted it making - a spell ended short is its share behind, answered by its cause ({@link MissAnswer}) - and a
 * soldier of a squad on production with no share holds without one. Each said once, and again when it's back at it. The platoon leader's part is the squad's:
 * {@link #squadBehind}. The clock and counts are handed in. Thread-safe.
 */
public final class ShareWatch {

    /** A share's spell: long enough for a few passes of its work. */
    public static final long DUE_MILLIS = 180_000;

    /** What a share's sample sees: its yield so far, its soldier's CANTCO at its duty now, its hands' time by routine so far, and the routine of its duty. */
    public record Seen(int made, Optional<String> cantco, Supplier<Map<String, Long>> held, String duty) {
    }

    /**
     * A share's spell under way: its order and what it had made at its start, what it should make in it, when it began, its
     * hands' time then, the last CANTCO at its duty in it; whether it's behind, since when, and the cause last answered.
     */
    private record Spell(DutyOrder order, int madeAtStart, int expected, long since, Map<String, Long> heldAtStart, Optional<String> cantco,
                         boolean behind, long behindSince, String cause) {

        Spell cantco(Optional<String> said) {
            return said.isEmpty() ? this : new Spell(order, madeAtStart, expected, since, heldAtStart, said, behind, behindSince, cause);
        }
    }

    private final Map<String, Spell> spells = new HashMap<>();
    /** Each idle share's reason, said up the chain - a baker with no wheat stored - till a spell with work in it has run. */
    private final Map<String, String> idle = new HashMap<>();
    private final Map<String, Long> unshared = new HashMap<>();
    private final Set<String> squadsBehind = new HashSet<>();

    /**
     * {@code order}'s soldier, of {@code squad}, as {@code seen} at {@code now}; {@code expected} what its share should yield
     * in a spell from now, asked as each spell opens. A spell with nothing expected - nothing in the share to work - is no
     * spell: it opens again at the next sample, and a baker's idle for want of wheat is said up the chain, once. One ended
     * short of what it expected is answered by its cause (MissAnswer), said once while that cause holds; one that met it
     * after, back at its share.
     */
    public synchronized List<String> sample(String squad, DutyOrder order, Seen seen, IntSupplier expected, long now) {
        Spell was = spells.get(order.soldier());
        if (was != null && was.expected() > 0 && now - was.since() < DUE_MILLIS) {
            spells.put(order.soldier(), was.cantco(seen.cantco()));
            return List.of();
        }
        List<String> said = new ArrayList<>();
        int due = expected.getAsInt();
        Map<String, Long> held = due > 0 || was != null && was.expected() > 0 ? Map.copyOf(seen.held().get()) : Map.of();
        if (was == null || was.expected() == 0) {
            spells.put(order.soldier(), new Spell(order, seen.made(), due, now, held, seen.cantco(), false, now, ""));
        } else {
            int yielded = seen.made() - was.madeAtStart();
            boolean short_ = yielded < was.expected();
            String cause = "";
            if (short_) {
                Map<String, Long> spell = new HashMap<>();
                held.forEach((r, ms) -> spell.put(r, ms - was.heldAtStart().getOrDefault(r, 0L)));
                MissAnswer.Answer answer = MissAnswer.answer(new MissAnswer.Miss(squad, was.order(), order, yielded, was.expected(), now - was.since()),
                        was.cantco(), spell, seen.duty());
                cause = answer.kind();
                if (!was.behind() || !cause.equals(was.cause())) {
                    said.add(String.format(Locale.ROOT, "[Alarm] [%s] %s behind its share: %s, made %d of %d expected in %d s - %s", squad,
                            order.soldier(), was.order().line(), yielded, was.expected(), (now - was.since()) / 1000, cause));
                    said.add(answer.line());
                }
            } else if (was.behind()) {
                said.add(String.format(Locale.ROOT, "[%s] %s back at its share after %d s: made %d of %d expected", squad, order.soldier(),
                        (now - was.behindSince()) / 1000, yielded, was.expected()));
            }
            spells.put(order.soldier(), new Spell(order, seen.made(), due, now, held, seen.cantco(), short_,
                    short_ && was.behind() ? was.behindSince() : was.since(), cause));
        }
        Optional<String> reason = due > 0 ? Optional.empty() : DutyYield.idle(order);
        if (reason.isPresent() && !reason.get().equals(idle.get(order.soldier()))) {
            said.add(String.format(Locale.ROOT, "[%s -> PL] %s, %s, idle: %s", squad, order.soldier(), order.duty().name().toLowerCase(Locale.ROOT), reason.get()));
        }
        // Idle till a spell with work in it has run its course: a baker drawing a few loaves' wheat, baking them and idle again
        // is one idle, not one a draw.
        if (reason.isPresent()) {
            idle.put(order.soldier(), reason.get());
        } else if (was != null && was.expected() > 0) {
            idle.remove(order.soldier());
        }
        return said;
    }

    /** {@code soldier} of {@code squad}, on production, with no share at {@code now}: the alarm once it's held so {@link #DUE_MILLIS}. */
    public synchronized Optional<String> withoutShare(String squad, String soldier, String task, long now) {
        spells.remove(soldier);
        Long since = unshared.putIfAbsent(soldier, now);
        if (since != null && since >= 0 && now - since >= DUE_MILLIS) {
            unshared.put(soldier, -1L);
            return Optional.of(String.format(Locale.ROOT, "[Alarm] [%s] %s holds without a share of the %s for %d s", squad, soldier, task,
                    (now - since) / 1000));
        }
        return Optional.empty();
    }

    /** {@code soldier} has a share, or isn't on production: its spell without one is over. */
    public synchronized void shared(String soldier) {
        unshared.remove(soldier);
    }

    /** {@code soldier} is off production altogether: nothing watched. */
    public synchronized void off(String soldier) {
        spells.remove(soldier);
        idle.remove(soldier);
        unshared.remove(soldier);
    }

    /** How many of {@code producers} are behind their shares. */
    public synchronized long behind(Collection<String> producers) {
        return producers.stream().filter(p -> Optional.ofNullable(spells.get(p)).filter(Spell::behind).isPresent()).count();
    }

    /** Of {@code producers}, each one idle and why, as said up the chain: "Dana idle: no wheat stored". */
    public synchronized List<String> idle(Collection<String> producers) {
        return producers.stream().filter(idle::containsKey).map(p -> p + " idle: " + idle.get(p)).toList();
    }

    /**
     * The platoon leader's alarm on {@code squad}, of whose {@code producers} {@code behind} are behind: once half or
     * more are, said once a spell, and again when fewer are.
     */
    public synchronized Optional<String> squadBehind(String squad, String task, long behind, int producers) {
        boolean now = producers > 0 && behind * 2 >= producers;
        if (now == squadsBehind.contains(squad)) {
            return Optional.empty();
        }
        if (now) {
            squadsBehind.add(squad);
            return Optional.of(String.format(Locale.ROOT, "[Alarm] [PL] %s's %s is behind its order: %d of %d producers behind their shares",
                    squad, task, behind, producers));
        }
        squadsBehind.remove(squad);
        return Optional.of(String.format(Locale.ROOT, "[PL] %s's %s back to its order: %d of %d producers behind", squad, task, behind, producers));
    }
}
