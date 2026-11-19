package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodSightingsTest {

    private static final ResourceSite TREES = new ResourceSite(Resource.WOOD, new Position(30, 64, 30), 12);
    private static final ResourceSite STONE = new ResourceSite(Resource.STONE, new Position(0, 60, 0), 40);

    /** Up from a night underground, no trees seen: the ones seen yesterday still count; a day after, not. */
    @Test
    void treesSeenLastAreKeptADay() {
        WoodSightings sightings = new WoodSightings();
        assertEquals(List.of(TREES), sightings.seen(List.of(TREES), 0));
        assertEquals(List.of(STONE, TREES), sightings.seen(List.of(STONE), 9 * 60_000), "none seen at dawn: yesterday's");
        assertEquals(List.of(STONE), sightings.seen(List.of(STONE), WoodSightings.KEPT_MILLIS + 1), "a day on: forgotten");
    }
}
