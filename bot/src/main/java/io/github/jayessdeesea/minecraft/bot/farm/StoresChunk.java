package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The supply point laid out on one chunk of the server's grid (operator, 2026-11-21 and 2026-11-22; FM 4-0, ATP 4-90:
 * where supplies are held and issued): its ground cobblestone at its level, the chunk farms' fence and gates round it,
 * and on the ground four rows of double chests along x, a class of supply to a row - six chest blocks to a half-row,
 * three double chests, a cross-aisle at local x=8, two aisles between rows so each chest is reached from both sides,
 * 24 double chests in all. Nine ground torches, and a workshop along local z=14: a crafting table and four furnaces.
 * Its stages: ground, fenced and lit, stocked - the workshop and the first two rows up, food and Class IV - and grown,
 * every row.
 *
 * <p>Pure: laid out from its chunk and level, its stage read from a {@link WorldView}.
 */
public record StoresChunk(int chunkX, int chunkZ, int level) {

    /** A row of chests: the supply it holds, and its local z. */
    public enum Row {
        /** Class I: food. */
        FOOD(3, List.of(ItemClass.FOOD, ItemClass.WHEAT)),
        /** Class IV: what's built with. */
        BUILDING(6, List.of(ItemClass.COBBLESTONE, ItemClass.DIRT, ItemClass.LOG, ItemClass.PLANKS)),
        /** Classes II and IX: tools, armour, ore and ingots - and what has no row of its own. */
        KIT(9, List.of()),
        /** Classes III and V: coal, torches, arrows. */
        FUEL_AND_ARROWS(12, List.of(ItemClass.COAL, ItemClass.TORCH, ItemClass.ARROWS));

        private final int z;
        private final List<ItemClass> holds;

        Row(int z, List<ItemClass> holds) {
            this.z = z;
            this.holds = holds;
        }

        /** Its local z. */
        public int z() {
            return z;
        }

        /** The row a thing of {@code kind} is kept in: its class's, else the kit row - nothing is without a row. */
        public static Row of(ItemClass kind) {
            for (Row row : values()) {
                if (row.holds.contains(kind)) {
                    return row;
                }
            }
            return KIT;
        }
    }

    /** The chest blocks' local x in a row: two halves of six, the cross-aisle at 8 between. */
    static final int[] CHEST_X = {2, 3, 4, 5, 6, 7, 9, 10, 11, 12, 13, 14};
    /** The ground torches' local x and z. */
    static final int[] TORCH_X = {2, 7, 12};
    static final int[] TORCH_Z = {2, 7, 11};
    /** The workshop's line, its table's local x, and its furnaces'. */
    static final int WORKSHOP_Z = 14;
    static final int TABLE_X = 3;
    static final int[] FURNACE_X = {4, 5, 6, 7};
    /** The rows that make it stocked: up first (operator, 2026-11-22: food and Class IV; a later row a follow-on order). */
    public static final List<Row> FIRST_ROWS = List.of(Row.FOOD, Row.BUILDING);

    /** The work it's at, in the order it's built: its ground, its fence and lights, its workshop and first rows, the rest of its rows - or none left. */
    public enum Stage { GROUND, FENCED, STOCKED, GROWN, COMPLETE }

    private int x0() {
        return chunkX * ChunkFarm.SIDE;
    }

    private int z0() {
        return chunkZ * ChunkFarm.SIDE;
    }

    /** A row's twelve chest cells, on the ground over its level, west to east: each pair a double chest. */
    public List<BlockPos> chests(Row row) {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : CHEST_X) {
            cells.add(new BlockPos(x0() + x, level + 1, z0() + row.z()));
        }
        return cells;
    }

    /** The row a chest at {@code cell} is of, if it's one of its chests' cells. */
    public Optional<Row> rowAt(BlockPos cell) {
        for (Row row : Row.values()) {
            if (chests(row).contains(cell)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /** The nine ground torches. */
    public List<BlockPos> torches() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : TORCH_X) {
            for (int z : TORCH_Z) {
                cells.add(new BlockPos(x0() + x, level + 1, z0() + z));
            }
        }
        return cells;
    }

    /** Where its crafting table stands. */
    public BlockPos table() {
        return new BlockPos(x0() + TABLE_X, level + 1, z0() + WORKSHOP_Z);
    }

    /** Its bank of furnaces, beside the table. */
    public List<BlockPos> furnaces() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x : FURNACE_X) {
            cells.add(new BlockPos(x0() + x, level + 1, z0() + WORKSHOP_Z));
        }
        return cells;
    }

    /** Its fence and gates: the chunk farms', on the same lines. */
    public ChunkFarm grid() {
        return new ChunkFarm(chunkX, chunkZ, level);
    }

    /** Its ground: the chunk's layer at its level, cobblestone when it's made. */
    public List<BlockPos> ground() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 0; x < ChunkFarm.SIDE; x++) {
            for (int z = 0; z < ChunkFarm.SIDE; z++) {
                cells.add(new BlockPos(x0() + x, level, z0() + z));
            }
        }
        return cells;
    }

    /**
     * What its mission puts up, and where, in the order it's built: the fence and its gates, the torches, the workshop's
     * table and furnaces, then its first rows' chests.
     */
    public java.util.Map<ItemClass, List<BlockPos>> fixtures() {
        java.util.Map<ItemClass, List<BlockPos>> layout = new java.util.LinkedHashMap<>();
        layout.put(ItemClass.FENCE, grid().fence());
        layout.put(ItemClass.FENCE_GATE, grid().gates());
        layout.put(ItemClass.TORCH, torches());
        layout.put(ItemClass.CRAFTING_TABLE, List.of(table()));
        layout.put(ItemClass.FURNACE, furnaces());
        List<BlockPos> chests = new ArrayList<>();
        FIRST_ROWS.forEach(row -> chests.addAll(chests(row)));
        layout.put(ItemClass.CHEST, chests);
        return layout;
    }

    private static boolean chestsUp(WorldView world, List<BlockPos> cells) {
        return cells.stream().allMatch(c -> world.isChest(c.x(), c.y(), c.z()));
    }

    /** The stage it's at, as the world stands: the first whose work is left undone. */
    public Stage stage(WorldView world) {
        if (!ground().stream().allMatch(c -> world.isCobblestone(c.x(), c.y(), c.z()))) {
            return Stage.GROUND;
        }
        // A gate's cell the ground stands in - a slope's foot on the chunk's line - is the ground's: it bars the way itself.
        if (!grid().fence().stream().allMatch(f -> world.solidHere(f.x(), f.y(), f.z()))
                || !grid().gates().stream().allMatch(g -> world.isGate(g.x(), g.y(), g.z()) || world.solidHere(g.x(), g.y(), g.z()))
                || !torches().stream().allMatch(t -> world.isTorch(t.x(), t.y(), t.z()))) {
            return Stage.FENCED;
        }
        if (!world.solidHere(table().x(), table().y(), table().z()) || !furnaces().stream().allMatch(f -> world.solidHere(f.x(), f.y(), f.z()))
                || !FIRST_ROWS.stream().allMatch(row -> chestsUp(world, chests(row)))) {
            return Stage.STOCKED;
        }
        return List.of(Row.values()).stream().allMatch(row -> chestsUp(world, chests(row))) ? Stage.COMPLETE : Stage.GROWN;
    }

    /** Whether it's stocked: fenced and lit, its workshop in, its first rows up - what its mission is done at. */
    public boolean stocked(WorldView world) {
        return stage(world).compareTo(Stage.GROWN) >= 0;
    }
}
