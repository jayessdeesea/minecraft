package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * The server's side of crafting, as far as a soldier can tell: an inventory that clicks pick up,
 * put down, swap and place one at a time into; a 2x2 grid and a table's 3x3 that make what a
 * shaped {@link Recipe} matches on a shift-click of the result; tables and furnaces placed and
 * opened; a furnace that burns a plank for 300 ticks and smelts in 200. Every container window is
 * sent whole after each click, as the real server does for a click with a stale state ID; the
 * inventory's own window goes straight into {@link Inventory}, as the soldier's connection does.
 */
final class FakeCraftingServer {

    private static final int FURNACE_MENU = 14;
    private static final int CRAFTING_MENU = 12;

    final Inventory inventory = new Inventory();
    /** Inventory slots 0-45 on the server: [item, count] or null. */
    private final int[][] slots = new int[Inventory.SLOTS][];
    private final int[][] grid2 = new int[5][];
    private int[] cursor;
    private int held;
    final Map<BlockPos, Workstation> stations = new HashMap<>();
    /** Chests put down: solid, and opened by nothing here. */
    final java.util.Set<BlockPos> chests = new java.util.HashSet<>();
    private final Map<BlockPos, int[][]> furnaces = new HashMap<>();
    private final Map<BlockPos, int[]> burning = new HashMap<>(); // fuel ticks left, smelt progress
    private int[][] tableGrid;
    private BlockPos open;
    private int openId = -1;
    private int nextId = 1;
    /** What the server did, for a test to count. */
    final List<String> log = new ArrayList<>();
    /** Containers that don't answer when used. */
    final java.util.Set<Workstation> silent = java.util.EnumSet.noneOf(Workstation.class);
    Consumer<ContainerUpdate> client = u -> { };
    /** Squadmates standing about: the server won't place a block into one. */
    final List<Position> bodies = new ArrayList<>();

    final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            return y < 64 || stations.containsKey(new BlockPos(x, y, z)) || chests.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isHazard(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isChest(int x, int y, int z) {
            return chests.contains(new BlockPos(x, y, z));
        }

        @Override
        public Optional<Workstation> workstation(int x, int y, int z) {
            return Optional.ofNullable(stations.get(new BlockPos(x, y, z)));
        }

        @Override
        public List<Position> bodiesNear(Position center, double radius) {
            return bodies;
        }

        @Override
        public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
            return List.of();
        }
    };

    final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "click" -> click((int) args[0], (int) args[2], (boolean) args[3]);
                    case "quickMove" -> quickMove((int) args[0], (int) args[2]);
                    case "closeContainer" -> close((int) args[0]);
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        int[] moving = slots[slot];
                        slots[slot] = slots[hotbar];
                        slots[hotbar] = moving;
                        sync();
                    }
                    case "selectHotbarSlot" -> held = (int) args[0];
                    case "useBlock" -> use(new BlockPos((int) args[0], (int) args[1], (int) args[2]));
                    // A block placed against a face: into the cell beside it, as useBlock on the ground below that cell.
                    case "useBlockFace" -> {
                        io.github.jayessdeesea.minecraft.protocol.BlockFace face = (io.github.jayessdeesea.minecraft.protocol.BlockFace) args[3];
                        use(new BlockPos((int) args[0] + face.dx, (int) args[1] + face.dy - 1, (int) args[2] + face.dz));
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    /** Gives the soldier {@code count} of the first item of {@code itemClass}, into {@code slot}. */
    FakeCraftingServer give(int slot, ItemClass itemClass, int count) {
        return give(slot, itemOf(itemClass, 0), count);
    }

    FakeCraftingServer give(int slot, int itemId, int count) {
        slots[slot] = new int[]{itemId, count};
        sync();
        return this;
    }

    /** One server tick: furnaces burn and smelt. */
    void tick() {
        furnaces.forEach((at, f) -> {
            int[] state = burning.computeIfAbsent(at, a -> new int[2]);
            if (f[0] == null) {
                state[1] = 0;
                return;
            }
            if (state[0] == 0 && f[1] != null) {
                take(f, 1);
                state[0] = 300;
            }
            if (state[0] == 0) {
                return;
            }
            state[0]--;
            if (++state[1] >= 200) {
                state[1] = 0;
                int out = smelted(f[0][0]);
                take(f, 0);
                f[2] = f[2] == null ? new int[]{out, 1} : new int[]{f[2][0], f[2][1] + 1};
                if (at.equals(open)) {
                    sendWindow();
                }
            }
        });
    }

    private void click(int window, int slot, boolean right) {
        if (window != 0 && window != openId) {
            return; // the server ignores a click for a window that isn't open
        }
        int[][] holder = holderFor(window, slot);
        int index = indexFor(window, slot);
        int[] there = holder[index];
        if (cursor == null) {
            if (there != null && !right) {
                cursor = there;
                holder[index] = null;
            }
        } else if (there == null || there[0] == cursor[0]) {
            int moving = right ? 1 : cursor[1];
            int count = there == null ? 0 : there[1];
            int fits = Math.min(moving, 64 - count);
            holder[index] = new int[]{cursor[0], count + fits};
            cursor = cursor[1] - fits == 0 ? null : new int[]{cursor[0], cursor[1] - fits};
        } else {
            holder[index] = cursor;
            cursor = there;
        }
        sync();
    }

    private void quickMove(int window, int slot) {
        if (window != 0 && window != openId) {
            return;
        }
        if (open != null && stations.get(open) == Workstation.FURNACE) {
            int[][] f = furnaces.computeIfAbsent(open, k -> new int[3][]);
            if (slot < 3 && f[slot] != null) {
                store(f[slot]);
                f[slot] = null;
            }
        } else if (slot == 0) {
            int[][] grid = window == 0 ? grid2 : tableGrid;
            int width = window == 0 ? 2 : 3;
            Optional<Recipe> recipe;
            while ((recipe = match(grid, width)).isPresent()) {
                Recipe r = recipe.get();
                for (int cell = 1; cell < grid.length; cell++) {
                    if (grid[cell] != null) {
                        take(grid, cell);
                    }
                }
                store(new int[]{r.item().map(FakeCraftingServer::itemNamed).orElseGet(() -> itemOf(r.makes(), 0)), r.yield()});
                log.add("crafted " + r.name());
            }
        }
        sync();
    }

    /** The recipe the grid's contents match - shaped, anywhere in the grid. */
    private static Optional<Recipe> match(int[][] grid, int width) {
        int minRow = 9, minColumn = 9, maxRow = -1, maxColumn = -1;
        for (int cell = 1; cell < grid.length; cell++) {
            if (grid[cell] != null) {
                int row = (cell - 1) / width, column = (cell - 1) % width;
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }
        }
        if (maxRow < 0) {
            return Optional.empty();
        }
        for (Recipe recipe : Recipe.ALL) {
            if (recipe.station() == Recipe.Station.FURNACE || recipe.rows().size() != maxRow - minRow + 1) {
                continue;
            }
            int columns = recipe.rows().stream().mapToInt(String::length).max().orElseThrow();
            if (columns != maxColumn - minColumn + 1) {
                continue;
            }
            boolean matches = true;
            for (int row = 0; row < recipe.rows().size() && matches; row++) {
                String pattern = recipe.rows().get(row);
                for (int column = 0; column < columns && matches; column++) {
                    char c = column < pattern.length() ? pattern.charAt(column) : ' ';
                    int[] item = grid[1 + (minRow + row) * width + minColumn + column];
                    matches = c == ' ' ? item == null
                            : item != null && ItemClassRegistry.classOf(item[0]).equals(Optional.of(recipe.key().get(c)));
                }
            }
            if (matches) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }

    private void close(int window) {
        int[][] grid = window == 0 ? grid2 : window == openId && stations.get(open) == Workstation.CRAFTING_TABLE ? tableGrid : null;
        if (grid != null) {
            for (int cell = 1; cell < grid.length; cell++) {
                if (grid[cell] != null) {
                    store(grid[cell]);
                    grid[cell] = null;
                }
            }
        }
        if (cursor != null) {
            store(cursor);
            cursor = null;
        }
        if (window != 0 && window == openId) {
            open = null;
            openId = -1;
        }
        sync();
    }

    private void use(BlockPos at) {
        Workstation station = stations.get(at);
        if (station != null) {
            if (silent.contains(station)) {
                return;
            }
            open = at;
            openId = nextId++;
            tableGrid = new int[10][];
            log.add("opened " + station);
            client.accept(new ContainerUpdate.Opened(openId, station == Workstation.FURNACE ? FURNACE_MENU : CRAFTING_MENU));
            sendWindow();
            return;
        }
        int hand = Inventory.HOTBAR_FIRST + held;
        int[] item = slots[hand];
        Optional<ItemClass> itemClass = item == null ? Optional.empty() : ItemClassRegistry.classOf(item[0]);
        BlockPos above = new BlockPos(at.x(), at.y() + 1, at.z());
        boolean blocked = bodies.stream().anyMatch(b -> (int) Math.floor(b.x()) == above.x() && (int) Math.floor(b.z()) == above.z());
        if (!blocked && itemClass.equals(Optional.of(ItemClass.CHEST))) {
            chests.add(above);
            take(slots, hand);
            log.add("placed CHEST");
            sync();
            return;
        }
        if (!blocked && (itemClass.equals(Optional.of(ItemClass.CRAFTING_TABLE)) || itemClass.equals(Optional.of(ItemClass.FURNACE)))) {
            Workstation placed = itemClass.get() == ItemClass.FURNACE ? Workstation.FURNACE : Workstation.CRAFTING_TABLE;
            stations.put(above, placed);
            if (placed == Workstation.FURNACE) {
                furnaces.put(above, new int[3][]);
            }
            take(slots, hand);
            log.add("placed " + placed);
            sync();
        }
    }

    private int[][] holderFor(int window, int slot) {
        if (window == 0) {
            return slot >= 1 && slot <= 4 ? grid2 : slots;
        }
        int size = containerSize();
        if (slot < size) {
            return stations.get(open) == Workstation.FURNACE ? furnaces.computeIfAbsent(open, k -> new int[3][]) : tableGrid;
        }
        return slots;
    }

    private int indexFor(int window, int slot) {
        if (window == 0) {
            return slot;
        }
        int size = containerSize();
        return slot < size ? slot : slot - size + Inventory.MAIN_FIRST;
    }

    private int containerSize() {
        return stations.get(open) == Workstation.FURNACE ? 3 : 10;
    }

    private static void take(int[][] holder, int index) {
        holder[index] = holder[index][1] == 1 ? null : new int[]{holder[index][0], holder[index][1] - 1};
    }

    /** Into the inventory: onto a stack of the same item with room, then into the first empty slot. */
    private void store(int[] item) {
        int left = item[1];
        for (int slot = Inventory.MAIN_FIRST; slot < Inventory.SLOTS - 1 && left > 0; slot++) {
            if (slots[slot] != null && slots[slot][0] == item[0] && slots[slot][1] < 64) {
                int fits = Math.min(left, 64 - slots[slot][1]);
                slots[slot] = new int[]{item[0], slots[slot][1] + fits};
                left -= fits;
            }
        }
        for (int slot = Inventory.MAIN_FIRST; slot < Inventory.SLOTS - 1 && left > 0; slot++) {
            if (slots[slot] == null) {
                slots[slot] = new int[]{item[0], left};
                left = 0;
            }
        }
        if (left > 0) {
            throw new AssertionError("inventory full");
        }
    }

    /** The inventory to the soldier: straight in for its own window, through the open container's otherwise. */
    private void sync() {
        if (open != null) {
            sendWindow();
        } else {
            for (int slot = 0; slot < Inventory.SLOTS; slot++) {
                inventory.setSlot(slot, slots[slot]);
            }
        }
    }

    private void sendWindow() {
        int size = containerSize();
        int[][] own = stations.get(open) == Workstation.FURNACE ? furnaces.computeIfAbsent(open, k -> new int[3][]) : tableGrid;
        List<Optional<ItemSlot>> window = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            window.add(item(own[i]));
        }
        for (int slot = Inventory.MAIN_FIRST; slot < Inventory.SLOTS - 1; slot++) {
            window.add(item(slots[slot]));
        }
        client.accept(new ContainerUpdate.Contents(openId, 0, window, true));
    }

    private static Optional<ItemSlot> item(int[] stack) {
        return stack == null ? Optional.empty() : Optional.of(new ItemSlot(stack[0], stack[1], 0, false, Map.of()));
    }

    int count(ItemClass itemClass) {
        return IntStream.range(Inventory.MAIN_FIRST, Inventory.SLOTS - 1).filter(s -> slots[s] != null)
                .filter(s -> ItemClassRegistry.classOf(slots[s][0]).equals(Optional.of(itemClass))).map(s -> slots[s][1]).sum();
    }

    long logged(String line) {
        return log.stream().filter(line::equals).count();
    }

    /** The {@code nth} item of {@code itemClass} in the registry - two kinds of log, say. */
    /** What smelting {@code input} makes: the furnace recipe for its class. */
    private static int smelted(int input) {
        ItemClass in = ItemClassRegistry.classOf(input).orElseThrow();
        Recipe recipe = Recipe.ALL.stream().filter(r -> r.station() == Recipe.Station.FURNACE && r.key().containsValue(in)).findFirst()
                .orElseThrow(() -> new AssertionError("nothing smelts " + in));
        return itemOf(recipe.makes(), 0);
    }

    static int itemNamed(String name) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of(name))).findFirst().orElseThrow();
    }

    static int itemOf(ItemClass itemClass, int nth) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass)))
                .skip(nth).findFirst().orElseThrow();
    }
}
