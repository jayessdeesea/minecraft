package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiteTest {

    private static Site.Standard standard(Site.Level level, boolean met) {
        return new Site.Standard(level, level.name().toLowerCase() + " standard", met, met ? "met" : "not yet");
    }

    @Test
    void aSitesLevelIsTheHighestWithItAndEveryOneBelowMet() {
        Site keep = new Site(Site.Kind.KEEP, "the keep", List.of(standard(Site.Level.AIRHEAD, true), standard(Site.Level.BUILDUP, false)));

        assertEquals(Optional.of(Site.Level.AIRHEAD), keep.level(), "no shelter standard - supplied from the camp - so it's met as it stands");
        assertEquals(Optional.of(Site.Level.BUILDUP), keep.next());
        assertEquals("the keep: AIRHEAD; BUILDUP open: buildup standard - not yet", keep.line());
    }

    @Test
    void noFurtherOnThanItsMeasured() {
        Site mine = new Site(Site.Kind.MINE_BASE, "the mine base", List.of(standard(Site.Level.SHELTER, true)));

        assertEquals(Optional.of(Site.Level.SHELTER), mine.level(), "not ESTABLISHED for want of standards above");
        assertEquals(Optional.empty(), mine.next());
    }

    @Test
    void anUnmetLowerLevelHoldsItBelowWhateverIsMetAbove() {
        Site camp = new Site(Site.Kind.CAMP, "the camp", List.of(standard(Site.Level.SHELTER, false), standard(Site.Level.AIRHEAD, true)));

        assertEquals(Optional.empty(), camp.level());
        assertEquals(Optional.of(Site.Level.SHELTER), camp.next());
    }

    @Test
    void theCampIsItsFootholdsPhasesWithPhaseFourEstablished() {
        Foothold foothold = new Foothold(new Position(0, 64, 0), List.of(
                new Foothold.Standard(2, "the camp walled in", true, "met", "FORTIFY"),
                new Foothold.Standard(4, "every soldier armoured", false, "3 of 32", "MINE")));

        Site camp = Site.camp(foothold);

        assertEquals(Site.Level.ESTABLISHED, camp.standards().get(1).level());
        assertEquals(Optional.of(Site.Level.BUILDUP), camp.level(), "airhead met, buildup has none, established open");
    }
}
