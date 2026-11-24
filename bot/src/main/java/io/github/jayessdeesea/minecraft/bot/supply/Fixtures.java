package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier putting up a template's fixtures (operator, 2026-11-22: the supply point): a layout says which item goes
 * in which cells - fence posts, gates, torches, a crafting table, furnaces, chests - in the order they're built, and the
 * soldier puts what it carries of each into the cells still open, a kind at a time. What it's short of it says
 * ({@link #wants}), for its armourer to make. Where things go is the layout's; the soldier only places them, as it
 * would any block of a {@code Place} order ({@link Builder}).
 */
public final class Fixtures {

    private static final Logger LOG = LogManager.getLogger(Fixtures.class);

    /** What makes each kind of fixture. */
    static final Map<ItemClass, Recipe> MAKES = Map.of(ItemClass.FENCE, Recipe.FENCE, ItemClass.FENCE_GATE, Recipe.FENCE_GATE,
            ItemClass.TORCH, Recipe.TORCHES, ItemClass.CRAFTING_TABLE, Recipe.CRAFTING_TABLE, ItemClass.FURNACE, Recipe.FURNACE,
            ItemClass.CHEST, Recipe.CHEST);

    private final String name;
    private final Inventory inventory;
    private final Map<ItemClass, Builder> builders = new EnumMap<>(ItemClass.class);
    /** The kind it's putting up, or null. */
    private ItemClass placing;

    public Fixtures(String name, PlaySession session, Inventory inventory) {
        this.name = name;
        this.inventory = inventory;
        MAKES.keySet().forEach(item -> builders.put(item, new Builder(name, session, inventory, List.of(item))));
        // Nothing is put up from a cell of the layout: a post put in under a soldier standing on the line fences it in. And a
        // gate faces the way it's put from: across its fence line - the line the posts beside it in its layout run along.
        builders.forEach((item, b) -> b.putFrom((cell, stand) -> !laidCells.contains(BlockPos.containing(stand.x(), stand.y(), stand.z()))
                && (item != ItemClass.FENCE_GATE || across(laid, cell, stand.x(), stand.z()))));
    }

    /** The layout it was last given: where its fence runs says which way a gate in it faces. */
    private volatile Map<ItemClass, List<BlockPos>> laid = Map.of();
    /** Every cell of it. */
    private volatile Set<BlockPos> laidCells = Set.of();

    /**
     * Whether a gate in {@code gate} is put from ({@code x}, {@code z}) facing across its line in {@code layout}: with a post
     * of the layout north or south of it its line runs along z, and the stand is in the gate's own z; else its own x.
     */
    static boolean across(Map<ItemClass, List<BlockPos>> layout, BlockPos gate, double x, double z) {
        List<BlockPos> fence = layout.getOrDefault(ItemClass.FENCE, List.of());
        boolean alongZ = fence.contains(gate.offset(0, 0, 1)) || fence.contains(gate.offset(0, 0, -1));
        return alongZ ? (int) Math.floor(z) == gate.z() : (int) Math.floor(x) == gate.x();
    }

    /** Whether {@code cell} is still open for a fixture: nothing in it, and ground under it. */
    static boolean open(WorldView world, BlockPos cell) {
        return Farmer.openFor(world, cell) && !world.isChest(cell.x(), cell.y(), cell.z());
    }

    /** The cells of {@code layout} still open, by kind, in its order - a kind with none left out. */
    public static Map<ItemClass, List<BlockPos>> left(WorldView world, Map<ItemClass, List<BlockPos>> layout) {
        Map<ItemClass, List<BlockPos>> left = new LinkedHashMap<>();
        layout.forEach((item, cells) -> {
            List<BlockPos> open = cells.stream().filter(c -> open(world, c)).toList();
            if (!open.isEmpty()) {
                left.put(item, open);
            }
        });
        return left;
    }

    /**
     * What a soldier putting up {@code layout} needs made, carrying what {@code inventory} holds: the first kind still
     * wanted that it carries none of - one at a time, so the logs go to what's built first. Torches only from coal it
     * carries: nobody sets up a smelter for charcoal on the way.
     */
    public static Set<Recipe> wants(WorldView world, Map<ItemClass, List<BlockPos>> layout, Inventory inventory) {
        Set<Recipe> wants = new LinkedHashSet<>();
        for (ItemClass item : left(world, layout).keySet()) {
            if (inventory.count(item) > 0) {
                return wants; // something to put up first
            }
            if (item != ItemClass.TORCH || inventory.count(ItemClass.COAL) > 0) {
                wants.add(MAKES.get(item));
                return wants;
            }
        }
        return wants;
    }

    /** Whether it's putting a kind up now. */
    public synchronized boolean active() {
        return placing != null;
    }

    /** Where it stands for the cell under way. */
    public synchronized Optional<Position> standAt() {
        return placing == null ? Optional.empty() : builders.get(placing).standAt();
    }

    /** Where it looks for the cell under way. */
    public synchronized Optional<Position> lookAt() {
        return placing == null ? Optional.empty() : builders.get(placing).lookAt();
    }

    /** Each tick: puts up what it carries of {@code layout}'s first kind still open; started only when {@code free}. */
    public synchronized void tick(Position soldier, WorldView world, Map<ItemClass, List<BlockPos>> layout, boolean free) {
        if (!layout.equals(laid)) {
            laid = layout;
            laidCells = layout.values().stream().flatMap(List::stream).collect(java.util.stream.Collectors.toSet());
        }
        Map<ItemClass, List<BlockPos>> left = left(world, layout);
        if (placing != null && (!left.containsKey(placing) || inventory.count(placing) == 0)) {
            stop();
        }
        if (placing == null) {
            if (!free) {
                return;
            }
            placing = left.entrySet().stream().filter(e -> inventory.count(e.getKey()) > 0
                            && e.getValue().stream().anyMatch(c -> !builders.get(e.getKey()).skipped().contains(c)))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (placing == null) {
                return;
            }
            LOG.info("{} puts up the template's {}: {} to go", name, placing, left.get(placing).size());
        }
        Builder builder = builders.get(placing);
        List<BlockPos> cells = left.get(placing).stream().filter(c -> !builder.skipped().contains(c)).toList();
        if (cells.isEmpty()) {
            stop();
            return;
        }
        if (builder.tick(soldier, world, cells) instanceof Builder.Progress.Failed(String why)) {
            // A cell that won't take one is the builder's to leave out: the rest go up, and the next kind after.
            LOG.info("{} leaves the template's {} for now - {}", name, placing, why);
            placing = null;
        }
    }

    /** Stops the kind under way; the next tick starts afresh. */
    public synchronized void stop() {
        if (placing != null) {
            builders.get(placing).stop();
            placing = null;
        }
    }
}
