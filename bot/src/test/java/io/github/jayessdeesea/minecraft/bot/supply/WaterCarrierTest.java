package io.github.jayessdeesea.minecraft.bot.supply;

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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterCarrierTest {

    private static final int GROUND = 63;
    /** A pond of three sources in a row, x 10..12: the middle one has two beside it. */
    private final Set<BlockPos> water = new HashSet<>(Set.of(new BlockPos(10, GROUND, 0), new BlockPos(11, GROUND, 0), new BlockPos(12, GROUND, 0)));
    /** The hole to water, dug in the ground. */
    private static final BlockPos HOLE = new BlockPos(0, GROUND, 0);
    private final Inventory inventory = new Inventory();
    private final List<Runnable> answers = new ArrayList<>();
    private final List<String> used = new ArrayList<>();

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            BlockPos c = new BlockPos(x, y, z);
            return y <= GROUND && !water.contains(c) && !c.equals(HOLE);
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return water.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isWater(int x, int y, int z) {
            return water.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isWaterSource(int x, int y, int z) {
            return water.contains(new BlockPos(x, y, z));
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    /** The server: a bucket fills, a full one pours into the hole - each answered a tick later, the look recorded. */
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "useItem" -> {
                        int slot = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
                        ItemClass held = ItemClassRegistry.classOf(inventory.itemAt(slot)).orElse(null);
                        used.add(held + " pitch " + Math.round((float) args[1]));
                        if (held == ItemClass.BUCKET) {
                            answers.add(() -> inventory.setSlot(slot, new int[]{item(ItemClass.WATER_BUCKET), 1}));
                        } else if (held == ItemClass.WATER_BUCKET) {
                            answers.add(() -> {
                                inventory.setSlot(slot, new int[]{item(ItemClass.BUCKET), 1});
                                water.add(HOLE);
                            });
                        }
                    }
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
                        answers.add(() -> {
                            inventory.setSlot(slot, null);
                            inventory.setSlot(hotbar, moving);
                        });
                    }
                    case "selectHotbarSlot" -> {
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    /** An empty bucket filled at the pond's middle source, walked to the hole, and poured: looking down into it. */
    @Test
    void aBucketIsFilledAtThePondAndPouredIntoTheHole() {
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.BUCKET), 1});
        WaterCarrier carrier = new WaterCarrier("Alice", session, inventory);
        Position soldier = new Position(5.5, GROUND + 1, 0.5);
        WaterCarrier.Progress progress = null;
        for (int tick = 0; tick < 200 && !(progress instanceof WaterCarrier.Progress.Done); tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            progress = carrier.tick(soldier, world, HOLE);
            soldier = carrier.standAt().orElse(soldier);
        }

        assertInstanceOf(WaterCarrier.Progress.Done.class, progress);
        assertTrue(world.isWaterSource(HOLE.x(), HOLE.y(), HOLE.z()));
        assertEquals(2, used.size(), "filled once, poured once: " + used);
        assertTrue(used.get(0).startsWith("BUCKET"), used.toString());
        assertTrue(used.get(1).startsWith("WATER_BUCKET") && Integer.parseInt(used.get(1).replaceAll(".* ", "")) > 45,
                "poured looking down into the hole: " + used);
    }

    /** A lone source isn't a pond: a bucket empties it for good. Nor a cell with no ground beside it to pour from. */
    @Test
    void aLoneSourceIsNoPond() {
        water.clear();
        water.add(new BlockPos(10, GROUND, 0));
        assertEquals(Optional.empty(), WaterCarrier.pond(world, HOLE));
        inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.BUCKET), 1});
        assertInstanceOf(WaterCarrier.Progress.Failed.class, new WaterCarrier("Alice", session, inventory).tick(new Position(0.5, GROUND + 1, 1.5), world, HOLE));
    }

    /** No bucket, no water carried. */
    @Test
    void withNoBucketItSaysSo() {
        WaterCarrier.Progress progress = new WaterCarrier("Alice", session, inventory).tick(new Position(0.5, GROUND + 1, 1.5), world, HOLE);
        assertEquals(new WaterCarrier.Progress.Failed("no bucket carried"), progress);
    }
}
