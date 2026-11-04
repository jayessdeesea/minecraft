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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against a fake server that answers as the real one does: it breaks a block only once its full
 * break time has passed, and says so - and resends the inventory after a swap - a tick later.
 */
class BreacherTest {

    private static final List<BlockPos> CELLS = List.of(new BlockPos(2, 64, 0), new BlockPos(2, 65, 0));

    private final Set<BlockPos> wall = new HashSet<>(CELLS);
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return wall.contains(new BlockPos(x, y, z));
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
    private final Inventory inventory = new Inventory();
    private final List<String> calls = new ArrayList<>();
    private final List<Runnable> answers = new ArrayList<>();
    private int digStarted;
    private int tick;
    private boolean unbreakable;
    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                BlockPos cell = args != null && args.length == 3 ? new BlockPos((int) args[0], (int) args[1], (int) args[2]) : null;
                switch (method.getName()) {
                    case "startDigging" -> {
                        calls.add("start " + cell);
                        digStarted = tick;
                    }
                    case "finishDigging" -> {
                        boolean pickaxe = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot()))
                                .equals(Optional.of(ItemClass.PICKAXE));
                        if (!unbreakable && tick - digStarted >= (pickaxe ? 30 : 200)) {
                            answers.add(() -> wall.remove(cell));
                        }
                        calls.add("finish " + cell);
                    }
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = {inventory.itemAt(slot), inventory.countAt(slot)};
                        answers.add(() -> {
                            inventory.setSlot(slot, null);
                            inventory.setSlot(hotbar, moving);
                        });
                        calls.add("swap " + slot + " to hotbar " + args[3]);
                    }
                    case "selectHotbarSlot" -> calls.add("select " + args[0]);
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private boolean run(Breacher doorway, int ticks) {
        boolean open = false;
        for (int i = 0; i < ticks && !open; i++, tick++) {
            answers.forEach(Runnable::run);
            answers.clear();
            open = doorway.tick(world, CELLS);
        }
        return open;
    }

    @Test
    void bareHandedItBreaksBothBlocksOfTheDoorway() {
        Breacher doorway = new Breacher("Alice", session, inventory);

        assertTrue(run(doorway, 1000));
        assertEquals(List.of("start " + CELLS.get(0), "finish " + CELLS.get(0), "start " + CELLS.get(1), "finish " + CELLS.get(1)), calls);
    }

    @Test
    void withAPickaxeInThePackItTakesItInHandFirstAndDigsFaster() {
        int pickaxe = IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(ItemClass.PICKAXE)))
                .findFirst().orElseThrow();
        inventory.setSlot(20, new int[]{pickaxe, 1});
        Breacher doorway = new Breacher("Alice", session, inventory);

        assertTrue(run(doorway, 2 * (Breacher.PICKAXE_TICKS + 8)));
        assertEquals(List.of("swap 20 to hotbar 8", "select 8"), calls.subList(0, 2));
        doorway.stop();
        assertEquals("select 0", calls.getLast(), "the weapon hand back");
    }

    @Test
    void aBlockTheServerWontBreakFailsLoudlyThenPausesBeforeTryingAgain() {
        unbreakable = true;
        Breacher doorway = new Breacher("Alice", session, inventory);

        assertFalse(run(doorway, Breacher.HAND_TICKS + Breacher.ANSWER_TICKS + 5));
        assertEquals(List.of("start " + CELLS.get(0), "finish " + CELLS.get(0)), calls);
        assertFalse(run(doorway, Breacher.COOLDOWN_TICKS - 10));
        assertEquals(2, calls.size(), "pausing, not hammering at it");
    }
}
