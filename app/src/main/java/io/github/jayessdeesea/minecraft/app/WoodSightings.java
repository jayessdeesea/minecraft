package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;

import java.util.ArrayList;
import java.util.List;

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

    /** {@code now}'s sites, with the trees last seen added when {@code now} has none and they're not too old. */
    synchronized List<ResourceSite> seen(List<ResourceSite> now, long millis) {
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
}
