package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared positions of every entity any soldier's connection can see, keyed by entity ID (IDs are
 * server-global). Like {@link ChunkCache}: written from multiple connections' event loops, read
 * from every soldier's tick loop, and reference-counted by owner - visibility is per-connection, so
 * an entity leaving Alice's view must not drop it while Bob still sees it.
 *
 * <p>Positions arrive already absolute ({@code protocol} resolves the server's relative movement
 * per connection), so a move from any owner can simply overwrite - every connection watching the
 * entity converges on the same server-sent position.
 *
 * <p>Decoupled from {@code protocol}'s types the same way {@code ChunkCache} is - {@code app}
 * bridges the listeners into these methods.
 */
public final class EntityTracker {

    private final ConcurrentHashMap<Integer, Entry> entities = new ConcurrentHashMap<>();

    public void spawn(int entityId, int typeId, double x, double y, double z, Object owner) {
        entities.compute(entityId, (id, existing) -> {
            Entry entry = existing != null ? existing : new Entry(typeId);
            entry.position = new Position(x, y, z);
            entry.owners.add(owner);
            return entry;
        });
    }

    /** No-op for an entity nobody is tracking - there's no type to classify it by. */
    public void move(int entityId, double x, double y, double z) {
        entities.computeIfPresent(entityId, (id, entry) -> {
            entry.position = new Position(x, y, z);
            return entry;
        });
    }

    public void remove(List<Integer> entityIds, Object owner) {
        for (int entityId : entityIds) {
            entities.computeIfPresent(entityId, (id, entry) -> {
                entry.owners.remove(owner);
                return entry.owners.isEmpty() ? null : entry;
            });
        }
    }

    /** Drops {@code owner}'s view entirely - for a connection that died without saying what it saw. */
    public void removeOwner(Object owner) {
        for (Integer entityId : entities.keySet()) {
            remove(List.of(entityId), owner);
        }
    }

    /** Hostile entities within {@code radius} (3D distance) of {@code center}, nearest first. */
    public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        double radiusSquared = radius * radius;
        return entities.entrySet().stream()
                .map(e -> new TrackedEntity(e.getKey(), e.getValue().typeId, e.getValue().position))
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

    private static final class Entry {
        final int typeId;
        // Written only inside compute()/computeIfPresent() for this key; volatile so tick-loop
        // readers outside those functions see the latest position.
        volatile Position position;
        // Only touched inside compute()/computeIfPresent() for this key - exclusive access, like
        // ChunkCache's owner sets.
        final Set<Object> owners = new HashSet<>();

        Entry(int typeId) {
            this.typeId = typeId;
        }
    }
}
