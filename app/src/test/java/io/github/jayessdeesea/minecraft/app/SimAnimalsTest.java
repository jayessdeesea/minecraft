package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The simulated server's animals: led by their kind's food in a player's hand, over ground an animal walks. */
class SimAnimalsTest {

    private static final int SHEEP = EntityTracker.SHEEP_TYPE_ID, CHICKEN = EntityTracker.CHICKEN_TYPE_ID, GROUND = 63;

    /** Level grass at {@link #GROUND}. */
    private static int flat(int x, int y, int z) {
        return y <= GROUND ? SimServer.GRASS : SimServer.AIR;
    }

    private static SimAnimals.Tempter holding(ItemClass item, double x, double z) {
        return new SimAnimals.Tempter(x, GROUND + 1, z, Optional.ofNullable(item));
    }

    /** {@code ticks} of {@code tempter} standing still; how far across the ground the animal {@code id} then is from it. */
    private static double offAfter(SimAnimals animals, int id, SimAnimals.Tempter tempter, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            animals.follow(List.of(tempter), (moved, at) -> {
            });
        }
        return Math.hypot(animals.at(id)[0] - tempter.x(), animals.at(id)[2] - tempter.z());
    }

    @Test
    void aSheepComesToWheatInAHandAndStopsShortOfIt() {
        SimAnimals animals = new SimAnimals(SimAnimalsTest::flat);
        animals.spawn(1, SHEEP, 5.5, GROUND + 1, 5.5, 8);

        double off = offAfter(animals, 1, holding(ItemClass.WHEAT, 13.5, 5.5), 100);

        assertTrue(off <= SimAnimals.STOP && off > SimAnimals.STOP - SimAnimals.STEP, "a stand short of the hand, " + off + " off");
    }

    @Test
    void anAnimalIsLedOnlyByItsOwnKindsFoodAndOnlyFromNear() {
        SimAnimals animals = new SimAnimals(SimAnimalsTest::flat);
        animals.spawn(1, SHEEP, 5.5, GROUND + 1, 5.5, 8);
        animals.spawn(2, CHICKEN, 5.5, GROUND + 1, 7.5, 4);
        animals.spawn(3, SimAnimals.COW_TYPE_ID, 5.5, GROUND + 1, 9.5, 10);

        assertEquals(8.0, offAfter(animals, 1, holding(ItemClass.SEEDS, 13.5, 5.5), 100), "seeds don't move a sheep");
        assertEquals(8.0, offAfter(animals, 1, holding(null, 13.5, 5.5), 100), "nor an empty hand");
        assertEquals(11.0, offAfter(animals, 1, holding(ItemClass.WHEAT, 16.5, 5.5), 100), "from past 10 blocks it isn't seen");
        assertTrue(offAfter(animals, 2, holding(ItemClass.SEEDS, 13.5, 7.5), 100) <= SimAnimals.STOP, "seeds lead a chicken");
        assertTrue(offAfter(animals, 3, holding(ItemClass.WHEAT, 13.5, 9.5), 100) <= SimAnimals.STOP, "wheat a cow");
    }

    @Test
    void aLedSheepKeepsUpWithAWalk() {
        SimAnimals animals = new SimAnimals(SimAnimalsTest::flat);
        animals.spawn(1, SHEEP, 5.5, GROUND + 1, 5.5, 8);
        double x = 8.5;
        double farthest = 0;
        for (int tick = 0; tick < 400; tick++) {
            x += 0.2; // a soldier's walk a tick
            animals.follow(List.of(holding(ItemClass.WHEAT, x, 5.5)), (moved, at) -> {
            });
            farthest = Math.max(farthest, Math.abs(x - animals.at(1)[0]));
        }

        assertTrue(farthest <= SimAnimals.STOP + 0.5, "never more than a stand behind over 80 blocks: " + farthest);
    }

    @Test
    void aLedSheepStopsAtWaterAndAtADropAndTakesAStep() {
        // Each on its own ground, the hand 8 east: a pond across x=9..10; a pit two deep at x=9; a block's rise from x=9 on.
        SimAnimals pond = new SimAnimals((x, y, z) -> (x == 9 || x == 10) && y == GROUND ? SimServer.WATER : flat(x, y, z));
        SimAnimals pit = new SimAnimals((x, y, z) -> x == 9 && y >= GROUND - 1 ? SimServer.AIR : flat(x, y, z));
        SimAnimals rise = new SimAnimals((x, y, z) -> x >= 9 && y == GROUND + 1 ? SimServer.GRASS : flat(x, y, z));
        for (SimAnimals ground : List.of(pond, pit, rise)) {
            ground.spawn(1, SHEEP, 5.5, GROUND + 1, 5.5, 8);
        }

        offAfter(pond, 1, holding(ItemClass.WHEAT, 13.5, 5.5), 100);
        offAfter(pit, 1, holding(ItemClass.WHEAT, 13.5, 5.5), 100);
        offAfter(rise, 1, new SimAnimals.Tempter(13.5, GROUND + 2, 5.5, Optional.of(ItemClass.WHEAT)), 100);

        assertTrue(pond.at(1)[0] < 9, "on the bank, not in the pond: x=" + pond.at(1)[0]);
        assertTrue(pit.at(1)[0] < 9, "at the pit's edge: x=" + pit.at(1)[0]);
        assertEquals(GROUND + 2, rise.at(1)[1], "up the block's rise");
        assertTrue(rise.at(1)[0] > 9, "and on: x=" + rise.at(1)[0]);
    }
}
