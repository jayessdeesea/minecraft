package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A tree farm laid out on open grass, and a soldier planting it against a server that answers a tick late. */
class ForesterTest {

    private static final int GROUND = 64;
    private static final BlockPos CENTRE = new BlockPos(0, GROUND, 0);

    private final Map<BlockPos, String> saplings = new HashMap<>();
    /** The trees planted, in the order they went in. */
    private final List<String> plantedInTurn = new java.util.ArrayList<>();
    private final List<Runnable> answers = new ArrayList<>();
    private final Inventory inventory = new Inventory();

    private final WorldView grass = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y < GROUND;
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSoil(int x, int y, int z) {
            return y == GROUND - 1;
        }

        @Override
        public Optional<String> sapling(int x, int y, int z) {
            return Optional.ofNullable(saplings.get(new BlockPos(x, y, z)));
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
                        BlockPos cell = new BlockPos((int) args[0], (int) args[1] + 1, (int) args[2]);
                        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
                        String tree = ItemClassRegistry.nameOf(inventory.itemAt(held)).orElseThrow().replace("_sapling", "");
                        answers.add(() -> {
                            saplings.put(cell, tree);
                            plantedInTurn.add(tree);
                            int left = inventory.countAt(held) - 1;
                            inventory.setSlot(held, left == 0 ? null : new int[]{inventory.itemAt(held), left});
                        });
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
                    case "selectHotbarSlot" -> {
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private static int item(String name) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of(name))).findFirst().orElseThrow();
    }

    @Test
    void laysTheFarmOutOnAGridWithRoomToGrow() {
        TreeFarm farm = TreeFarm.site(grass, CENTRE, 8).orElseThrow();

        assertEquals(16, farm.plot().size(), "x and z each 2 past a multiple of 4 in a 17x17 square: -6, -2, 2, 6 each way");
        assertEquals(4, Math.abs(farm.plot().getFirst().x()) + Math.abs(farm.plot().getFirst().z()), "nearest the centre first");
        assertTrue(farm.plot().stream().allMatch(c -> Math.floorMod(c.x() + 2, TreeFarm.SPACING) == 0 && Math.floorMod(c.z() + 2, TreeFarm.SPACING) == 0),
                "on the world's grid, not the centre's");
    }

    @Test
    void plantsBirchFirstThenSpruceThenWhateverElseItCarries() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("oak_sapling"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("spruce_sapling"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 2, new int[]{item("birch_sapling"), 1});
        TreeFarm farm = new TreeFarm(CENTRE, List.of(CENTRE, CENTRE.offset(3, 0, 0), CENTRE.offset(-3, 0, 0)));
        Forester forester = new Forester("Alice", session, inventory, new HashSet<>());
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 600 && saplings.size() < 3; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            forester.tick(soldier, grass, Optional.of(farm), true);
            soldier = forester.standAt().orElse(soldier); // walks there at once
        }

        assertEquals(List.of("birch", "spruce", "oak"), plantedInTurn, "birch, then spruce, then the oak: " + saplings);
        assertTrue(forester.failure().isEmpty(), forester.failure().orElse(""));
    }

    /**
     * A cell one forester couldn't stand in is passed over by the next: live (field618), (38, 66, 82) was walked at 22
     * times by Fran, Bob and the rest in turn.
     */
    @Test
    void aCellOneForesterCouldntReachTheNextPassesOver() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("birch_sapling"), 4});
        BlockPos far = CENTRE.offset(3, 0, 0);
        TreeFarm farm = new TreeFarm(CENTRE, List.of(CENTRE, far));
        Setbacks<BlockPos> shared = new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS);
        Forester fran = new Forester("Fran", session, inventory, new HashSet<>(), shared);
        Position stuck = new Position(-5.5, GROUND, 0.5); // never gets to the cell it's sent to
        for (int tick = 0; tick < Forester.WALK_TICKS + 50; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            fran.tick(stuck, grass, Optional.of(farm), true);
        }
        assertTrue(fran.failure().isPresent(), "Fran couldn't get to " + CENTRE);

        Forester bob = new Forester("Bob", session, inventory, new HashSet<>(), shared);
        Position soldier = new Position(-5.5, GROUND, 0.5);
        for (int tick = 0; tick < 200 && saplings.isEmpty(); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            bob.tick(soldier, grass, Optional.of(farm), true);
            soldier = bob.standAt().orElse(soldier);
        }
        assertEquals(java.util.Set.of(far), saplings.keySet(), "Bob plants the cell Fran could get to, not hers again");
    }

    /** A cell far off is given the walk it takes, not a fixed 20 s (field627: 50 blocks off, timed out 17 times). */
    @Test
    void aCellFarOffIsGivenTheWalkItTakes() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("birch_sapling"), 4});
        BlockPos far = CENTRE.offset(60, 0, 0);
        TreeFarm farm = new TreeFarm(CENTRE, List.of(far));
        Forester ivy = new Forester("Ivy", session, inventory, new HashSet<>());
        Position walking = new Position(0.5, GROUND, 0.5);
        for (int tick = 0; tick < Forester.WALK_TICKS + 100; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            ivy.tick(walking, grass, Optional.of(farm), true);
            walking = new Position(Math.min(far.x() + 0.5, walking.x() + 0.12), GROUND, 0.5); // a slow walk there, 25 s and more
        }
        assertTrue(ivy.failure().isEmpty(), "still walking to the cell 60 off: " + ivy.failure().orElse(""));
    }
}
