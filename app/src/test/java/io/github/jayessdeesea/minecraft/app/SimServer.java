package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.protocol.BlockChange;
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
 * inventory slots swapped, the time of day. What it doesn't model throws, naming the action, so a
 * scenario that needs more says so instead of quietly doing nothing.
 *
 * <p>Block-state and item IDs are protocol 777's (the pinned server's reports).
 */
final class SimServer {

    static final int AIR = 0, STONE = 1, GRASS = 9, DIRT = 10, COBBLESTONE = 14, BEDROCK = 88, WATER = 89,
            IRON_ORE = 134, COAL_ORE = 136, TORCH = 4926;
    static final int ITEM_COBBLESTONE = 62, ITEM_DIRT = 55, ITEM_RAW_IRON = 1017, ITEM_COAL = 1010,
            ITEM_WOODEN_PICKAXE = 1027, ITEM_STONE_PICKAXE = 1037, ITEM_WOODEN_SWORD = 1025, ITEM_BREAD = 1067,
            ITEM_TORCH = 395;
    private static final int MIN_Y = -64;
    private static final int SECTIONS = 24;
    /** Chunks sent round a soldier on joining: radius 3, 7x7. */
    private static final int VIEW = 3;
    /** How far from the eyes a block may be broken or clicked: the server's interaction range, with slack. */
    private static final double REACH = 6;

    private final SimClock clock;
    private final Terrain ground;
    private final Map<Long, Integer> placed = new HashMap<>();
    private final Map<String, Player> players = new LinkedHashMap<>();
    /** What happened, in order, for a scenario to read. */
    final List<String> log = new ArrayList<>();
    private long dayTicks;

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

    private void tick() {
        dayTicks++;
        if (dayTicks % 20 == 0) {
            players.values().forEach(Player::sendTime);
        }
    }

    /** The joins: each soldier spawns at {@code spawn} carrying {@code kit} ([slot, item, count] each). */
    SoldierRunner.Connector connector(PlayerPosition spawn, List<int[]> kit) {
        return (name, listeners) -> {
            Player player = new Player(name, listeners, spawn);
            players.put(name, player);
            kit.forEach(k -> player.slots[k[0]] = new int[]{k[1], k[2]});
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

    private final class Player implements PlaySession {
        final String name;
        final PlaySessionListeners listeners;
        PlayerPosition position;
        final int[][] slots = new int[46][];
        int held;
        boolean open = true;

        Player(String name, PlaySessionListeners listeners, PlayerPosition spawn) {
            this.name = name;
            this.listeners = listeners;
            this.position = spawn;
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
            throw new UnsupportedOperationException("simulated: respawn");
        }

        @Override
        public void attack(int entityId) {
            throw new UnsupportedOperationException("simulated: attack " + entityId);
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
            throw new UnsupportedOperationException("simulated: using block " + block(x, y, z) + " at " + x + "," + y + "," + z);
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
        public void quickMove(int containerId, int stateId, int slot) {
            throw new UnsupportedOperationException("simulated: shift-click slot " + slot + " of " + containerId);
        }

        @Override
        public void click(int containerId, int stateId, int slot, boolean rightButton) {
            throw new UnsupportedOperationException("simulated: click slot " + slot + " of " + containerId);
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
            sendSlot(slot);
            sendSlot(other);
        }

        @Override
        public void throwOne(int containerId, int stateId, int slot) {
            throw new UnsupportedOperationException("simulated: throwing from slot " + slot);
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
        public void closeContainer(int containerId) {
        }

        @Override
        public void selectHotbarSlot(int hotbarSlot) {
            held = hotbarSlot;
            listeners.onInventory().accept(new InventoryUpdate.HeldSlot(held));
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
