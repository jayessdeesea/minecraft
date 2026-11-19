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

    /** A wood found cut out isn't offered again for a day, from the squads' sightings or the remembered ones. */
    @Test
    void aWoodFoundCutOutIsNotOfferedForADay() {
        WoodSightings sightings = new WoodSightings();
        ResourceSite near = new ResourceSite(
                Resource.WOOD, new Position(-49, 64, 47), 12);
        ResourceSite far = new ResourceSite(
                Resource.WOOD, new Position(60, 64, 47), 30);
        sightings.seen(List.of(near, far), 0);
        sightings.spent(new Position(-49, 64, 47), 0);

        assertEquals(List.of(far), sightings.seen(List.of(near, far), 1));
        assertEquals(List.of(far), sightings.seen(List.of(), 2), "the remembered ones too");
        assertEquals(List.of(near, far), sightings.seen(List.of(near, far), WoodSightings.KEPT_MILLIS + 1), "a day on, it's grown");
    }
}
