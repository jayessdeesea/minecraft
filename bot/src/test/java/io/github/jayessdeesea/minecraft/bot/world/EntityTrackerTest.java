package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Type IDs are real protocol-776 IDs - see {@link HostileMobRegistryTest}. */
class EntityTrackerTest {

    private static final int ZOMBIE = 151;
    private static final int CREEPER = 32;
    private static final int COW = 30;

    private static final Position ORIGIN = new Position(0, 64, 0);

    @Test
    void reportsHostilesWithinRadiusNearestFirst() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, ZOMBIE, 5, 64, 0);
        tracker.spawn(2, CREEPER, 2, 64, 0);

        List<TrackedEntity> hostiles = tracker.nearbyHostiles(ORIGIN, 8);

        assertEquals(List.of(2, 1), hostiles.stream().map(TrackedEntity::entityId).toList());
    }

    @Test
    void ignoresNonHostileEntities() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, COW, 1, 64, 0);

        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void ignoresHostilesOutsideRadius() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, ZOMBIE, 9, 64, 0);

        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void radiusIsThreeDimensional() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, ZOMBIE, 0, 30, 0); // directly below, deep underground

        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void removedEntitiesAreNoLongerReported() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, ZOMBIE, 1, 64, 0);
        tracker.spawn(2, ZOMBIE, 2, 64, 0);

        tracker.remove(List.of(1));

        assertEquals(List.of(2), tracker.nearbyHostiles(ORIGIN, 8).stream().map(TrackedEntity::entityId).toList());
        assertEquals(1, tracker.trackedEntityCount());
    }

    @Test
    void respawningTheSameIdReplacesItsPosition() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, ZOMBIE, 100, 64, 0);
        tracker.spawn(1, ZOMBIE, 1, 64, 0);

        assertEquals(1, tracker.nearbyHostiles(ORIGIN, 8).size());
    }
}
