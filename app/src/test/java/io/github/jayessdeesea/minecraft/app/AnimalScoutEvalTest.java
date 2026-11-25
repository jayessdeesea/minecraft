package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.supply.Herder;
import io.github.jayessdeesea.minecraft.bot.supply.Livestock;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ScoutSectors;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The eval for scouting animals (operator, 2026-11-25): look here for a kind and bring them back there - and the
 * animals follow. One soldier's {@link Herder} walked against the simulated server's animals ({@link SimAnimals}),
 * which follow their food in a hand as the pinned server's do: found, gone up to, led back, every one with it all the way.
 */
class AnimalScoutEvalTest {

    private static final int GROUND = 63;
    private static final Position CAMP = new Position(0.5, GROUND + 1, 0.5);

    /** What a walk came to. */
    record Walk(Herder herder, int ticks, double farthestLed, List<Position> landEnds, Position soldier, SimAnimals animals) {
    }

    /**
     * Walks {@code herder} from the camp over {@code ground} till it is done: each tick the soldier steps a walk's length
     * straight where it says, and each animal follows the food in its hand as the simulated server has it.
     */
    private static Walk walk(Herder herder, Livestock kind, SimServer.Terrain ground, SimAnimals animals) {
        WorldView world = world(ground, animals);
        Position soldier = CAMP;
        double farthestLed = 0;
        List<Position> landEnds = new ArrayList<>();
        int tick = 0;
        for (; tick < 20_000; tick++) {
            Herder.Step step = herder.tick(soldier, world);
            step.landEnds().ifPresent(landEnds::add);
            if (step.phase() == Herder.Phase.DONE) {
                break;
            }
            soldier = stepped(soldier, step);
            Position at = soldier;
            animals.follow(List.of(tempter(at, step, kind)), (id, a) -> {
            });
            if (step.phase() == Herder.Phase.LEAD) {
                for (TrackedEntity e : world.gameNear(at, Livestock.TEMPT_RANGE)) {
                    farthestLed = Math.max(farthestLed, Math.hypot(e.position().x() - at.x(), e.position().z() - at.z()));
                }
            }
        }
        return new Walk(herder, tick, farthestLed, landEnds, soldier, animals);
    }

    /** The soldier a walk's step on from {@code soldier}, straight where {@code step} says. */
    private static Position stepped(Position soldier, Herder.Step step) {
        if (step.walkTo().isEmpty()) {
            return soldier;
        }
        Position to = step.walkTo().get();
        double d = Math.hypot(to.x() - soldier.x(), to.z() - soldier.z());
        double pace = Math.min(0.2, d);
        return d < 1e-9 ? soldier : new Position(soldier.x() + (to.x() - soldier.x()) / d * pace, soldier.y(), soldier.z() + (to.z() - soldier.z()) / d * pace);
    }

    private static SimAnimals.Tempter tempter(Position at, Herder.Step step, Livestock kind) {
        return new SimAnimals.Tempter(at.x(), at.y(), at.z(), step.holdsFood() ? Optional.of(kind.food()) : Optional.empty());
    }

    /** {@code ground} and its {@code animals} as a soldier's connection shows them. */
    private static WorldView world(SimServer.Terrain ground, SimAnimals animals) {
        return new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return ground.at(x, y, z) != SimServer.AIR && ground.at(x, y, z) != SimServer.WATER;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return ground.at(x, y, z) == SimServer.WATER;
            }

            @Override
            public List<TrackedEntity> gameNear(Position center, double radius) {
                return animals.tracked().stream().filter(e -> e.position().distanceTo(center) <= radius).toList();
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
    }

    private static int flat(int x, int y, int z) {
        return y <= GROUND ? SimServer.GRASS : SimServer.AIR;
    }

    private static Herder herder(Livestock kind, Position lookAt) {
        return new Herder("Alice", kind, ScoutSectors.of(lookAt, 16, 1).getFirst(), lookAt, CAMP);
    }

    private static long withinOf(SimAnimals animals, Position place, double blocks) {
        return animals.tracked().stream().filter(e -> Math.hypot(e.position().x() - place.x(), e.position().z() - place.z()) <= blocks).count();
    }

    @Test
    void threeSheepAreFoundLedBackAndFollowAllTheWay() {
        SimAnimals animals = new SimAnimals(AnimalScoutEvalTest::flat);
        animals.spawn(1, Livestock.SHEEP.typeId(), 40.5, GROUND + 1, 5.5, 8);
        animals.spawn(2, Livestock.SHEEP.typeId(), 44.5, GROUND + 1, 9.5, 8);
        animals.spawn(3, Livestock.SHEEP.typeId(), 37.5, GROUND + 1, 1.5, 8);

        Walk walk = walk(herder(Livestock.SHEEP, new Position(40.5, GROUND + 1, 5.5)), Livestock.SHEEP, AnimalScoutEvalTest::flat, animals);

        assertEquals(3, walk.herder().brought(), "every one it found is with it at the camp, after " + walk.ticks() + " ticks");
        assertEquals(3, walk.herder().picked());
        assertEquals(3, withinOf(animals, CAMP, Livestock.TEMPT_RANGE), "and stands by the camp");
        assertTrue(walk.farthestLed() <= Livestock.TEMPT_RANGE, "none fell out of the range it follows from on the way: " + walk.farthestLed());
        assertEquals(List.of(), walk.landEnds());
    }

    @Test
    void chickensAreLedWithSeedsAndCowsWithWheat() {
        for (Livestock kind : List.of(Livestock.CHICKEN, Livestock.COW)) {
            SimAnimals animals = new SimAnimals(AnimalScoutEvalTest::flat);
            animals.spawn(1, kind.typeId(), 30.5, GROUND + 1, -12.5, 4);
            animals.spawn(2, kind.typeId(), 33.5, GROUND + 1, -10.5, 4);
            animals.spawn(3, Livestock.SHEEP.typeId(), 31.5, GROUND + 1, -14.5, 8); // not what was asked for

            Walk walk = walk(herder(kind, new Position(30.5, GROUND + 1, -12.5)), kind, AnimalScoutEvalTest::flat, animals);

            assertEquals(2, walk.herder().brought(), kind + " brought");
            assertEquals(kind == Livestock.COW ? 3 : 2, withinOf(animals, CAMP, Livestock.TEMPT_RANGE),
                    "a sheep comes with the cows' wheat, not with the chickens' seeds");
        }
    }

    @Test
    void anAnimalAcrossWaterIsLeftAndWhereTheLandEndsIsSaid() {
        // A river three wide across x=50..52; one sheep this side of it, one beyond.
        SimServer.Terrain river = (x, y, z) -> x >= 50 && x <= 52 && y == GROUND ? SimServer.WATER : flat(x, y, z);
        SimAnimals animals = new SimAnimals(river);
        animals.spawn(1, Livestock.SHEEP.typeId(), 40.5, GROUND + 1, 5.5, 8);
        animals.spawn(2, Livestock.SHEEP.typeId(), 54.5, GROUND + 1, 5.5, 8);

        Walk walk = walk(herder(Livestock.SHEEP, new Position(40.5, GROUND + 1, 5.5)), Livestock.SHEEP, river, animals);

        assertEquals(1, walk.herder().brought(), "the one on this side");
        assertEquals(1, walk.landEnds().size(), "said once: " + walk.landEnds());
        assertTrue(walk.landEnds().getFirst().x() < 50 && walk.landEnds().getFirst().x() >= 48, "the near bank: " + walk.landEnds());
        assertTrue(animals.at(2)[0] > 52, "and the far one still beyond the water");
    }

    @Test
    void aLedAnimalHeldBackByGroundIsWaitedForThenLeftAndCountedShort() {
        // A trench two deep across x=20 for z=0..12: the soldier's way home crosses it, an animal's doesn't.
        SimServer.Terrain trench = (x, y, z) -> x == 20 && z >= -4 && z <= 12 && y >= GROUND - 1 ? SimServer.AIR : flat(x, y, z);
        SimAnimals animals = new SimAnimals(trench);
        animals.spawn(1, Livestock.SHEEP.typeId(), 40.5, GROUND + 1, 5.5, 8);

        Walk walk = walk(herder(Livestock.SHEEP, new Position(40.5, GROUND + 1, 5.5)), Livestock.SHEEP, trench, animals);

        assertEquals(1, walk.herder().picked(), "it had one following");
        assertEquals(0, walk.herder().brought(), "and brought none: a shortfall to dig into");
        assertTrue(animals.at(1)[0] > 20, "the sheep at the trench's far side: x=" + animals.at(1)[0]);
        assertTrue(Math.hypot(walk.soldier().x() - CAMP.x(), walk.soldier().z() - CAMP.z()) <= 2, "the soldier back at the camp");
    }

    @Test
    void anAnimalAlreadyWhereTheyAreWantedIsNeitherGoneUpToNorCounted() {
        SimAnimals animals = new SimAnimals(AnimalScoutEvalTest::flat);
        animals.spawn(1, Livestock.SHEEP.typeId(), 4.5, GROUND + 1, 3.5, 8); // grazing by the camp
        animals.spawn(2, Livestock.SHEEP.typeId(), 40.5, GROUND + 1, 5.5, 8);
        Position lookAt = new Position(40.5, GROUND + 1, 5.5);

        Walk walk = walk(new Herder("Alice", Livestock.SHEEP, ScoutSectors.of(lookAt, 48, 1).getFirst(), lookAt, CAMP), Livestock.SHEEP,
                AnimalScoutEvalTest::flat, animals);

        assertEquals(1, walk.herder().picked(), "the one it went out for");
        assertEquals(1, walk.herder().brought());
    }

    @Test
    void twoSoldiersEachBringTheAnimalsOfTheirOwnSector() {
        // The place at x=40: one soldier has the ground west of it, one east, a sheep in each; they walk at once, each
        // back to a place of its own - an animal follows whichever hand is nearest, so two leads kept apart stay two.
        SimAnimals animals = new SimAnimals(AnimalScoutEvalTest::flat);
        animals.spawn(1, Livestock.SHEEP.typeId(), 28.5, GROUND + 1, -6.5, 8);
        animals.spawn(2, Livestock.SHEEP.typeId(), 52.5, GROUND + 1, 17.5, 8);
        Position lookAt = new Position(40.5, GROUND + 1, 5.5), south = new Position(0.5, GROUND + 1, 60.5);
        List<ScoutSectors.Sector> sectors = ScoutSectors.of(lookAt, 16, 2);
        List<Herder> herders = List.of(new Herder("Alice", Livestock.SHEEP, sectors.get(0), lookAt, CAMP),
                new Herder("Bob", Livestock.SHEEP, sectors.get(1), lookAt, south));
        List<Position> soldiers = new ArrayList<>(List.of(CAMP, south));
        WorldView world = world(AnimalScoutEvalTest::flat, animals);

        boolean walking = true;
        for (int tick = 0; tick < 20_000 && walking; tick++) {
            List<SimAnimals.Tempter> hands = new ArrayList<>();
            walking = false;
            for (int i = 0; i < herders.size(); i++) {
                Herder.Step step = herders.get(i).tick(soldiers.get(i), world);
                walking |= step.phase() != Herder.Phase.DONE;
                soldiers.set(i, stepped(soldiers.get(i), step));
                hands.add(tempter(soldiers.get(i), step, Livestock.SHEEP));
            }
            animals.follow(hands, (id, a) -> {
            });
        }

        assertEquals(List.of(1, 1), herders.stream().map(Herder::brought).toList(), "each its own, and one each");
        assertTrue(Math.hypot(animals.at(1)[0] - CAMP.x(), animals.at(1)[2] - CAMP.z()) <= Livestock.TEMPT_RANGE, "the west one at the first's place");
        assertTrue(Math.hypot(animals.at(2)[0] - south.x(), animals.at(2)[2] - south.z()) <= Livestock.TEMPT_RANGE, "the east one at the second's");
    }

    @Test
    void aGroundWithNoneOfTheKindIsWalkedAndSaidSo() {
        SimAnimals animals = new SimAnimals(AnimalScoutEvalTest::flat);

        Walk walk = walk(herder(Livestock.SHEEP, new Position(40.5, GROUND + 1, 5.5)), Livestock.SHEEP, AnimalScoutEvalTest::flat, animals);

        assertEquals(0, walk.herder().brought());
        assertTrue(walk.ticks() < 20_000, "done, not walking for good");
    }
}
