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
        TreeFarm farm = TreeFarm.site(grass, CENTRE, 6).orElseThrow();

        assertEquals(25, farm.plot().size(), "every third cell of a 13x13 square");
        assertEquals(CENTRE, farm.plot().getFirst(), "nearest the centre first");
        assertTrue(farm.plot().stream().allMatch(c -> Math.floorMod(c.x(), TreeFarm.SPACING) == 0 && Math.floorMod(c.z(), TreeFarm.SPACING) == 0));
    }

    @Test
    void plantsTheSpruceFirstThenWhateverElseItCarries() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item("oak_sapling"), 1});
        inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item("spruce_sapling"), 1});
        TreeFarm farm = new TreeFarm(CENTRE, List.of(CENTRE, CENTRE.offset(3, 0, 0)));
        Forester forester = new Forester("Alice", session, inventory, new HashSet<>());
        Position soldier = new Position(0.5, GROUND, 0.5);

        for (int tick = 0; tick < 400 && saplings.size() < 2; tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            forester.tick(soldier, grass, Optional.of(farm), true);
            soldier = forester.standAt().orElse(soldier); // walks there at once
        }

        assertEquals(Map.of(CENTRE, "spruce", CENTRE.offset(3, 0, 0), "oak"), saplings);
        assertTrue(forester.failure().isEmpty(), forester.failure().orElse(""));
    }
}
