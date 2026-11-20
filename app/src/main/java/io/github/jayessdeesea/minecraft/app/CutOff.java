package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soldiers cut off (AR 220-1: a unit's strength is its personnel present and fit; one cut off is reported, not counted
 * on): one that has stood within {@link #REACH} of one spot for {@link #MILLIS}, a "no route" reported there, till it
 * moves off it. The force's standards - every soldier armed - count those not cut off, and say who is: live
 * (field533-537), Eli, stuck a day in his mine with a wooden sword, held the whole force in the seizure. Thread-safe.
 */
final class CutOff {

    /** How far a soldier moves off its spot before it's no longer cut off. */
    static final double REACH = 3;
    /** How long at one spot, with no route, before it's cut off. */
    static final long MILLIS = 10 * 60_000;

    private record Spell(Position at, long since, boolean noRoute) {
    }

    private final Map<String, Spell> spells = new ConcurrentHashMap<>();

    /** {@code soldier} as reported at {@code now}. */
    void update(SoldierStatus soldier, long now) {
        boolean noRoute = soldier.report() instanceof Report.Cantco(String why) && why.startsWith("no route");
        spells.compute(soldier.name(), (name, was) -> was == null || Math.hypot(was.at().x() - soldier.position().x(),
                was.at().z() - soldier.position().z()) > REACH || Math.abs(was.at().y() - soldier.position().y()) > REACH
                ? new Spell(soldier.position(), now, noRoute) : new Spell(was.at(), was.since(), was.noRoute() || noRoute));
    }

    /** How long a soldier seen armed counts as armed: a sword put away for a moment to build isn't a sword lost. */
    static final long ARMED_MILLIS = 60_000;
    private final Map<String, Long> armedAt = new ConcurrentHashMap<>();

    /**
     * Whether {@code soldier}, {@code armedNow} or not at {@code now}, was seen armed within {@link #ARMED_MILLIS}: a
     * soldier's kit counts the weapon at hand - on its hotbar - and a builder's hotbar swaps its sword out for blocks a
     * moment at a time. Live (field538), the seizure's day of dwell broke twice on one such moment of Ned's.
     */
    boolean armed(String soldier, boolean armedNow, long now) {
        if (armedNow) {
            armedAt.put(soldier, now);
            return true;
        }
        Long at = armedAt.get(soldier);
        return at != null && now - at <= ARMED_MILLIS;
    }

    /** Whether {@code soldier} is cut off at {@code now}. */
    boolean cutOff(String soldier, long now) {
        Spell spell = spells.get(soldier);
        return spell != null && spell.noRoute() && now - spell.since() >= MILLIS;
    }
}
