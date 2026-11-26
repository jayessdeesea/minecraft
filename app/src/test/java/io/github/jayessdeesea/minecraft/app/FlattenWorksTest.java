package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Flatten;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@link FlattenWorks} answers: one entry, whatever ground is asked, and the hands that hold a soldier. */
class FlattenWorksTest {

    private static final Flatten SITE = new Flatten(0, 0, 15, 15, 62), CHUNK = new Flatten(32, 0, 47, 15, 62);

    @Test
    void theOneEntryIsReKeyedToTheGroundAskedWithItsStateKept() {
        FlattenWorks works = new FlattenWorks();
        FlattenWork site = works.of(SITE);
        site.digLeftBySquad.put("Alpha", 5);
        assertSame(site, works.of(SITE));
        assertSame(site, works.first());
        assertSame(site, works.of(CHUNK), "the configured site gives way to a mission's chunk: the same state");
        assertEquals(5, works.of(CHUNK).digLeft());
        assertEquals(List.of(site), List.copyOf(works.all()));
    }

    @Test
    void aSoldierIsHeldByTheEntryWhoseHandsHaveIt() {
        FlattenWorks works = new FlattenWorks();
        FlattenWork work = works.of(SITE);
        assertEquals(Optional.empty(), works.ofSoldier("Alice"));
        assertFalse(works.holds("Alice"));
        assertSame(work, works.forSoldier("Alice"), "not a hand: the first's");
        work.hands.add("Alice");
        assertEquals(Optional.of(work), works.ofSoldier("Alice"));
        assertTrue(works.holds("Alice"));
        assertSame(work, works.forSoldier("Alice"));
        works.release("Alice");
        assertFalse(works.holds("Alice"));
        assertTrue(work.hands.isEmpty());
    }
}
