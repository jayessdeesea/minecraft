package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Torchmaker against a fake server that answers each step the way the real one does - the result
 * of a craft into the inventory, a placed block into the world, a window opened, charcoal 200
 * ticks after the furnace is lit - and against one that stops answering.
 */
class TorchmakerTest {

    private static final Position SOLDIER = new Position(0.5, 64, 0.5);
    private static final int TABLE_WINDOW = 1;
    private static final int FURNACE_WINDOW = 2;

    private final Inventory inventory = new Inventory();
    /** Placed blocks, by cell: the item that placed them. */
    private final Map<BlockPos, ItemClass> placed = new HashMap<>();
    private Torchmaker torchmaker;
    private int craftsInTheGrid;
    private int litAt = -1;
    private int tick;
    /** What the fake server ignores - a step it never answers. */
    private String silentOn = "";

    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y < 64 || placed.containsKey(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "click", "closeContainer", "selectHotbarSlot" -> {
                        if (method.getName().equals("click") && (int) args[0] == FURNACE_WINDOW && (int) args[2] == 1) {
                            // A plank in the fuel slot: the furnace lights, the log is in.
                            torchmaker.onContainer(new ContainerUpdate.Slot(FURNACE_WINDOW, 0, 0, item(ItemClass.LOG, 1)));
                            litAt = tick;
                        }
                    }
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = stack(slot);
                        inventory.setSlot(slot, stack(hotbar));
                        inventory.setSlot(hotbar, moving);
                    }
                    case "quickMove" -> quickMove((int) args[0], (int) args[2]);
                    case "useBlock" -> useBlock(new BlockPos((int) args[0], (int) args[1], (int) args[2]));
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    TorchmakerTest() {
        inventory.setSlot(Inventory.HOTBAR_FIRST, new int[]{itemOf(ItemClass.WEAPON), 1});
        inventory.setSlot(9, new int[]{itemOf(ItemClass.LOG), 3});
        inventory.setSlot(10, new int[]{itemOf(ItemClass.COBBLESTONE), 8});
        torchmaker = new Torchmaker("Dana", session, inventory);
    }

    @Test
    void makesTorchesStepByStepAsTheServerAnswers() {
        assertTrue(Torchmaker.canMake(inventory));

        run(1000);

        assertEquals(4, inventory.count(ItemClass.TORCH));
        assertFalse(torchmaker.active());
        assertEquals(Optional.empty(), torchmaker.failure());
        assertEquals(List.of(ItemClass.CRAFTING_TABLE, ItemClass.FURNACE), List.copyOf(placed.values().stream().sorted().toList()));
        assertEquals(0, inventory.heldHotbarSlot(), "its weapon back in hand");
        assertTrue(tick > 200, "the smelt took its 200 ticks - nothing moved on early");
    }

    @Test
    void aFurnaceThatNeverOpensFailsLoudlyAndSaysWhat() {
        silentOn = "furnace";

        run(1000);

        assertFalse(torchmaker.active());
        assertEquals(Optional.of("can't make torches: open the furnace: expected the furnace's window within "
                + Torchmaker.ANSWER_TICKS + " ticks; it didn't come"), torchmaker.failure());
        assertEquals(0, inventory.count(ItemClass.TORCH));
    }

    @Test
    void theWrongWindowFailsTheStepAtOnce() {
        silentOn = "wrong menu";

        run(1000);

        assertTrue(torchmaker.failure().orElseThrow().startsWith("can't make torches: open the crafting table: after 1 ticks: menu type 14 opened, not 12"),
                torchmaker.failure().orElseThrow());
    }

    private void run(int ticks) {
        for (tick = 0; tick < ticks; tick++) {
            if (litAt >= 0 && tick == litAt + 200) {
                torchmaker.onContainer(new ContainerUpdate.Slot(FURNACE_WINDOW, 0, 0, Optional.empty()));
                torchmaker.onContainer(new ContainerUpdate.Slot(FURNACE_WINDOW, 0, 2, item(ItemClass.COAL, 1)));
            }
            torchmaker.tick(SOLDIER, world, true);
            if (!torchmaker.active() && (inventory.count(ItemClass.TORCH) > 0 || torchmaker.failure().isPresent())) {
                return;
            }
        }
    }

    private void quickMove(int window, int slot) {
        if (window == 0) {
            switch (++craftsInTheGrid) {
                case 1 -> { // 2 logs: 8 planks
                    inventory.setSlot(9, new int[]{itemOf(ItemClass.LOG), 1});
                    inventory.setSlot(11, new int[]{itemOf(ItemClass.PLANKS), 8});
                }
                case 2 -> { // 2 planks: 4 sticks
                    inventory.setSlot(11, new int[]{itemOf(ItemClass.PLANKS), 6});
                    inventory.setSlot(12, new int[]{itemOf(ItemClass.STICK), 4});
                }
                case 3 -> { // 4 planks: a table
                    inventory.setSlot(11, new int[]{itemOf(ItemClass.PLANKS), 2});
                    inventory.setSlot(13, new int[]{itemOf(ItemClass.CRAFTING_TABLE), 1});
                }
                case 4 -> { // charcoal over a stick: 4 torches
                    inventory.setSlot(14, null);
                    inventory.setSlot(12, new int[]{itemOf(ItemClass.STICK), 3});
                    inventory.setSlot(15, new int[]{itemOf(ItemClass.TORCH), 4});
                }
                default -> throw new AssertionError("a fifth craft in the grid");
            }
        } else if (window == TABLE_WINDOW) { // 8 cobblestone: a furnace - through the table's window, inventory slot + 1
            torchmaker.onContainer(new ContainerUpdate.Slot(TABLE_WINDOW, 0, 10 + 1, item(ItemClass.FURNACE, 1)));
        } else if (window == FURNACE_WINDOW && slot == 2) { // the charcoal out - through the furnace's window, inventory slot - 6
            torchmaker.onContainer(new ContainerUpdate.Slot(FURNACE_WINDOW, 0, 2, Optional.empty()));
            torchmaker.onContainer(new ContainerUpdate.Slot(FURNACE_WINDOW, 0, 14 - 6, item(ItemClass.COAL, 1)));
        }
    }

    private void useBlock(BlockPos at) {
        ItemClass block = placed.get(at);
        if (block != null) { // opening it
            if (block == ItemClass.FURNACE && silentOn.equals("furnace")) {
                return;
            }
            boolean furnace = block == ItemClass.FURNACE || silentOn.equals("wrong menu");
            torchmaker.onContainer(new ContainerUpdate.Opened(furnace ? FURNACE_WINDOW : TABLE_WINDOW,
                    furnace ? Torchmaker.FURNACE_MENU : Torchmaker.CRAFTING_MENU));
            return;
        }
        // Placing what's in hand on top of the block at {@code at}.
        int held = Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot();
        placed.put(new BlockPos(at.x(), at.y() + 1, at.z()), ItemClassRegistry.classOf(inventory.itemAt(held)).orElseThrow());
        inventory.setSlot(held, null);
    }

    private int[] stack(int slot) {
        return inventory.itemAt(slot) < 0 ? null : new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
    }

    private static Optional<ItemSlot> item(ItemClass itemClass, int count) {
        return Optional.of(new ItemSlot(itemOf(itemClass), count, 0, false, Map.of()));
    }

    private static int itemOf(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }
}
