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

    @Test
    void aPlotGrowingHasNoWorkUntilItRipens() {
        FarmPlan farm = new FarmPlan(WATER, PLOT);
        assertTrue(Farmer.hasWork(world, farm), "to till");
        PLOT.forEach(c -> {
            farmland.add(c);
            wheat.put(c.offset(0, 1, 0), 3);
        });
        assertTrue(!Farmer.hasWork(world, farm), "growing");
        wheat.put(PLOT.get(1).offset(0, 1, 0), Digger.RIPE);
        assertTrue(Farmer.hasWork(world, farm), "ripe");
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
}
