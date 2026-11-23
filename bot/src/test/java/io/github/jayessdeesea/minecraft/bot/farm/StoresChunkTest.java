package io.github.jayessdeesea.minecraft.bot.farm;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The supply point on a chunk: its layout, the row a thing is kept in, and its stage from the world (operator, 2026-11-22). */
class StoresChunkTest {

    private static final int GROUND = ChunkFarm.SEA_LEVEL;

    private final Set<BlockPos> cobblestone = new HashSet<>();
    private final Set<BlockPos> blocks = new HashSet<>();
    private final Set<BlockPos> gates = new HashSet<>();
    private final Set<BlockPos> torches = new HashSet<>();
    private final Set<BlockPos> chests = new HashSet<>();

    /** Grass at sea level everywhere, with what's been laid and put on it. */
    private final WorldView world = new WorldView() {
        @Override
        public boolean isSolid(int x, int y, int z) {
            BlockPos at = new BlockPos(x, y, z);
            return y <= GROUND || blocks.contains(at) || chests.contains(at) || gates.contains(at);
        }

        @Override
        public boolean isCobblestone(int x, int y, int z) {
            return cobblestone.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isGate(int x, int y, int z) {
            return gates.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isTorch(int x, int y, int z) {
            return torches.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean isChest(int x, int y, int z) {
            return chests.contains(new BlockPos(x, y, z));
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

    /**
     * Four rows of twelve chest blocks - 24 double chests - inside the fence, a cross-aisle at local x=8 and two aisles
     * between rows; nine torches, a table and four furnaces, none of them in a chest's cell or on the fence's line.
     */
    @Test
    void fourRowsOfSixDoubleChestsWithAislesNineTorchesAndAWorkshop() {
        StoresChunk stores = new StoresChunk(1, 4, GROUND);
        Set<BlockPos> all = new HashSet<>();
        for (StoresChunk.Row row : StoresChunk.Row.values()) {
            List<BlockPos> cells = stores.chests(row);
            assertEquals(12, cells.size(), row + ": six doubles");
            assertTrue(cells.stream().allMatch(c -> c.y() == GROUND + 1 && c.z() == 64 + row.z() && c.x() > 16 && c.x() < 32 && c.x() != 16 + 8),
                    row + " on the ground, inside the fence, the cross-aisle open: " + cells);
            all.addAll(cells);
        }
        assertEquals(48, all.size());
        List<Integer> rows = List.of(StoresChunk.Row.values()).stream().map(StoresChunk.Row::z).toList();
        for (int i = 1; i < rows.size(); i++) {
            assertEquals(3, rows.get(i) - rows.get(i - 1), "two aisles between rows");
        }
        List<BlockPos> fixtures = new java.util.ArrayList<>(stores.torches());
        fixtures.add(stores.table());
        fixtures.addAll(stores.furnaces());
        assertEquals(9 + 1 + 4, new HashSet<>(fixtures).size());
        assertTrue(fixtures.stream().noneMatch(all::contains), "no fixture in a chest's cell");
        Set<BlockPos> line = new HashSet<>(stores.grid().fence());
        line.addAll(stores.grid().gates());
        assertTrue(fixtures.stream().noneMatch(line::contains) && all.stream().noneMatch(line::contains), "nothing on the fence's line");
        assertEquals(Optional.of(StoresChunk.Row.FOOD), stores.rowAt(stores.chests(StoresChunk.Row.FOOD).getFirst()));
        assertEquals(Optional.empty(), stores.rowAt(stores.table()));
    }

    /** A class of supply to a row; what has no row of its own - a diamond, a bucket, string - is the kit row's (operator, 2026-11-22). */
    @Test
    void eachThingHasItsRow() {
        assertEquals(StoresChunk.Row.FOOD, StoresChunk.Row.of(ItemClass.FOOD));
        assertEquals(StoresChunk.Row.BUILDING, StoresChunk.Row.of(ItemClass.COBBLESTONE));
        assertEquals(StoresChunk.Row.BUILDING, StoresChunk.Row.of(ItemClass.LOG));
        assertEquals(StoresChunk.Row.FUEL_AND_ARROWS, StoresChunk.Row.of(ItemClass.TORCH));
        assertEquals(StoresChunk.Row.KIT, StoresChunk.Row.of(ItemClass.IRON_INGOT));
        assertEquals(StoresChunk.Row.KIT, StoresChunk.Row.of(ItemClass.DIAMOND));
        assertEquals(StoresChunk.Row.KIT, StoresChunk.Row.of(ItemClass.BUCKET));
        assertEquals(StoresChunk.Row.KIT, StoresChunk.Row.of(ItemClass.STRING));
    }

    /** Its stage is the first work left undone: ground, fence and lights, workshop and first rows, the rest - stocked from the fourth. */
    @Test
    void itsStageIsTheFirstWorkLeftUndone() {
        StoresChunk stores = new StoresChunk(1, 4, GROUND);
        assertEquals(StoresChunk.Stage.GROUND, stores.stage(world), "grass: its ground to lay");

        cobblestone.addAll(stores.ground());
        assertEquals(StoresChunk.Stage.FENCED, stores.stage(world));

        blocks.addAll(stores.grid().fence());
        gates.addAll(stores.grid().gates());
        torches.addAll(stores.torches());
        assertEquals(StoresChunk.Stage.STOCKED, stores.stage(world), "fenced and lit: its workshop and first rows next");
        assertTrue(!stores.stocked(world));

        blocks.add(stores.table());
        blocks.addAll(stores.furnaces());
        StoresChunk.FIRST_ROWS.forEach(row -> chests.addAll(stores.chests(row)));
        assertEquals(StoresChunk.Stage.GROWN, stores.stage(world), "the first rows up: stocked, the rest to grow");
        assertTrue(stores.stocked(world), "what its mission is done at");

        for (StoresChunk.Row row : StoresChunk.Row.values()) {
            chests.addAll(stores.chests(row));
        }
        assertEquals(StoresChunk.Stage.COMPLETE, stores.stage(world));
    }
}
