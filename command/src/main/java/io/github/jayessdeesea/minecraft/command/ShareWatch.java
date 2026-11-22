package io.github.jayessdeesea.minecraft.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;

/**
 * The squad leader's watch on its producers' shares (operator, 2026-11-18: the squad leader alarms if the soldiers
 * aren't completing their task in a timely way, and the platoon leader too): each spell of {@link #DUE_MILLIS}, a
 * soldier on a duty order is held to what its share should yield in it ({@link DutyYield}) against what the errand
 * ledger counted it making - a spell ended short is its share behind - and a soldier of a squad on production with no
 * share holds without one. Each said once, and again when it's back at it. The platoon leader's part is the squad's:
 * {@link #squadBehind}. The clock and counts are handed in. Thread-safe.
 */
public final class ShareWatch {

    /** A share's spell: long enough for a few passes of its work. */
    public static final long DUE_MILLIS = 180_000;

    /** A share's spell under way: what it had made at its start, what it should make in it, when it began; whether it's behind, and since when. */
    private record Spell(int madeAtStart, int expected, long since, boolean behind, long behindSince) {
    }

    private final Map<String, Spell> spells = new HashMap<>();
    private final Map<String, Long> unshared = new HashMap<>();
    private final Set<String> squadsBehind = new HashSet<>();

    /**
     * {@code order}'s soldier, of {@code squad}, having made {@code made} of its duty's yield so far - the errand ledger's
     * count - at {@code now}; {@code expected} what its share should yield in a spell from now, asked as each spell opens.
     * A spell with nothing expected - nothing in the share to work, or its soldier rightly elsewhere - opens again at the
     * next sample. One ended short of what it expected is the share behind, alarmed once; one that met it, back at it.
     */
    public synchronized Optional<String> sample(String squad, DutyOrder order, int made, IntSupplier expected, long now) {
        Spell was = spells.get(order.soldier());
        if (was == null || was.expected() == 0) {
            spells.put(order.soldier(), new Spell(made, expected.getAsInt(), now, false, now));
            return Optional.empty();
        }
        if (now - was.since() < DUE_MILLIS) {
            return Optional.empty();
        }
        int yielded = made - was.madeAtStart();
        boolean short_ = yielded < was.expected();
        spells.put(order.soldier(), new Spell(made, expected.getAsInt(), now, short_, short_ && was.behind() ? was.behindSince() : was.since()));
        if (short_ && !was.behind()) {
            return Optional.of(String.format(Locale.ROOT, "[Alarm] [%s] %s behind its share: %s, made %d of %d expected in %d s", squad,
                    order.soldier(), order.line(), yielded, was.expected(), (now - was.since()) / 1000));
        }
        if (!short_ && was.behind()) {
            return Optional.of(String.format(Locale.ROOT, "[%s] %s back at its share after %d s: made %d of %d expected", squad,
                    order.soldier(), (now - was.behindSince()) / 1000, yielded, was.expected()));
        }
        return Optional.empty();
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
        unshared.remove(soldier);
    }

    /** How many of {@code producers} are behind their shares. */
    public synchronized long behind(Collection<String> producers) {
        return producers.stream().filter(p -> Optional.ofNullable(spells.get(p)).filter(Spell::behind).isPresent()).count();
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
