package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.supply.Staircase;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A mineshaft's head laid out on one chunk of the server's grid (operator, 2026-11-22, and its pattern 2026-11-24): the
 * chunk farms' fence and gates round it and a torch inside each corner; at its middle the shaft's mouth, four by four,
 * ringed with a fence one out from it - a torch on each corner of the ring, one gate where the stair's first step
 * leaves from, so nothing walks up or falls in; two rows of double chests facing the mouth, a kind to each
 * ({@link Bank}); and a table and two furnaces by its north fence, where ore is smelted as it comes up.
 *
 * <p>Pure: laid out from its chunk and the level of its ground.
 */
public record MineHead(int chunkX, int chunkZ, int level) {

    /** The corner torches' local x and z: inside each corner. */
    static final int[] TORCH = {2, 13};
    /** The chest rows' first local x, and their local z: north of the mouth, and south of it. */
    static final int ROW_FROM = 4, NORTH_Z = 3, SOUTH_Z = 12;
    /** The ring round the mouth, by local x and z: one out from the four by four well. */
    static final int RING_FROM = 5, RING_TO = 10;
    /** The workshop by the north fence: its table's local x, its furnaces', and their local z. */
    static final int TABLE_X = 7, SHOP_Z = 1;
    static final int[] FURNACE_X = {8, 9};

    /** A double chest of the head, and the kinds kept in it: the north row west to east, then the south row. */
    public enum Bank {
        FUEL(List.of(ItemClass.COAL, ItemClass.TORCH)),
        RAW_ORE(List.of(ItemClass.RAW_IRON, ItemClass.RAW_COPPER)),
        INGOTS(List.of(ItemClass.IRON_INGOT, ItemClass.COPPER_INGOT, ItemClass.MINERAL, ItemClass.DIAMOND)),
        TOOLS(List.of(ItemClass.PICKAXE, ItemClass.SHOVEL)),
        SPOIL(List.of(ItemClass.COBBLESTONE, ItemClass.DIRT)),
        MORE_SPOIL(List.of()),
        FOOD(List.of(ItemClass.FOOD)),
        /** What has no chest of its own. */
        KIT(List.of());

        private final List<ItemClass> holds;

        Bank(List<ItemClass> holds) {
            this.holds = holds;
        }

        /** The chest a thing of {@code kind} is kept in: its own, else the kit's - nothing is without one. */
        public static Bank of(ItemClass kind) {
            for (Bank bank : values()) {
                if (bank.holds.contains(kind)) {
                    return bank;
                }
            }
            return KIT;
        }
    }

    private int x0() {
        return chunkX * ChunkFarm.SIDE;
    }

    private int z0() {
        return chunkZ * ChunkFarm.SIDE;
    }

    /** Where its shaft goes down: the chunk's middle. */
    public BlockPos shaft() {
        return new BlockPos(x0() + 8, level + 1, z0() + 8);
    }

    /** {@code bank}'s double chest: its two cells, side by side in its row. */
    public List<BlockPos> chests(Bank bank) {
        int x = x0() + ROW_FROM + 2 * (bank.ordinal() % 4), z = z0() + (bank.ordinal() < 4 ? NORTH_Z : SOUTH_Z);
        return List.of(new BlockPos(x, level + 1, z), new BlockPos(x + 1, level + 1, z));
    }

    /** Its head's chests, every bank's: the north row, then the south. */
    public List<BlockPos> chests() {
        List<BlockPos> cells = new ArrayList<>();
        for (Bank bank : Bank.values()) {
            cells.addAll(chests(bank));
        }
        return cells;
    }

    /** The gate in its mouth's ring: beside the well's head cell, where the stair's first step leaves from. */
    public BlockPos mouthGate() {
        BlockPos head = Staircase.wellCell(chunkX, chunkZ, level + 1);
        int lx = head.x() - x0(), lz = head.z() - z0();
        if (lx == RING_FROM + 1) {
            return new BlockPos(x0() + RING_FROM, level + 1, head.z());
        }
        if (lx == RING_TO - 1) {
            return new BlockPos(x0() + RING_TO, level + 1, head.z());
        }
        return new BlockPos(head.x(), level + 1, z0() + (lz == RING_FROM + 1 ? RING_FROM : RING_TO));
    }

    /** The posts of its mouth's ring: its square, one out from the well, but for its gate. */
    public List<BlockPos> ring() {
        BlockPos gate = mouthGate();
        List<BlockPos> posts = new ArrayList<>();
        for (int x = RING_FROM; x <= RING_TO; x++) {
            for (int z = RING_FROM; z <= RING_TO; z++) {
                BlockPos c = new BlockPos(x0() + x, level + 1, z0() + z);
                if ((x == RING_FROM || x == RING_TO || z == RING_FROM || z == RING_TO) && !c.equals(gate)) {
                    posts.add(c);
                }
            }
        }
        return posts;
    }

    /** Its torches: on the ground inside each corner, and on the top of each corner post of its mouth's ring. */
    public List<BlockPos> torches() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : TORCH) {
            for (int z : TORCH) {
                cells.add(new BlockPos(x0() + x, level + 1, z0() + z));
            }
        }
        for (int x : new int[]{RING_FROM, RING_TO}) {
            for (int z : new int[]{RING_FROM, RING_TO}) {
                cells.add(new BlockPos(x0() + x, level + 2, z0() + z));
            }
        }
        return cells;
    }

    /** Its workshop's table. */
    public BlockPos table() {
        return new BlockPos(x0() + TABLE_X, level + 1, z0() + SHOP_Z);
    }

    /** Its workshop's two furnaces, beside the table. */
    public List<BlockPos> furnaces() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : FURNACE_X) {
            cells.add(new BlockPos(x0() + x, level + 1, z0() + SHOP_Z));
        }
        return cells;
    }

    /** Its fence and gates: the chunk farms', on the same lines. */
    public ChunkFarm grid() {
        return new ChunkFarm(chunkX, chunkZ, level);
    }

    /**
     * What's put up at its head, and where, in the order it's built: its fences - the chunk's and its mouth's ring - and
     * their gates, its torches (a ring post's once the post stands), its workshop, its chests.
     */
    public Map<ItemClass, List<BlockPos>> fixtures() {
        Map<ItemClass, List<BlockPos>> layout = new LinkedHashMap<>();
        List<BlockPos> fence = new ArrayList<>(grid().fence());
        fence.addAll(ring());
        layout.put(ItemClass.FENCE, fence);
        List<BlockPos> gates = new ArrayList<>(grid().gates());
        gates.add(mouthGate());
        layout.put(ItemClass.FENCE_GATE, gates);
        layout.put(ItemClass.TORCH, torches());
        layout.put(ItemClass.CRAFTING_TABLE, List.of(table()));
        layout.put(ItemClass.FURNACE, furnaces());
        layout.put(ItemClass.CHEST, chests());
        return layout;
    }
}
