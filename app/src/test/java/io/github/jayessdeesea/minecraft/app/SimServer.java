package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.BlockChange;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.EntityMove;
import io.github.jayessdeesea.minecraft.protocol.EntityUpdate;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.ChunkUpdate;
import io.github.jayessdeesea.minecraft.protocol.HealthUpdate;
import io.github.jayessdeesea.minecraft.protocol.InventoryUpdate;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import io.github.jayessdeesea.minecraft.protocol.PlayState;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import io.github.jayessdeesea.minecraft.protocol.PlaySessionListeners;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import io.github.jayessdeesea.minecraft.protocol.TimeUpdate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The server's side of a squad simulation: one world, and a {@link PlaySession} per soldier that
 * answers as the vanilla server does, a tick late - blocks broken and placed, items picked up,
 * inventory slots clicked and swapped, crafting in the 2x2 grid and at a table, a furnace that
 * burns a plank for 300 ticks and smelts in 200, the time of day; zombies that walk at the nearest
 * soldier, hit it, and die to its blows; and a soldier killed respawning empty-handed at spawn. What it doesn't model throws, naming the action, so a
 * scenario that needs more says so instead of quietly doing nothing.
 *
 * <p>Block-state and item IDs are protocol 777's (the pinned server's reports).
 */
final class SimServer {

    static final int AIR = 0, STONE = 1, GRASS = 9, DIRT = 10, COBBLESTONE = 14, BEDROCK = 88, WATER = 89,
            IRON_ORE = 134, COAL_ORE = 136, TORCH = 4926, OAK_LOG = 140, CRAFTING_TABLE = 6866, FURNACE = 6884;
    static final int ITEM_COBBLESTONE = 62, ITEM_DIRT = 55, ITEM_RAW_IRON = 1017, ITEM_COAL = 1010,
            ITEM_WOODEN_PICKAXE = 1027, ITEM_STONE_PICKAXE = 1037, ITEM_WOODEN_SWORD = 1025, ITEM_BREAD = 1067,
            ITEM_TORCH = 395, ITEM_OAK_LOG = 163;
    private static final int CRAFTING_MENU = 12, FURNACE_MENU = 14;
    static final int ZOMBIE_TYPE = 154;
    /** A zombie: 20 health, 3 a hit once a second within reach, a slow walk. */
    private static final double ZOMBIE_REACH = 1.5, ZOMBIE_STEP = 0.08;
    private static final int ZOMBIE_HIT = 3, ZOMBIE_COOLDOWN = 20;
    private static final int MIN_Y = -64;
    private static final int SECTIONS = 24;
    /** Chunks sent round a soldier on joining: radius 3, 7x7. */
    private static final int VIEW = 3;
    /** How far from the eyes a block may be broken or clicked: the server's interaction range, with slack. */
    private static final double REACH = 6;
    /** How far a thrown item carries to be picked up. */
    private static final double THROW_REACH = 5;

    private final SimClock clock;
    private final Terrain ground;
    private final Map<Long, Integer> placed = new HashMap<>();
    private final Map<String, Player> players = new LinkedHashMap<>();
    /** Each furnace placed: input, fuel, output - [item, count] or null - and fuel ticks left, smelt progress. */
    private final Map<Long, int[][]> furnaces = new HashMap<>();
    private final Map<Long, int[]> burning = new HashMap<>();
    /** What happened, in order, for a scenario to read. */
    final List<String> log = new ArrayList<>();
    private long dayTicks;
    private int nextEntity = 1000;
    /** Each zombie: [x, y, z, health, cooldown] by entity ID. */
    private final Map<Integer, double[]> zombies = new LinkedHashMap<>();
    /** Deaths by soldier, for a scenario to count. */
    final Map<String, Integer> deaths = new HashMap<>();

    /** The world before anyone touches it: the block-state at each cell. */
    interface Terrain {
        int at(int x, int y, int z);
    }

    SimServer(SimClock clock, Terrain ground, long dayTicks) {
        this.clock = clock;
        this.ground = ground;
        this.dayTicks = dayTicks;
        clock.scheduler("server").scheduleAtFixedRate(this::tick, 50, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    int block(int x, int y, int z) {
        Integer changed = placed.get(key(x, y, z));
        return changed != null ? changed : ground.at(x, y, z);
    }

    private void set(int x, int y, int z, int state) {
        placed.put(key(x, y, z), state);
        players.values().forEach(p -> p.listeners.onBlockChanges().accept(List.of(new BlockChange(x, y, z, state))));
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y - MIN_Y) & 0xFFF;
    }

    long dayTicks() {
        return dayTicks;
    }

    /** A zombie at the given cell's feet, seen by every soldier at once. */
    int spawnZombie(double x, double y, double z) {
        int id = nextEntity++;
        zombies.put(id, new double[]{x, y, z, 20, 0});
        players.values().forEach(p -> p.listeners.onEntitySpawn()
                .accept(new EntityUpdate(id, java.util.UUID.randomUUID(), ZOMBIE_TYPE, x, y, z)));
        log.add("a zombie spawned at " + x + "," + y + "," + z);
        return id;
    }

    float health(String name) {
        return players.get(name).health;
    }

    int zombiesLeft() {
        return zombies.size();
    }

    private void zombies() {
        for (Map.Entry<Integer, double[]> e : List.copyOf(zombies.entrySet())) {
            double[] z = e.getValue();
            Player target = players.values().stream().filter(p -> p.health > 0)
                    .min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.position.x() - z[0], p.position.z() - z[2])))
                    .orElse(null);
            if (target == null) {
                continue;
            }
            double dx = target.position.x() - z[0], dz = target.position.z() - z[2], d = Math.hypot(dx, dz);
            if (d > ZOMBIE_REACH * 0.8) {
                // Walks straight at it, stopped by a wall in the way.
                double nx = z[0] + dx / d * ZOMBIE_STEP, nz = z[2] + dz / d * ZOMBIE_STEP;
                int fy = (int) Math.floor(z[1]);
                if (block((int) Math.floor(nx), fy, (int) Math.floor(nz)) == AIR && block((int) Math.floor(nx), fy + 1, (int) Math.floor(nz)) == AIR) {
                    z[0] = nx;
                    z[2] = nz;
                    players.values().forEach(p -> p.listeners.onEntityMove().accept(new EntityMove(e.getKey(), z[0], z[1], z[2])));
                }
            }
            if (z[4] > 0) {
                z[4]--;
            } else if (d <= ZOMBIE_REACH && Math.abs(target.position.y() - z[1]) < 2) {
                z[4] = ZOMBIE_COOLDOWN;
                target.hurt(ZOMBIE_HIT, "death.attack.mob[" + target.name + ", entity.minecraft.zombie]");
            }
        }
    }

    private void tick() {
        dayTicks++;
        zombies();
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
                players.values().stream().filter(p -> p.open != null && p.open == at).forEach(Player::sync);
            }
        });
        if (dayTicks % 20 == 0) {
            players.values().forEach(Player::sendTime);
        }
    }

    /** The joins: each soldier spawns at {@code spawn} carrying {@code kit} ([slot, item, count] each). */
    SoldierRunner.Connector connector(PlayerPosition spawn, List<int[]> kit) {
        return connector(name -> spawn, name -> kit);
    }

    /** As {@link #connector(PlayerPosition, List)}, each soldier's spawn and kit its own. */
    SoldierRunner.Connector connector(java.util.function.Function<String, PlayerPosition> spawns,
                                      java.util.function.Function<String, List<int[]>> kits) {
        return (name, listeners) -> {
            Player player = new Player(name, listeners, spawns.apply(name));
            players.put(name, player);
            kits.apply(name).forEach(k -> player.slots[k[0]] = new int[]{k[1], k[2]});
            // As the server sends them: registries, the world, where it stands, its kit, its health.
            clock.scheduler(name + "-join").schedule(player::join, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
            return CompletableFuture.completedFuture(player);
        };
    }

    PlayerPosition position(String name) {
        return players.get(name).position;
    }

    int count(String name, int item) {
        int total = 0;
        for (int[] slot : players.get(name).slots) {
            if (slot != null && slot[0] == item) {
                total += slot[1];
            }
        }
        return total;
    }

    /** What a block drops broken with what's in hand: stone and ore only to a pickaxe. */
    private static Optional<Integer> drop(int state, Optional<Integer> held) {
        boolean pickaxe = held.filter(i -> i == ITEM_WOODEN_PICKAXE || i == ITEM_STONE_PICKAXE).isPresent();
        boolean stonePick = held.filter(i -> i == ITEM_STONE_PICKAXE).isPresent();
        return switch (state) {
            case GRASS, DIRT -> Optional.of(ITEM_DIRT);
            case STONE, COBBLESTONE -> pickaxe ? Optional.of(ITEM_COBBLESTONE) : Optional.empty();
            case COAL_ORE -> pickaxe ? Optional.of(ITEM_COAL) : Optional.empty();
            case IRON_ORE -> stonePick ? Optional.of(ITEM_RAW_IRON) : Optional.empty();
            case TORCH -> Optional.of(ITEM_TORCH);
            case OAK_LOG -> Optional.of(ITEM_OAK_LOG);
            case CRAFTING_TABLE -> Optional.of(itemOf(ItemClass.CRAFTING_TABLE));
            default -> throw new UnsupportedOperationException("simulated: breaking block state " + state);
        };
    }

    /** The block an item places, if it's one the simulation knows. */
    private static Optional<Integer> placesAs(int item) {
        return switch (item) {
            case ITEM_COBBLESTONE -> Optional.of(COBBLESTONE);
            case ITEM_DIRT -> Optional.of(DIRT);
            case ITEM_TORCH -> Optional.of(TORCH);
            default -> Optional.empty();
        };
    }

    private static void take(int[][] holder, int index) {
        holder[index] = holder[index][1] == 1 ? null : new int[]{holder[index][0], holder[index][1] - 1};
    }

    /** What smelting {@code input} makes: the furnace recipe for its class. */
    private static int smelted(int input) {
        ItemClass in = ItemClassRegistry.classOf(input).orElseThrow();
        Recipe recipe = Recipe.ALL.stream().filter(r -> r.station() == Recipe.Station.FURNACE && r.key().containsValue(in)).findFirst()
                .orElseThrow(() -> new AssertionError("nothing smelts " + in));
        return itemOf(recipe.makes());
    }

    static int itemOf(ItemClass itemClass) {
        return java.util.stream.IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass)))
                .findFirst().orElseThrow();
    }

    private static int itemNamedOrMinus(String name) {
        return java.util.stream.IntStream.range(0, 4096).filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of(name)))
                .findFirst().orElse(-1);
    }

    private static int itemNamed(String name) {
        return java.util.stream.IntStream.range(0, 4096).filter(id -> ItemClassRegistry.nameOf(id).equals(Optional.of(name)))
                .findFirst().orElseThrow();
    }

    /** The recipe a grid's contents match - shaped, anywhere in the grid. */
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
            if (columns != maxColumn - minColumn + 1 || columns > width) {
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

    private final class Player implements PlaySession {
        final String name;
        final PlaySessionListeners listeners;
        PlayerPosition position;
        final PlayerPosition spawn;
        float health = 20;
        final int[][] slots = new int[46][];
        int held;
        boolean connected = true;
        final int[][] grid2 = new int[5][];
        int[] cursor;
        int[][] tableGrid;
        /** The table or furnace open, by cell key, and its window's ID. */
        Long open;
        int openId = -1;
        int nextId = 1;

        Player(String name, PlaySessionListeners listeners, PlayerPosition spawn) {
            this.name = name;
            this.listeners = listeners;
            this.position = spawn;
            this.spawn = spawn;
        }

        void hurt(int damage, String cause) {
            if (health <= 0) {
                return;
            }
            health = Math.max(0, health - damage);
            listeners.onHealth().accept(new HealthUpdate(health, 20));
            if (health == 0) {
                deaths.merge(name, 1, Integer::sum);
                log.add(name + " died: " + cause);
                listeners.onDeath().accept(cause);
            }
        }

        void join() {
            listeners.onRegistry().accept("minecraft:world_clock", List.of("minecraft:overworld"));
            listeners.onRegistry().accept("minecraft:enchantment", List.of());
            int cx = Math.floorDiv((int) Math.floor(position.x()), 16);
            int cz = Math.floorDiv((int) Math.floor(position.z()), 16);
            for (int dx = -VIEW; dx <= VIEW; dx++) {
                for (int dz = -VIEW; dz <= VIEW; dz++) {
                    listeners.onChunkUpdate().accept(chunk(cx + dx, cz + dz));
                }
            }
            listeners.onPlayerPosition().accept(position);
            sendInventory();
            listeners.onHealth().accept(new HealthUpdate(20, 20));
            sendTime();
        }

        void sendTime() {
            listeners.onTime().accept(new TimeUpdate(dayTicks, Map.of(0, new TimeUpdate.Clock(dayTicks, 1))));
        }

        private ChunkUpdate chunk(int cx, int cz) {
            List<int[]> sections = new ArrayList<>(SECTIONS);
            for (int s = 0; s < SECTIONS; s++) {
                int[] states = new int[4096];
                for (int i = 0; i < 4096; i++) {
                    int x = cx * 16 + (i & 15), z = cz * 16 + (i >> 4 & 15), y = MIN_Y + s * 16 + (i >> 8);
                    states[i] = block(x, y, z);
                }
                sections.add(states);
            }
            return new ChunkUpdate(cx, cz, sections);
        }

        private Optional<ItemSlot> item(int slot) {
            int[] s = slots[slot];
            return s == null ? Optional.empty() : Optional.of(new ItemSlot(s[0], s[1], 0, false, Map.of()));
        }

        private void sendInventory() {
            List<Optional<ItemSlot>> all = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) {
                all.add(item(i));
            }
            listeners.onInventory().accept(new InventoryUpdate.Contents(all, true));
            listeners.onInventory().accept(new InventoryUpdate.HeldSlot(held));
        }

        private void sendSlot(int slot) {
            listeners.onInventory().accept(new InventoryUpdate.Slot(slot, item(slot)));
        }

        private Optional<Integer> heldItem() {
            int[] s = slots[36 + held];
            return s == null ? Optional.empty() : Optional.of(s[0]);
        }

        private void give(int item) {
            for (int pass = 0; pass < 2; pass++) {
                for (int slot : order()) {
                    int[] s = slots[slot];
                    if (pass == 0 && s != null && s[0] == item && s[1] < 64 || pass == 1 && s == null) {
                        slots[slot] = s == null ? new int[]{item, 1} : new int[]{item, s[1] + 1};
                        sendSlot(slot);
                        return;
                    }
                }
            }
            log.add(name + "'s inventory is full: " + item + " left on the ground");
        }

        /** Where a picked-up item goes: the hotbar first, then the main inventory. */
        private int[] order() {
            int[] order = new int[36];
            for (int i = 0; i < 9; i++) {
                order[i] = 36 + i;
            }
            for (int i = 0; i < 27; i++) {
                order[9 + i] = 9 + i;
            }
            return order;
        }

        @Override
        public PlayState playState() {
            return new PlayState(players.size(), "minecraft:overworld", 0);
        }

        @Override
        public void reportPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
            position = new PlayerPosition(x, y, z);
        }

        @Override
        public void endTick() {
        }

        @Override
        public void respawn() {
            // Kit dropped where it died - not modelled as items on the ground: it's gone.
            java.util.Arrays.fill(slots, null);
            health = 20;
            position = spawn;
            sendInventory();
            listeners.onHealth().accept(new HealthUpdate(health, 20));
            listeners.onPlayerPosition().accept(spawn);
        }

        @Override
        public void attack(int entityId) {
            double[] z = zombies.get(entityId);
            if (z == null) {
                return;
            }
            if (Math.hypot(z[0] - position.x(), z[2] - position.z()) > 3.5) {
                log.add(name + " REFUSED: attack on " + entityId + " out of reach");
                return;
            }
            int damage = heldItem().map(i -> i == ITEM_WOODEN_SWORD ? 4 : itemNamedOrMinus("stone_sword") == i ? 5 : 1).orElse(1);
            z[3] -= damage;
            if (z[3] <= 0) {
                zombies.remove(entityId);
                players.values().forEach(p -> p.listeners.onEntitiesRemoved().accept(List.of(entityId)));
                log.add(name + " killed zombie " + entityId);
            }
        }

        @Override
        public void sendChatCommand(String command) {
            log.add(name + " /" + command);
        }

        @Override
        public void sendChat(String message) {
            log.add(name + ": " + message);
        }

        @Override
        public void useBlock(int x, int y, int z) {
            if (!reaches(x, y, z)) {
                log.add(name + " REFUSED: use of " + x + "," + y + "," + z + " from " + position);
                return;
            }
            int state = block(x, y, z);
            if (state == CRAFTING_TABLE || state == FURNACE) {
                open = key(x, y, z);
                openId = nextId++;
                tableGrid = new int[10][];
                listeners.onContainer().accept(new ContainerUpdate.Opened(openId, state == FURNACE ? FURNACE_MENU : CRAFTING_MENU));
                sync();
                return;
            }
            Optional<ItemClass> held = heldItem().flatMap(ItemClassRegistry::classOf);
            if (held.equals(Optional.of(ItemClass.CRAFTING_TABLE)) || held.equals(Optional.of(ItemClass.FURNACE))) {
                int px = x, py = y + 1, pz = z;
                if (block(px, py, pz) != AIR || bodyIn(px, py, pz)) {
                    return; // the server won't place into a block or a body
                }
                take(slots, 36 + this.held);
                boolean furnace = held.get() == ItemClass.FURNACE;
                set(px, py, pz, furnace ? FURNACE : CRAFTING_TABLE);
                if (furnace) {
                    furnaces.put(key(px, py, pz), new int[3][]);
                }
                log.add(name + " placed a " + held.get() + " at " + px + "," + py + "," + pz);
                sync();
                return;
            }
            throw new UnsupportedOperationException("simulated: using " + heldItem() + " on block " + state + " at " + x + "," + y + "," + z);
        }

        private boolean bodyIn(int x, int y, int z) {
            return players.values().stream().anyMatch(p -> (int) Math.floor(p.position.x()) == x && (int) Math.floor(p.position.z()) == z
                    && Math.abs(p.position.y() - y) < 2);
        }

        private boolean furnaceOpen() {
            return open != null && furnaces.containsKey(open);
        }

        private int containerSize() {
            return furnaceOpen() ? 3 : 10;
        }

        private int[][] holderFor(int window, int slot) {
            if (window == 0) {
                return slot >= 1 && slot <= 4 ? grid2 : slots;
            }
            return slot < containerSize() ? furnaceOpen() ? furnaces.get(open) : tableGrid : slots;
        }

        private int indexFor(int window, int slot) {
            return window == 0 || slot < containerSize() ? slot : slot - containerSize() + 9;
        }

        /** Into the inventory: onto a stack of the same item with room, then into the first empty slot. */
        private void store(int[] item) {
            int left = item[1];
            for (int slot = 9; slot < 45 && left > 0; slot++) {
                if (slots[slot] != null && slots[slot][0] == item[0] && slots[slot][1] < 64) {
                    int fits = Math.min(left, 64 - slots[slot][1]);
                    slots[slot] = new int[]{item[0], slots[slot][1] + fits};
                    left -= fits;
                }
            }
            for (int slot = 9; slot < 45 && left > 0; slot++) {
                if (slots[slot] == null) {
                    slots[slot] = new int[]{item[0], left};
                    left = 0;
                }
            }
            if (left > 0) {
                throw new AssertionError(name + "'s inventory is full");
            }
        }

        /** The inventory to the soldier: its own window, or through the open container's. */
        void sync() {
            if (open == null) {
                sendInventory();
                return;
            }
            int size = containerSize();
            int[][] own = furnaceOpen() ? furnaces.get(open) : tableGrid;
            List<Optional<ItemSlot>> window = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                window.add(stack(own[i]));
            }
            for (int slot = 9; slot < 45; slot++) {
                window.add(item(slot));
            }
            listeners.onContainer().accept(new ContainerUpdate.Contents(openId, 0, window, true));
        }

        private Optional<ItemSlot> stack(int[] s) {
            return s == null ? Optional.empty() : Optional.of(new ItemSlot(s[0], s[1], 0, false, Map.of()));
        }

        @Override
        public void useBlockFace(int x, int y, int z, BlockFace face) {
            Optional<Integer> held = heldItem();
            Optional<Integer> block = held.flatMap(SimServer::placesAs);
            if (block.isEmpty()) {
                throw new UnsupportedOperationException("simulated: using " + held + " on block " + block(x, y, z) + " at " + x + "," + y + "," + z);
            }
            int px = x + face.dx, py = y + face.dy, pz = z + face.dz;
            if (!reaches(x, y, z)) {
                log.add(name + " REFUSED: place at " + px + "," + py + "," + pz + " from " + position);
                sendSlot(36 + this.held);
                return;
            }
            if (block(x, y, z) == AIR || block(px, py, pz) != AIR && block(px, py, pz) != WATER) {
                return; // nothing to place against, or somewhere taken: the server ignores it
            }
            int[] s = slots[36 + this.held];
            slots[36 + this.held] = s[1] == 1 ? null : new int[]{s[0], s[1] - 1};
            sendSlot(36 + this.held);
            set(px, py, pz, block.get());
            log.add(name + " placed " + block.get() + " at " + px + "," + py + "," + pz);
        }

        @Override
        public void useItem(float yaw, float pitch) {
            throw new UnsupportedOperationException("simulated: using " + heldItem());
        }

        @Override
        public void raiseShield(float yaw, float pitch) {
        }

        @Override
        public void releaseUseItem() {
        }

        @Override
        public void setSprinting(boolean sprinting) {
        }

        @Override
        public void quickMove(int window, int stateId, int slot) {
            if (window != 0 && window != openId) {
                return; // the server ignores a click for a window that isn't open
            }
            if (window != 0 && furnaceOpen()) {
                int[][] f = furnaces.get(open);
                if (slot < 3 && f[slot] != null) {
                    store(f[slot]);
                    f[slot] = null;
                } else if (slot >= 3) {
                    throw new UnsupportedOperationException("simulated: shift-click into a furnace");
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
                    store(new int[]{r.item().map(SimServer::itemNamed).orElseGet(() -> itemOf(r.makes())), r.yield()});
                    log.add(name + " crafted " + r.name());
                }
            } else if (window == 0 && slot >= 5 && slot <= 8 || window == 0 && slot == 45) {
                throw new UnsupportedOperationException("simulated: shift-click out of slot " + slot);
            } else if (window == 0) {
                // Armour shift-clicked on: into the slot it fits, if that's empty.
                int[] s = slots[slot];
                java.util.OptionalInt fits = s == null ? java.util.OptionalInt.empty() : ItemClassRegistry.armourSlot(s[0]);
                if (fits.isEmpty()) {
                    throw new UnsupportedOperationException("simulated: shift-click of " + (s == null ? "nothing" : s[0]) + " in slot " + slot);
                }
                if (slots[fits.getAsInt()] == null) {
                    slots[fits.getAsInt()] = s;
                    slots[slot] = null;
                }
            } else {
                throw new UnsupportedOperationException("simulated: shift-click slot " + slot + " of a table");
            }
            sync();
        }

        @Override
        public void click(int window, int stateId, int slot, boolean right) {
            if (window != 0 && window != openId) {
                return;
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

        @Override
        public void swapWithHotbar(int containerId, int stateId, int slot, int hotbarSlot) {
            if (containerId != 0) {
                throw new UnsupportedOperationException("simulated: swap in container " + containerId);
            }
            int other = hotbarSlot == 40 ? 45 : 36 + hotbarSlot;
            int[] a = slots[slot];
            slots[slot] = slots[other];
            slots[other] = a;
            sync();
        }

        @Override
        public void throwOne(int containerId, int stateId, int slot) {
            if (containerId != 0 || slots[slot] == null) {
                throw new UnsupportedOperationException("simulated: throwing from slot " + slot + " of " + containerId);
            }
            int item = slots[slot][0];
            take(slots, slot);
            sync();
            // Thrown toward a squadmate, it's picked up by the nearest one within a throw's reach.
            players.values().stream().filter(p -> p != this)
                    .filter(p -> Math.hypot(p.position.x() - position.x(), p.position.z() - position.z()) <= THROW_REACH)
                    .min(java.util.Comparator.comparingDouble(p -> Math.hypot(p.position.x() - position.x(), p.position.z() - position.z())))
                    .ifPresentOrElse(p -> {
                        p.give(item);
                        log.add(name + " threw " + item + " to " + p.name);
                    }, () -> log.add(name + " threw " + item + " on the ground"));
        }

        @Override
        public void startDigging(int x, int y, int z) {
        }

        /** Whether the cell's centre is within the server's reach of the eyes: it refuses past that. */
        private boolean reaches(int x, int y, int z) {
            double dx = x + 0.5 - position.x(), dy = y + 0.5 - (position.y() + 1.62), dz = z + 0.5 - position.z();
            return dx * dx + dy * dy + dz * dz <= REACH * REACH;
        }

        @Override
        public void finishDigging(int x, int y, int z) {
            if (!reaches(x, y, z)) {
                log.add(name + " REFUSED: dig at " + x + "," + y + "," + z + " from " + position);
                return;
            }
            int state = block(x, y, z);
            if (state == AIR || state == WATER) {
                return;
            }
            if (state == BEDROCK) {
                throw new AssertionError(name + " dug at bedrock at " + x + "," + y + "," + z);
            }
            drop(state, heldItem()).ifPresent(this::give);
            set(x, y, z, AIR);
            log.add(name + " dug " + state + " at " + x + "," + y + "," + z);
        }

        @Override
        public void closeContainer(int window) {
            int[][] grid = window == 0 ? grid2 : window == openId && !furnaceOpen() ? tableGrid : null;
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
                listeners.onContainer().accept(new ContainerUpdate.Closed(window));
            }
            sync();
        }

        @Override
        public void selectHotbarSlot(int hotbarSlot) {
            held = hotbarSlot;
            listeners.onInventory().accept(new InventoryUpdate.HeldSlot(held));
        }

        @Override
        public boolean isOpen() {
            return connected;
        }

        @Override
        public void close() {
            connected = false;
        }
    }
}
