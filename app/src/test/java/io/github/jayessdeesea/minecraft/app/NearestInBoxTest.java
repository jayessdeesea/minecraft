package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Box;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NearestInBoxTest {

    /** The keep's borrow pit along the site's east side, 130 long: a gather from it is looked for beside the soldier. */
    private static final Box PIT = new Box(21, 58, -65, 26, 66, 65);

    @Test
    void aSoldierBesideThePitLooksForItsDirtBesideItself() {
        Position at = SiteGeometry.nearestIn(PIT, new Position(5.5, 63, 40.5));
        assertEquals(21.5, at.x(), "the pit's near edge");
        assertEquals(40.5, at.z(), "level with the soldier, not the pit's middle at z=0");
        assertEquals(62, at.y());
    }

    @Test
    void oneInsideItLooksWhereItStands() {
        Position at = SiteGeometry.nearestIn(PIT, new Position(23.2, 64, -10.7));
        assertEquals(23.2, at.x());
        assertEquals(-10.7, at.z());
    }
}
