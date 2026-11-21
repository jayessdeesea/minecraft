package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.FarmPlan;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Farmer against a server that answers as the real one does, a tick late: a hoe tills, a seed sprouts. */
class FarmerTest {

    private static final int GROUND = 63;
    private static final BlockPos WATER = new BlockPos(0, GROUND, 0);
    private static final List<BlockPos> PLOT = List.of(new BlockPos(1, GROUND, 0), new BlockPos(2, GROUND, 0));

    private final Set<BlockPos> farmland = new HashSet<>();
    private final Map<BlockPos, Integer> wheat = new HashMap<>();
    /** Short grass standing on a cell's top: the server won't till under it. */
    private final Set<BlockPos> grass = new HashSet<>();
    private final List<Runnable> answers = new ArrayList<>();
    private final Inventory inventory = new Inventory();

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y <= GROUND && !new BlockPos(x, y, z).equals(WATER);
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return new BlockPos(x, y, z).equals(WATER);
        }

        @Override
        public boolean isTillable(int x, int y, int z) {
            return y == GROUND && !farmland.contains(new BlockPos(x, y, z)) && !new BlockPos(x, y, z).equals(WATER);
        }

        @Override
        public boolean isFarmland(int x, int y, int z) {
            return farmland.contains(new BlockPos(x, y, z));
        }

        @Override
        public OptionalInt wheatAge(int x, int y, int z) {
            Integer age = wheat.get(new BlockPos(x, y, z));
            return age == null ? OptionalInt.empty() : OptionalInt.of(age);
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "useBlockFace" -> {
                        BlockPos cell = new BlockPos((int) args[0], (int) args[1], (int) args[2]);
                        ItemClass held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()))
                                .orElse(null);
                        if (held == ItemClass.HOE && world.isTillable(cell.x(), cell.y(), cell.z()) && !grass.contains(cell.offset(0, 1, 0))) {
                            answers.add(() -> farmland.add(cell));
                        } else if (held == ItemClass.SEEDS && farmland.contains(cell)) {
                            answers.add(() -> wheat.put(cell.offset(0, 1, 0), 0));
                        }
                    }
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
                        int[] staying = inventory.itemAt(hotbar) < 0 ? null : new int[]{inventory.itemAt(hotbar), inventory.countAt(hotbar)};
                        answers.add(() -> {
                            inventory.setSlot(slot, staying);
                            inventory.setSlot(hotbar, moving);
                        });
                    }
                    case "selectHotbarSlot", "startDigging" -> {
                    }
                    // Grass breaks at a touch, and the server takes it before the hoe sent after it.
                    case "finishDigging" -> grass.remove(new BlockPos((int) args[0], (int) args[1], (int) args[2]));
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    @Test
    void grassStandingOnTheGroundIsBrokenSoTheHoeTillsIt() {
        grass.add(PLOT.get(0).offset(0, 1, 0));
        tillsThePlotThenPlantsIt();
    }

    @Test
    void aCellASquadmateCouldntWorkIsPassedOverByEveryFarmer() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.HOE), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item(ItemClass.SEEDS), 5});
        Setbacks<BlockPos> shared = new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS);
        shared.failed(PLOT.get(0), 0);
        Farmer farmer = new Farmer("Bob", session, inventory, new HashSet<>(), shared);
        Position soldier = new Position(0.5, GROUND + 1, 1.5);

        for (int tick = 0; tick < 400; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            farmer.tick(soldier, world, Optional.of(new FarmPlan(WATER, PLOT)), true);
            soldier = farmer.standAt().orElse(soldier);
        }

        assertEquals(Set.of(PLOT.get(1)), farmland, "the other cell only");
    }

    /** A cell with no route to it is passed over at once, and the next taken in the same few ticks. */
    @Test
    void aCellWithNoRouteToItIsPassedOverAtOnce() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.HOE), 1});
        Farmer farmer = new Farmer("Bob", session, inventory, new HashSet<>());
        Position soldier = new Position(0.5, GROUND + 1, 1.5);
        farmer.tick(soldier, world, Optional.of(new FarmPlan(WATER, PLOT)), true);
        Position first = farmer.standAt().orElseThrow();

        farmer.unreachable("no route from here to within 0 of it");
        farmer.tick(soldier, world, Optional.of(new FarmPlan(WATER, PLOT)), true);

        assertTrue(farmer.standAt().filter(next -> !next.equals(first)).isPresent(), "another cell at once, not " + first);
    }

    @Test
    void aFarmerFarFromThePlotIsGivenTheWalkThere() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.HOE), 1});
        Farmer farmer = new Farmer("Bob", session, inventory, new HashSet<>());
        // 60 blocks off, closing at 2 blocks a second - a winding route's pace: half a minute there.
        Position soldier = new Position(60.5, GROUND + 1, 0.5);

        for (int tick = 0; tick < 1_000 && farmland.isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            farmer.tick(soldier, world, Optional.of(new FarmPlan(WATER, PLOT)), true);
            Position to = farmer.standAt().orElse(soldier);
            double dx = to.x() - soldier.x(), dz = to.z() - soldier.z(), d = Math.hypot(dx, dz);
            soldier = d <= 0.1 ? to : new Position(soldier.x() + dx / d * 0.1, soldier.y(), soldier.z() + dz / d * 0.1);
        }

        assertEquals(Set.of(PLOT.get(1)), farmland, "the nearest cell tilled at the first walk, none given up on the way");
    }

    @Test
    void aPlotCellWithABlockOnItIsNotTilled() {
        Set<BlockPos> blocked = Set.of(PLOT.get(0).offset(0, 1, 0)); // a pillar's cobblestone on the nearer cell
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return blocked.contains(new BlockPos(x, y, z)) || FarmerTest.this.world.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return FarmerTest.this.world.isHazard(x, y, z);
            }

            @Override
            public boolean isTillable(int x, int y, int z) {
                return FarmerTest.this.world.isTillable(x, y, z);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.HOE), 1});
        Farmer farmer = new Farmer("Bob", session, inventory, new HashSet<>());

        farmer.tick(new Position(0.5, GROUND + 1, 1.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);

        assertEquals(Optional.of(new Position(PLOT.get(1).x() + 0.5, GROUND + 1, PLOT.get(1).z() + 0.5)), farmer.standAt(),
                "the free cell, not one with a block where it would stand");
    }

    @Test
    void aPlotCellWithABlockOverHeadIsLeftAlone() {
        Set<BlockPos> blocked = Set.of(PLOT.get(0).offset(0, 2, 0)); // a hut's wall over the nearer cell, at head height
        WorldView world = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return blocked.contains(new BlockPos(x, y, z)) || FarmerTest.this.world.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return FarmerTest.this.world.isHazard(x, y, z);
            }

            @Override
            public boolean isTillable(int x, int y, int z) {
                return FarmerTest.this.world.isTillable(x, y, z);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.HOE), 1});
        Farmer farmer = new Farmer("Bob", session, inventory, new HashSet<>());

        farmer.tick(new Position(0.5, GROUND + 1, 1.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);

        assertEquals(Optional.of(new Position(PLOT.get(1).x() + 0.5, GROUND + 1, PLOT.get(1).z() + 0.5)), farmer.standAt(),
                "the free cell, not one with no room for its head");
    }

    @Test
    void aPlotGrowingHasNoWorkUntilItRipens() {
        FarmPlan farm = new FarmPlan(WATER, PLOT);
        assertTrue(Farmer.hasWork(world, farm, java.util.Set.of()), "to till");
        PLOT.forEach(c -> {
            farmland.add(c);
            wheat.put(c.offset(0, 1, 0), 3);
        });
        assertTrue(!Farmer.hasWork(world, farm, java.util.Set.of()), "growing");
        wheat.put(PLOT.get(1).offset(0, 1, 0), Digger.RIPE);
        assertTrue(Farmer.hasWork(world, farm, java.util.Set.of()), "ripe");
    }

    @Test
    void wheatASquadmateIsHarvestingIsLeftToIt() {
        PLOT.forEach(c -> {
            farmland.add(c);
            wheat.put(c.offset(0, 1, 0), Digger.RIPE);
        });
        Set<BlockPos> claims = new HashSet<>(Set.of(PLOT.get(0).offset(0, 1, 0))); // a squadmate's harvest of the nearer
        Farmer farmer = new Farmer("Bob", session, inventory, claims);

        farmer.tick(new Position(0.5, GROUND + 1, 1.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);

        assertTrue(claims.contains(PLOT.get(1).offset(0, 1, 0)), "the other wheat taken: " + claims);
    }

    /**
     * A harvest started again and again on the same crop, never done - interrupted each time, the
     * crop never going - is a failure, and the crop is passed over for the rest. Live, three farmers
     * started on one ripe wheat 168 times in an hour: the world model held a crop the server didn't.
     */
    @Test
    void aCropHarvestedAgainAndAgainWithoutEndIsPassedOver() {
        PLOT.forEach(c -> {
            farmland.add(c);
            wheat.put(c.offset(0, 1, 0), Digger.RIPE);
        });
        Set<BlockPos> claims = new HashSet<>();
        Farmer farmer = new Farmer("Eli", session, inventory, claims);
        Position near = new Position(0.5, GROUND + 1, 1.5);
        BlockPos nearer = PLOT.get(0).offset(0, 1, 0);

        for (int i = 0; i < Farmer.RESTARTS_BEFORE_PASSING_OVER; i++) {
            farmer.tick(near, world, Optional.of(new FarmPlan(WATER, PLOT)), true);
            assertTrue(claims.contains(nearer), "on the nearer crop, start " + (i + 1) + ": " + claims);
            farmer.tick(near, world, Optional.of(new FarmPlan(WATER, PLOT)), false); // its hands taken
        }
        farmer.tick(near, world, Optional.of(new FarmPlan(WATER, PLOT)), true);

        assertTrue(claims.contains(PLOT.get(1).offset(0, 1, 0)), "on to the other crop: " + claims);
    }

    @Test
    void tillsThePlotThenPlantsIt() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.HOE), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item(ItemClass.SEEDS), 5});
        Farmer farmer = new Farmer("Alice", session, inventory, new HashSet<>());
        Position soldier = new Position(0.5, GROUND + 1, 1.5);

        for (int tick = 0; tick < 400 && wheat.size() < PLOT.size(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            farmer.tick(soldier, world, Optional.of(new FarmPlan(WATER, PLOT)), true);
            soldier = farmer.standAt().orElse(soldier); // walks there at once
        }

        assertEquals(Set.copyOf(PLOT), farmland, "both cells tilled");
        assertEquals(Map.of(PLOT.get(0).offset(0, 1, 0), 0, PLOT.get(1).offset(0, 1, 0), 0), wheat, "and both planted");
        assertTrue(farmer.failure().isEmpty(), farmer.failure().orElse(""));
    }

    /** A farm's trench is cut out from the water: a cell beside water first, one ahead of it not yet. */
    @Test
    void aTrenchIsCutOutFromTheWater() {
        BlockPos trench = new BlockPos(0, GROUND, 1), ahead = new BlockPos(0, GROUND, 2);
        WorldView wet = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return world.isHazard(x, y, z);
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        assertEquals(List.of(trench), Farmer.trenchToCut(wet, new FarmPlan(WATER, PLOT, List.of(trench, ahead))), "not ahead of the water");
        assertTrue(Farmer.trenchToCut(new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return world.isSolid(x, y, z) && !new BlockPos(x, y, z).equals(trench);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return world.isHazard(x, y, z) || isWater(x, y, z);
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(trench) || new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        }, new FarmPlan(WATER, PLOT, List.of(trench))).isEmpty(), "dug and filled: nothing left");
    }

    /**
     * A water hole whose pond has no route to it is passed over, not watered again at once: live (field563), Olga
     * started the same hole's watering 1,321 times in a quarter of an hour, "no route" to the pond's bank each time.
     */
    @Test
    void aWaterHoleWithNoRouteToItsPondIsPassedOver() {
        io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm chunk = new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(0, 0, GROUND);
        Set<BlockPos> holes = Set.copyOf(chunk.holes());
        WorldView dry = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND && !holes.contains(new BlockPos(x, y, z)) && !isWaterSource(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return isWaterSource(x, y, z);
            }

            @Override
            public boolean isWater(int x, int y, int z) {
                return isWaterSource(x, y, z);
            }

            @Override
            public boolean isWaterSource(int x, int y, int z) {
                return y == GROUND && z == 30 && x >= 7 && x <= 9;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.BUCKET), 1});
        Farmer farmer = new Farmer("Olga", session, inventory, new HashSet<>());
        FarmPlan plan = new FarmPlan(new BlockPos(8, GROUND, 30), List.of(), List.of(), List.of(chunk));
        Position soldier = new Position(8.5, GROUND + 1, 8.5);
        farmer.tick(soldier, dry, Optional.of(plan), true);
        BlockPos first = farmer.wateringHole().orElseThrow(() -> new AssertionError("no hole watered"));

        farmer.unreachable("no route from here to within 0 of the pond's bank");
        for (int tick = 0; tick < 400; tick++) {
            farmer.tick(soldier, dry, Optional.of(plan), true);
            assertTrue(farmer.wateringHole().filter(first::equals).isEmpty(), "the same hole again at tick " + tick);
        }
    }

    /**
     * A water hole with blocks on every side of it a block up has no ground to pour from: those blocks are the farm's
     * earthwork, dug so it gets some. Live (field566), a hole at (28, 67, 52) on the chunk farm's heaped ground failed
     * "no ground beside it to pour from" 614 times.
     */
    @Test
    void theBlocksBoxingAWaterHoleInAreEarthwork() {
        io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm chunk = new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(0, 0, GROUND);
        BlockPos hole = chunk.holes().getFirst();
        Set<BlockPos> heap = new HashSet<>();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            heap.add(hole.offset(d[0], 1, d[1]));
        }
        WorldView boxed = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !b.equals(hole) || heap.contains(b);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return false;
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        FarmPlan plan = new FarmPlan(new BlockPos(8, GROUND, 30), List.of(), List.of(), List.of(chunk));

        assertTrue(Farmer.earthwork(boxed, plan).containsAll(heap), "the blocks round the hole dug: " + Farmer.earthwork(boxed, plan));

        // And the hole waits on them, not watered and failed over and over: live, its four failures in a row took
        // Olga off the farm before its earthwork was ever reached.
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.BUCKET), 1});
        Farmer farmer = new Farmer("Olga", session, inventory, new HashSet<>());
        for (int tick = 0; tick < 4 * Farmer.COOLDOWN_TICKS; tick++) {
            farmer.tick(new Position(8.5, GROUND + 1, 8.5), boxed, Optional.of(plan), true);
            assertTrue(farmer.failure().filter(f -> f.contains("no ground beside")).isEmpty(), "the boxed hole failed at tick " + tick + ": " + farmer.failure());
        }
    }
}
