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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** One crop ripe far off waits for a batch to sweep; one near is taken (field617: Ned walked 27 blocks for one crop). */
    @Test
    void aLoneRipeCropFarOffWaitsForABatch() {
        PLOT.forEach(c -> {
            farmland.add(c);
            wheat.put(c.offset(0, 1, 0), 3);
        });
        wheat.put(PLOT.get(1).offset(0, 1, 0), Digger.RIPE);
        Set<BlockPos> claims = new HashSet<>();
        Farmer far = new Farmer("Ned", session, inventory, claims);
        far.tick(new Position(30.5, GROUND + 1, 0.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);
        assertTrue(!claims.contains(PLOT.get(1).offset(0, 1, 0)), "28 off, one ripe: left for a batch " + claims);

        Farmer near = new Farmer("Ned", session, inventory, claims);
        near.tick(new Position(0.5, GROUND + 1, 1.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);
        assertTrue(claims.contains(PLOT.get(1).offset(0, 1, 0)), "beside it: harvested " + claims);
    }

    /** One bare cell far off waits for a batch to sow; one near is sown (field631: 30 to 38 blocks for one seed). */
    @Test
    void aLoneBareCellFarOffWaitsForABatch() {
        PLOT.forEach(farmland::add);
        wheat.put(PLOT.get(0).offset(0, 1, 0), 3); // one growing, one bare
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.SEEDS), 16});
        Farmer far = new Farmer("Dana", session, inventory, new HashSet<>());
        far.tick(new Position(30.5, GROUND + 1, 0.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);
        assertTrue(far.standAt().isEmpty(), "28 off, one bare: left for a batch, not walked to: " + far.standAt());

        Farmer near = new Farmer("Dana", session, inventory, new HashSet<>());
        near.tick(new Position(0.5, GROUND + 1, 1.5), world, Optional.of(new FarmPlan(WATER, PLOT)), true);
        assertEquals(Optional.of(new Position(PLOT.get(1).x() + 0.5, GROUND + 1, PLOT.get(1).z() + 0.5)), near.standAt(), "beside it: sown");
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

    /**
     * A heap two high over a plot cell is earthwork, all of it, from the top: live (field574), the camp's farm said "13
     * blocks of the farm's earthwork, none taken: nothing in reach", its first at (11, 63, 98) - dirt with dirt over it,
     * the block over it no earthwork, so its clearer, cutting the highest first, had no cut to take.
     */
    @Test
    void aHeapOverAPlotCellIsEarthworkToItsTop() {
        BlockPos cell = new BlockPos(1, GROUND, 0);
        Set<BlockPos> heap = Set.of(cell.offset(0, 1, 0), cell.offset(0, 2, 0));
        WorldView heaped = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !b.equals(WATER) || heap.contains(b);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return isSolid(x, y, z);
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        assertTrue(Farmer.earthwork(heaped, new FarmPlan(WATER, List.of(cell))).containsAll(heap),
                "both blocks of the heap: " + Farmer.earthwork(heaped, new FarmPlan(WATER, List.of(cell))));
    }

    /**
     * A pillar two high on a plot cell, farmland all round it, is cut - stood on the farmland beside it at its level,
     * which treads no crop down. Live (field576), three farmers said "22 blocks of the farm's earthwork, none taken:
     * nothing in reach", the first a cobblestone pillar two high at (-2, 64-65, 92), every stand beside it farmland. And
     * with the field all sown, the farmer at its earthwork every tick: live (field577) the look from the farmland was
     * flipped on and off each tick of the clearer's rest, and came round off again for every look.
     */
    @Test
    void aPillarRingedByFarmlandIsCut() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = -4; x <= 10; x++) {
            for (int z = -7; z <= 7; z++) {
                plot.add(new BlockPos(x, GROUND, z));
            }
        }
        Set<BlockPos> pillar = Set.of(new BlockPos(3, GROUND + 1, 0), new BlockPos(3, GROUND + 2, 0));
        WorldView field = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !b.equals(WATER) || pillar.contains(b);
            }

            @Override
            public boolean isCobblestone(int x, int y, int z) {
                return pillar.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return plot.contains(new BlockPos(x, y, z)) && !pillar.contains(new BlockPos(x, y + 1, z));
            }

            // All sown, no crop ripe: nothing else on the farm to do, so the farmer looks at its earthwork every tick.
            @Override
            public OptionalInt wheatAge(int x, int y, int z) {
                return isFarmland(x, y - 1, z) ? OptionalInt.of(3) : OptionalInt.empty();
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.PICKAXE), 1});
        Farmer farmer = new Farmer("Fran", session, inventory, new HashSet<>());
        boolean cut = false;
        for (int tick = 0; tick < 1_000 && !cut; tick++) {
            farmer.tick(new Position(3.5, GROUND + 1, 7.5), field, Optional.of(new FarmPlan(WATER, plot)), true);
            cut = farmer.earthworkStand().filter(s -> s.y() == GROUND + 1 && Math.hypot(s.x() - 3.5, s.z() - 0.5) <= 2).isPresent();
        }

        assertTrue(cut, "the pillar cut from the farmland beside it: " + farmer.earthworkStand());
    }

    /**
     * A heap of the farm's earthwork no stand reaches holds up no other: the clear goes highest first, and live (field579)
     * the camp farm's 22 blocks were one list, a heap at (25, 63-69, 81) out of reach its top level, and a pillar 27
     * blocks off waited on it for hours. Here a column nine high at the plot's corner, its top out of reach, no
     * pickaxe to cut a stair up it, and a block of dirt on a cell across the field.
     */
    @Test
    void aHeapOfEarthworkOutOfReachHoldsUpNoOther() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = -4; x <= 10; x++) {
            for (int z = -7; z <= 7; z++) {
                plot.add(new BlockPos(x, GROUND, z));
            }
        }
        Set<BlockPos> heaped = new HashSet<>(Set.of(new BlockPos(3, GROUND + 1, 0)));
        for (int y = GROUND + 1; y <= GROUND + 9; y++) {
            heaped.add(new BlockPos(10, y, -7));
        }
        WorldView field = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !b.equals(WATER) || heaped.contains(b);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return heaped.contains(new BlockPos(x, y, z)) || y <= GROUND && !isFarmland(x, y, z) && !new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return plot.contains(new BlockPos(x, y, z)) && !heaped.contains(new BlockPos(x, y + 1, z));
            }

            // All sown, no crop ripe: nothing else on the farm to do, so the farmer looks at its earthwork every tick.
            @Override
            public OptionalInt wheatAge(int x, int y, int z) {
                return isFarmland(x, y - 1, z) ? OptionalInt.of(3) : OptionalInt.empty();
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        Farmer farmer = new Farmer("Fran", session, inventory, new HashSet<>());
        boolean cut = false;
        for (int tick = 0; tick < 2_000 && !cut; tick++) {
            // Nearest the column: looked at from off the farmland and on it, set aside, and the dirt across the field next.
            farmer.tick(new Position(9.5, GROUND + 1, -4.5), field, Optional.of(new FarmPlan(WATER, plot)), true);
            cut = farmer.earthworkStand().filter(s -> Math.hypot(s.x() - 3.5, s.z() - 0.5) <= 2).isPresent();
        }

        assertTrue(cut, "the dirt across the field cut: " + farmer.earthworkStand());
    }

    /**
     * A block of earthwork with one of the squad's steps on it comes down, the step first: the clear takes ground only
     * from an open top, and live (field580) four one-block heaps on the camp farm had a step over each - the pillar at
     * (-2, 64, 92) under the step at (-2, 65, 92) - and no cut from off the farmland or on it. A step on a plot cell
     * with no earthwork under it stays, a farmer's way out of the hollow.
     */
    @Test
    void aStepOnEarthworkComesDownWithIt() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = -4; x <= 10; x++) {
            for (int z = -7; z <= 7; z++) {
                plot.add(new BlockPos(x, GROUND, z));
            }
        }
        BlockPos earth = new BlockPos(3, GROUND + 1, 0), step = new BlockPos(3, GROUND + 2, 0), wayOut = new BlockPos(-4, GROUND + 1, 7);
        Set<BlockPos> heaped = Set.of(earth, step, wayOut);
        WorldView field = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !b.equals(WATER) || heaped.contains(b);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return heaped.contains(new BlockPos(x, y, z)) || y <= GROUND && !isFarmland(x, y, z) && !new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return plot.contains(new BlockPos(x, y, z)) && !heaped.contains(new BlockPos(x, y + 1, z));
            }

            // All sown, no crop ripe: nothing else on the farm to do, so the farmer looks at its earthwork every tick.
            @Override
            public OptionalInt wheatAge(int x, int y, int z) {
                return isFarmland(x, y - 1, z) ? OptionalInt.of(3) : OptionalInt.empty();
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        Farmer farmer = new Farmer("Fran", session, inventory, new HashSet<>(), new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS),
                Set.of(step, wayOut));
        boolean cut = false;
        for (int tick = 0; tick < 1_000 && !cut; tick++) {
            farmer.tick(new Position(3.5, GROUND + 1, 3.5), field, Optional.of(new FarmPlan(WATER, plot)), true);
            cut = farmer.earthworkStand().filter(s -> Math.hypot(s.x() - 3.5, s.z() - 0.5) <= 2).isPresent();
        }

        assertTrue(cut, "the step and the earthwork under it cut: " + farmer.earthworkStand());
        assertTrue(Farmer.hasWork(field, new FarmPlan(WATER, plot), Set.of(step, wayOut)), "the capped heap is work");
    }

    /**
     * A dry water hole one farmer is watering is claimed, so the next waters another: live (field627), Olga and Ned each
     * carried a bucket 20 blocks to the one hole at (28, 67, 60) in the same second.
     */
    @Test
    void aHoleOneFarmerIsWateringTheNextLeavesToIt() {
        io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm chunk = new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(0, 0, GROUND);
        Set<BlockPos> holes = Set.copyOf(chunk.holes()); // dug, over their floors, all dry
        Set<BlockPos> pond = Set.of(new BlockPos(30, GROUND, 30), new BlockPos(31, GROUND, 30), new BlockPos(30, GROUND, 31), new BlockPos(31, GROUND, 31));
        WorldView dry = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !holes.contains(b) && !pond.contains(b);
            }

            @Override
            public boolean isWaterSource(int x, int y, int z) {
                return pond.contains(new BlockPos(x, y, z));
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
        Set<BlockPos> claims = new HashSet<>();
        Setbacks<BlockPos> shared = new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS);
        Position by = new Position(chunk.holes().getFirst().x() + 2.5, GROUND + 1, chunk.holes().getFirst().z() + 0.5);
        for (String name : List.of("Olga", "Ned")) {
            Inventory own = new Inventory();
            own.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.BUCKET), 1});
            new Farmer(name, session, own, claims, shared, Set.of()).tick(by, dry, Optional.of(plan), true);
        }
        assertEquals(2, claims.stream().filter(holes::contains).count(), "each waters a hole of its own: " + claims);
    }

    /**
     * A water hole roofed - one of the squad's steps over it - isn't poured into, which can't work, but waits on its
     * earthwork, which takes the roof off: live (field581), Olga's pours at the chunk farm's hole (20, 67, 60), a step
     * at (20, 68, 60) over it, failed "expected water ... it didn't come" all day, the chunk farm SURVEYED.
     */
    @Test
    void aWaterHoleRoofedByAStepHasItsRoofDug() {
        io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm chunk = new io.github.jayessdeesea.minecraft.bot.farm.ChunkFarm(0, 0, GROUND);
        BlockPos hole = chunk.holes().getFirst();
        BlockPos roof = hole.offset(0, 1, 0);
        Set<BlockPos> holes = Set.copyOf(chunk.holes()); // all dug, over their floors - the one roofed
        WorldView roofed = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !holes.contains(b) || b.equals(roof);
            }

            @Override
            public boolean isWaterSource(int x, int y, int z) {
                return holes.contains(new BlockPos(x, y, z)) && !new BlockPos(x, y, z).equals(hole);
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
        assertTrue(Farmer.earthwork(roofed, plan).contains(roof), "the roof is earthwork: " + Farmer.earthwork(roofed, plan));

        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.BUCKET), 1});
        Farmer farmer = new Farmer("Olga", session, inventory, new HashSet<>(), new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS),
                Set.of(roof));
        boolean dug = false;
        for (int tick = 0; tick < 4 * Farmer.COOLDOWN_TICKS && !dug; tick++) {
            farmer.tick(new Position(hole.x() + 2.5, GROUND + 1, hole.z() + 0.5), roofed, Optional.of(plan), true);
            assertTrue(farmer.failure().isEmpty(), "failed at tick " + tick + ": " + farmer.failure());
            dug = farmer.earthworkStand().filter(st -> Math.hypot(st.x() - hole.x() - 0.5, st.z() - hole.z() - 0.5) <= 2).isPresent();
        }
        assertTrue(dug, "the roof dug from beside the hole: " + farmer.earthworkStand());
    }

    /**
     * A heap looked at from far off - nothing in reach of it from there - isn't set aside: the farmer near it again cuts
     * it. Live (field592), Olga set the chunk farm's heaps aside from 70 blocks off, at the camp, and none was cut.
     */
    @Test
    void aHeapLookedAtFromFarOffIsNotSetAside() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = -4; x <= 10; x++) {
            for (int z = -7; z <= 7; z++) {
                plot.add(new BlockPos(x, GROUND, z));
            }
        }
        BlockPos earth = new BlockPos(3, GROUND + 1, 0);
        WorldView field = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                BlockPos b = new BlockPos(x, y, z);
                return y <= GROUND && !b.equals(WATER) || b.equals(earth);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(earth) || y <= GROUND && !isFarmland(x, y, z) && !new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return plot.contains(new BlockPos(x, y, z)) && !new BlockPos(x, y + 1, z).equals(earth);
            }

            @Override
            public OptionalInt wheatAge(int x, int y, int z) {
                return isFarmland(x, y - 1, z) ? OptionalInt.of(3) : OptionalInt.empty();
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        Farmer farmer = new Farmer("Olga", session, inventory, new HashSet<>());
        // Far off - a hollow it can't climb out of, so nothing reaches from there - for four looks and more.
        for (int tick = 0; tick < 6 * Clearer.COOLDOWN_TICKS; tick++) {
            farmer.tick(new Position(3.5, GROUND - 20, 70.5), field, Optional.of(new FarmPlan(WATER, plot)), true);
        }
        boolean cut = false;
        for (int tick = 0; tick < 4 * Clearer.COOLDOWN_TICKS && !cut; tick++) {
            farmer.tick(new Position(3.5, GROUND + 1, 3.5), field, Optional.of(new FarmPlan(WATER, plot)), true);
            cut = farmer.earthworkStand().filter(s -> Math.hypot(s.x() - 3.5, s.z() - 0.5) <= 2).isPresent();
        }
        assertTrue(cut, "the heap cut once near it: " + farmer.earthworkStand());
    }

    /**
     * A farmer with bare farmland, no seeds and no grass near says it's out of seeds - for its runner to draw some - till
     * it carries some. Live (field603), Olga, the chunk farm's tender, "stops farming for a while - no seeds", 187 to sow.
     */
    @Test
    void aFarmerWithNoSeedsAndNoGrassSaysItsOutOfSeeds() {
        List<BlockPos> plot = new ArrayList<>();
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                plot.add(new BlockPos(x, GROUND, z));
            }
        }
        WorldView bare = new WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                return y <= GROUND && !new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public boolean isFarmland(int x, int y, int z) {
                return plot.contains(new BlockPos(x, y, z));
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return new BlockPos(x, y, z).equals(WATER);
            }

            @Override
            public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        Farmer farmer = new Farmer("Olga", session, inventory, new HashSet<>());
        for (int tick = 0; tick < 50 && !farmer.outOfSeeds(); tick++) {
            farmer.tick(new Position(2.5, GROUND + 1, 2.5), bare, Optional.of(new FarmPlan(WATER, plot)), true);
        }
        assertTrue(farmer.outOfSeeds(), "out of seeds: " + farmer.failure());
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.SEEDS), 64});
        assertFalse(farmer.outOfSeeds(), "seeds carried");
    }
}
