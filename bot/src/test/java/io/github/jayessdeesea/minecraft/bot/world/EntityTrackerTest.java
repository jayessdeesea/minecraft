package io.github.jayessdeesea.minecraft.bot.world;

import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Type IDs are real protocol-777 IDs - see {@link HostileMobRegistryTest}. */
class EntityTrackerTest {

    private static final int ZOMBIE = 154;
    private static final int CREEPER = 32;
    private static final int COW = 30;

    private static final Object ALICE = "Alice";
    private static final Object BOB = "Bob";

    private static final Position ORIGIN = new Position(0, 64, 0);

    @Test
    void reportsHostilesWithinRadiusNearestFirst() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 5, 64, 0, ALICE);
        tracker.spawn(2, new java.util.UUID(0, 2), CREEPER, 2, 64, 0, ALICE);

        List<TrackedEntity> hostiles = tracker.nearbyHostiles(ORIGIN, 8);

        assertEquals(List.of(2, 1), hostiles.stream().map(TrackedEntity::entityId).toList());
    }

    @Test
    void ignoresNonHostileEntities() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), COW, 1, 64, 0, ALICE);

        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void ignoresHostilesOutsideRadius() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 9, 64, 0, ALICE);

        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void radiusIsThreeDimensional() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 0, 30, 0, ALICE); // directly below, deep underground

        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void removedEntitiesAreNoLongerReported() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 1, 64, 0, ALICE);
        tracker.spawn(2, new java.util.UUID(0, 2), ZOMBIE, 2, 64, 0, ALICE);

        tracker.remove(List.of(1), ALICE);

        assertEquals(List.of(2), tracker.nearbyHostiles(ORIGIN, 8).stream().map(TrackedEntity::entityId).toList());
        assertEquals(1, tracker.trackedEntityCount());
    }

    @Test
    void respawningTheSameIdReplacesItsPosition() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 100, 64, 0, ALICE);
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 1, 64, 0, ALICE);

        assertEquals(1, tracker.nearbyHostiles(ORIGIN, 8).size());
    }

    @Test
    void moveUpdatesATrackedEntitysPosition() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 100, 64, 0, ALICE);

        tracker.move(1, 3, 64, 0);

        assertEquals(new Position(3, 64, 0), tracker.nearbyHostiles(ORIGIN, 8).get(0).position());
    }

    @Test
    void moveOfAnUntrackedEntityIsIgnored() {
        EntityTracker tracker = new EntityTracker();

        tracker.move(1, 3, 64, 0);

        assertEquals(0, tracker.trackedEntityCount());
    }

    @Test
    void anEntityStaysTrackedUntilEveryOwnerHasRemovedIt() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 1, 64, 0, ALICE);
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 1, 64, 0, BOB);

        tracker.remove(List.of(1), ALICE);
        assertEquals(1, tracker.nearbyHostiles(ORIGIN, 8).size(), "Bob still sees it");

        tracker.remove(List.of(1), BOB);
        assertTrue(tracker.nearbyHostiles(ORIGIN, 8).isEmpty());
    }

    @Test
    void removeOwnerDropsOnlyThatOwnersView() {
        EntityTracker tracker = new EntityTracker();
        tracker.spawn(1, new java.util.UUID(0, 1), ZOMBIE, 1, 64, 0, ALICE);
        tracker.spawn(2, new java.util.UUID(0, 2), ZOMBIE, 2, 64, 0, ALICE);
        tracker.spawn(2, new java.util.UUID(0, 2), ZOMBIE, 2, 64, 0, BOB);

        tracker.removeOwner(ALICE);

        assertEquals(List.of(2), tracker.nearbyHostiles(ORIGIN, 8).stream().map(TrackedEntity::entityId).toList());
    }
}
