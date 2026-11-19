package io.github.jayessdeesea.minecraft.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The squad leader's watch on its producers' shares (operator, 2026-11-18: the squad leader alarms if the soldiers
 * aren't completing their task in a timely way, and the platoon leader too): a soldier on a duty order whose
 * product - what its duty makes or spends - hasn't changed in {@link #DUE_MILLIS} is behind its share, and a
 * soldier of a squad on production with no share holds without one. Each said once a spell, and again when it's
 * back at it. The platoon leader's part is the squad's: {@link #squadBehind}. The clock and counts are handed in.
 * Thread-safe.
 */
public final class ShareWatch {

    /** How long a share may go with no change in its product before it's behind: a few passes of a share's work. */
    public static final long DUE_MILLIS = 180_000;

    private record Seen(int product, long changedAt, boolean behind) {
    }

    private final Map<String, Seen> seen = new HashMap<>();
    private final Map<String, Long> unshared = new HashMap<>();
    private final Set<String> squadsBehind = new HashSet<>();

    /**
     * {@code order}'s soldier, of {@code squad}, holding {@code product} of what its duty makes or spends at {@code now}:
     * the alarm when it falls behind, or the line when it's back at it.
     */
    public synchronized Optional<String> sample(String squad, DutyOrder order, int product, long now) {
        Seen was = seen.get(order.soldier());
        if (was == null || was.product() != product) {
            seen.put(order.soldier(), new Seen(product, now, false));
            return was != null && was.behind() ? Optional.of(String.format(Locale.ROOT, "[%s] %s back at its share after %d s", squad,
                    order.soldier(), (now - was.changedAt()) / 1000)) : Optional.empty();
        }
        if (!was.behind() && now - was.changedAt() >= DUE_MILLIS) {
            seen.put(order.soldier(), new Seen(product, was.changedAt(), true));
            return Optional.of(String.format(Locale.ROOT, "[Alarm] [%s] %s behind its share: %s, nothing made or spent in %d s", squad,
                    order.soldier(), order.line(), (now - was.changedAt()) / 1000));
        }
        return Optional.empty();
    }

    /** {@code soldier} of {@code squad}, on production, with no share at {@code now}: the alarm once it's held so {@link #DUE_MILLIS}. */
    public synchronized Optional<String> withoutShare(String squad, String soldier, String task, long now) {
        seen.remove(soldier);
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
        seen.remove(soldier);
        unshared.remove(soldier);
    }

    /** How many of {@code producers} are behind their shares. */
    public synchronized long behind(Collection<String> producers) {
        return producers.stream().filter(p -> Optional.ofNullable(seen.get(p)).filter(Seen::behind).isPresent()).count();
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
