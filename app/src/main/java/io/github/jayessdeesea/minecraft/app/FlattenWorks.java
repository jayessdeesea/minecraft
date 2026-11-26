package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Flatten;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The keep's work by the ground it is on, in the order the grounds came: {@link FlattenWork} for each. One entry
 * for now - the one ground worked, so asking for another ground's work re-keys it, its state kept as the ground
 * moves from the configured site to a mission's chunk. Read without a lock: the map is replaced whole, under the
 * lock, on a re-key.
 */
final class FlattenWorks {

    /** Keyed by ground; the one entry is keyed null until its ground is first asked for. */
    private volatile Map<Flatten, FlattenWork> works = only(null, new FlattenWork());

    private static Map<Flatten, FlattenWork> only(Flatten ground, FlattenWork work) {
        Map<Flatten, FlattenWork> one = new LinkedHashMap<>();
        one.put(ground, work);
        return Collections.unmodifiableMap(one);
    }

    /** The work of {@code ground}: the one entry, re-keyed to it if it was keyed to another. */
    FlattenWork of(Flatten ground) {
        FlattenWork work = works.get(ground);
        if (work != null) {
            return work;
        }
        synchronized (this) {
            work = works.get(ground);
            if (work == null) {
                work = first();
                works = only(ground, work);
            }
            return work;
        }
    }

    /** The first ground's work. */
    FlattenWork first() {
        return works.values().iterator().next();
    }

    /** Every ground's work, in the order the grounds came. */
    Collection<FlattenWork> all() {
        return works.values();
    }

    /** The work whose hands hold {@code soldier}, if any does. */
    Optional<FlattenWork> ofSoldier(String soldier) {
        for (FlattenWork work : works.values()) {
            if (work.hands.contains(soldier)) {
                return Optional.of(work);
            }
        }
        return Optional.empty();
    }

    /** Whether any ground's hands hold {@code soldier}. */
    boolean holds(String soldier) {
        for (FlattenWork work : works.values()) {
            if (work.hands.contains(soldier)) {
                return true;
            }
        }
        return false;
    }

    /** The work {@code soldier} is a hand of, else the first's: where a soldier who isn't a hand reads the depot. */
    FlattenWork forSoldier(String soldier) {
        for (FlattenWork work : works.values()) {
            if (work.hands.contains(soldier)) {
                return work;
            }
        }
        return first();
    }

    /** {@code soldier} is a hand of none. */
    void release(String soldier) {
        for (FlattenWork work : works.values()) {
            work.hands.remove(soldier);
        }
    }
}
