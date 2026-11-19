package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.WoodDuties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The trees the squads saw last, kept a while: at dawn they come up from a night underground having seen none,
 * and the force short of wood had nowhere to send anyone - live (field468), "short of WOOD with nobody on it,
 * and no squad to spare or nowhere to do it" each morning, logs at 0. Thread-safe.
 */
final class WoodSightings {

    /** How long trees seen stay known: a day and a night. */
    static final long KEPT_MILLIS = 20 * 60_000;

    private List<ResourceSite> lastWood = List.of();
    private long seenAt = Long.MIN_VALUE / 2;
    /** Woods their squad leaders found cut out, and when: no sighting within a wood's reach of one is offered. */
    private final Map<Position, Long> spent = new HashMap<>();

    /** The wood at {@code at} found cut out at {@code millis}: kept out of the sightings for {@link #KEPT_MILLIS}. */
    synchronized void spent(Position at, long millis) {
        spent.put(at, millis);
    }

    /**
     * {@code now}'s sites, with the trees last seen added when {@code now} has none and they're not too old - and none
     * within a wood's reach of one found cut out lately (backlog/renewables.md: a remembered site dropped once a
     * gather there finds nothing). Live (field477), Charlie's three strips all found cut out, and the wood still
     * offered.
     */
    synchronized List<ResourceSite> seen(List<ResourceSite> now, long millis) {
        spent.values().removeIf(at -> millis - at > KEPT_MILLIS);
        now = now.stream().filter(s -> s.resource() != Resource.WOOD || !nearSpent(s.location())).toList();
        lastWood = lastWood.stream().filter(s -> !nearSpent(s.location())).toList();
        List<ResourceSite> wood = now.stream().filter(s -> s.resource() == Resource.WOOD).toList();
        if (!wood.isEmpty()) {
            lastWood = wood;
            seenAt = millis;
            return now;
        }
        if (millis - seenAt > KEPT_MILLIS) {
            return now;
        }
        List<ResourceSite> with = new ArrayList<>(now);
        with.addAll(lastWood);
        return List.copyOf(with);
    }

    /** Whether {@code site} is within a wood's reach of one found cut out. */
    synchronized boolean nearSpent(Position site) {
        return spent.keySet().stream().anyMatch(at -> Math.hypot(at.x() - site.x(), at.z() - site.z()) <= WoodDuties.REACH);
    }
}
