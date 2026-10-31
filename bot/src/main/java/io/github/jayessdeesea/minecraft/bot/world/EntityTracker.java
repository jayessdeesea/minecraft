package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, last-known positions of every entity any soldier's connection has seen, keyed by entity
 * ID. Like {@link ChunkCache}, written from multiple connections' event loops and read from every
 * soldier's tick loop, so it's a {@link ConcurrentHashMap}; unlike {@code ChunkCache} it needs no
 * reference counting - entity IDs are server-global, and removal names exactly which IDs are gone.
 *
 * <p>Known limitation: a removal from one connection (the entity left <em>that</em> soldier's view)
 * drops it for everyone, even if another soldier can still see it. Harmless while positions are
 * spawn-only anyway; revisit alongside movement tracking.
 *
 * <p>Decoupled from {@code protocol}'s types the same way {@code ChunkCache} is - {@code app} bridges
 * {@code EntityUpdate}s into {@link #spawn}.
 */
public final class EntityTracker {

    private final ConcurrentHashMap<Integer, TrackedEntity> entities = new ConcurrentHashMap<>();

    public void spawn(int entityId, int typeId, double x, double y, double z) {
        entities.put(entityId, new TrackedEntity(entityId, typeId, new Position(x, y, z)));
    }

    public void remove(List<Integer> entityIds) {
        entityIds.forEach(entities::remove);
    }

    /** Hostile entities within {@code radius} (3D distance) of {@code center}, nearest first. */
    public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        double radiusSquared = radius * radius;
        return entities.values().stream()
                .filter(entity -> HostileMobRegistry.isHostile(entity.typeId()))
                .filter(entity -> distanceSquared(center, entity.position()) <= radiusSquared)
                .sorted(Comparator.comparingDouble(entity -> distanceSquared(center, entity.position())))
                .toList();
    }

    /** Number of tracked entities - observability only. */
    public int trackedEntityCount() {
        return entities.size();
    }

    private static double distanceSquared(Position a, Position b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
