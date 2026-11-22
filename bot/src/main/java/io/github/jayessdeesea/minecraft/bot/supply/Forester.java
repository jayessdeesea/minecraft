package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.farm.TreeFarm;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.BlockFace;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One soldier planting its squad's {@link TreeFarm}: each tick it does the most pressing job left -
 * <ol>
 *   <li>plant a sapling in a plot cell with nothing growing, birch if it carries any, then spruce;</li>
 *   <li>with no sapling, break leaves for one - birch leaves first, then spruce - one in twenty drops
 *       its sapling.</li>
 * </ol>
 * The trees that grow are logs like any others: a squad sent for WOOD there chops them.
 *
 * <p>Planting is an expect script (docs/architecture.md § Behavior modeling): stand in the cell, the sapling in
 * hand when the server's resent inventory says so, use it on the ground's top, done when the
 * server's block update shows the sapling. Soldiers planting one farm share {@code claims}.
 */
public final class Forester {

    private static final Logger LOG = LogManager.getLogger(Forester.class);

    static final int WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    static final int COOLDOWN_TICKS = 200;
    /**
     * The trees a farm is planted with, first choice first (operator, 2026-11-14): birch, which grows
     * one straight trunk to a predictable height with no giant form, so a lumberjack's pass over the
     * grove is the same at every tree; then spruce, the pine.
     */
    static final List<String> PREFERRED = List.of("birch", "spruce");

    private final String name;
    private final PlaySession session;
    private final Inventory inventory;
    private final Set<BlockPos> claims;

    private Digger digger;
    private Script planting;
    private BlockPos claimed;
    private Position standAt;
    private Position soldier;
    private int cooldown;
    private String failure;
    /**
     * Grove cells a forester couldn't plant, passed over by every forester for a while, as the farm's are: live
     * (field618), one at (38, 66, 82) no soldier could stand in was walked at 22 times, each forester in turn.
     */
    private final Setbacks<BlockPos> shared;
    private long ticks;
    /** Puts up a tree chunk's fixtures once its trunks are in, as a farmer its chunk farm's: its torches, then its chest. */
    private final Map<ItemClass, Builder> fitters = new EnumMap<>(ItemClass.class);
    /** The kind of fixture it's putting up. */
    private ItemClass fitting;
    /** A tree chunk's fixtures, in the order they're put up: lit, then its chest for the spares (TreeChunk.Stage). */
    static final List<ItemClass> FIXTURES = List.of(ItemClass.TORCH, ItemClass.CHEST);

    public Forester(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this(name, session, inventory, claims, new Setbacks<>(Farmer.PASS_OVER_TICKS, 16L * Farmer.PASS_OVER_TICKS));
    }

    /** As above, the cells it couldn't plant {@code shared} with every forester. */
    public Forester(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims, Setbacks<BlockPos> shared) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
        this.shared = shared;
        FIXTURES.forEach(item -> fitters.put(item, new Builder(name, session, inventory, List.of(item))));
    }

    public synchronized boolean active() {
        return digger != null || planting != null || fitting != null;
    }

    /** Where the soldier should be standing for the job under way. */
    public synchronized Optional<Position> standAt() {
        return digger != null ? Optional.of(digger.standAt()) : fitting != null ? fitters.get(fitting).standAt() : Optional.ofNullable(standAt);
    }

    /** Why the last job failed, while the soldier waits before the next. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Whether every cell of {@code farm} has something growing in it. */
    public static boolean planted(WorldView world, TreeFarm farm) {
        return farm.plot().stream().allMatch(c -> TreeFarm.planted(world, c));
    }

    /** Each tick: plants {@code farm} while {@code free}; anything else stops the job under way. */
    public synchronized void tick(Position soldier, WorldView world, Optional<TreeFarm> farm, boolean free) {
        this.soldier = soldier;
        ticks++;
        if (cooldown > 0) {
            cooldown--;
        }
        if (!free || farm.isEmpty()) {
            stop();
            return;
        }
        if (digger != null) {
            switch (digger.tick(soldier, world)) {
                case Digger.Progress.Working _ -> {
                }
                case Digger.Progress.Done _ -> stopDigging();
                case Digger.Progress.Failed(String why) -> fail(why);
            }
            return;
        }
        if (planting != null) {
            switch (planting.tick()) {
                case Script.Status.Running _ -> {
                }
                case Script.Status.Done _, Script.Status.Interrupted _ -> endPlanting();
                case Script.Status.Failed(String step, String reason) -> fail(step + ": " + reason);
            }
            return;
        }
        if (fitting != null) {
            fit(world, farm.get());
            return;
        }
        if (cooldown > 0) {
            return;
        }
        next(world, farm.get());
    }

    /**
     * The cells of {@code farm}'s fixtures still to put {@code item} in - its torches or its chest - open, less those
     * {@code skipped}: none for any other item, or a grove with none.
     */
    static List<BlockPos> fixtures(WorldView world, TreeFarm farm, ItemClass item, Set<BlockPos> skipped) {
        List<BlockPos> cells = item == ItemClass.TORCH ? farm.torches() : item == ItemClass.CHEST ? farm.chest().stream().toList() : List.of();
        return cells.stream().filter(f -> Farmer.openFor(world, f) && !skipped.contains(f)).toList();
    }

    /** What a forester planting {@code farm} needs made for its fixtures: a chest; torches, from coal it carries. */
    public static Set<Recipe> wants(WorldView world, TreeFarm farm, Inventory inventory) {
        Set<Recipe> wants = new LinkedHashSet<>();
        if (inventory.count(ItemClass.TORCH) == 0 && inventory.count(ItemClass.COAL) > 0 && !fixtures(world, farm, ItemClass.TORCH, Set.of()).isEmpty()) {
            wants.add(Recipe.TORCHES);
        }
        if (inventory.count(ItemClass.CHEST) == 0 && !fixtures(world, farm, ItemClass.CHEST, Set.of()).isEmpty()) {
            wants.add(Recipe.CHEST);
        }
        return wants;
    }

    /** A tick of putting up the fixtures of the kind under way: over when none is left, or none is carried. */
    private void fit(WorldView world, TreeFarm farm) {
        Builder builder = fitters.get(fitting);
        List<BlockPos> left = fixtures(world, farm, fitting, builder.skipped());
        if (left.isEmpty() || inventory.count(fitting) == 0) {
            builder.stop();
            fitting = null;
            return;
        }
        if (builder.tick(soldier, world, left) instanceof Builder.Progress.Failed(String why)) {
            LOG.info("{} leaves the tree chunk's {} for now - {}", name, fitting, why);
            fitting = null;
        }
    }

    private void next(WorldView world, TreeFarm farm) {
        Optional<BlockPos> bare = farm.plot().stream().filter(c -> !claims.contains(c) && !TreeFarm.planted(world, c) && !shared.barred(c, ticks))
                .min(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())));
        if (bare.isEmpty()) {
            // Planted: its fixtures, with what it carries (operator, 2026-11-21: lit, then a chest for its saplings).
            FIXTURES.stream().filter(item -> inventory.count(item) > 0 && !fixtures(world, farm, item, fitters.get(item).skipped()).isEmpty())
                    .findFirst().ifPresent(item -> {
                        fitting = item;
                        fit(world, farm);
                    });
            return;
        }
        Optional<Integer> sapling = saplingSlot();
        if (sapling.isPresent()) {
            plant(world, bare.get(), sapling.get());
            return;
        }
        BlockPos at = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        Optional<Digger.Spot> leaves = Digger.findSpot(world, at, claims, Digger.Target.BIRCH_LEAVES)
                .or(() -> Digger.findSpot(world, at, claims, Digger.Target.SPRUCE_LEAVES))
                .or(() -> Digger.findSpot(world, at, claims, Digger.Target.LEAVES));
        if (leaves.isEmpty()) {
            fail("no sapling, and no leaves within " + Digger.SEARCH_RADIUS + " blocks to get one from");
            return;
        }
        digger = new Digger(name, session, inventory, claims, leaves.get(), Digger.walkTicks(soldier, leaves.get().stand()));
    }

    /** The slot of a sapling to plant: the first choice of {@link #PREFERRED} carried, any other if none is. */
    private Optional<Integer> saplingSlot() {
        Integer best = null;
        int bestRank = Integer.MAX_VALUE;
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            int item = inventory.itemAt(slot);
            if (ItemClassRegistry.classOf(item).equals(Optional.of(ItemClass.SAPLING))) {
                String tree = ItemClassRegistry.nameOf(item).map(n -> n.replace("_sapling", "")).orElse("");
                int rank = PREFERRED.contains(tree) ? PREFERRED.indexOf(tree) : PREFERRED.size();
                if (rank < bestRank) {
                    best = slot;
                    bestRank = rank;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private void plant(WorldView world, BlockPos cell, int slot) {
        claimed = cell;
        claims.add(cell);
        standAt = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        int item = inventory.itemAt(slot);
        String sapling = ItemClassRegistry.nameOf(item).orElse("sapling");
        planting = new Script(name, List.of(
                new Step("walk to " + cell, () -> {
                }, new Expect("standing in " + cell, () -> on(cell)), Digger.walkTicks(soldier, cell)), // as far as it is: live (field627),
                // cells 50 blocks off, 90 by route, timed out at 20 s every time, 17 trips, each cell then barred for all
                new Step(sapling + " in hand", () -> heldItem() == item, () -> takeInHand(item),
                        new Expect(sapling + " in hand", () -> heldItem() == item), ANSWER_TICKS),
                new Step("plant " + sapling + " at " + cell, () -> TreeFarm.planted(world, cell), () -> {
                    session.useBlockFace(cell.x(), cell.y() - 1, cell.z(), BlockFace.UP);
                    LOG.info("{} plants {} at {}", name, sapling, cell);
                }, new Expect("a sapling at " + cell, () -> TreeFarm.planted(world, cell)), ANSWER_TICKS)));
    }

    private int heldItem() {
        return inventory.itemAt(Inventory.HOTBAR_FIRST + inventory.heldHotbarSlot());
    }

    private boolean on(BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6 && Math.abs(soldier.y() - cell.y()) < 0.6;
    }

    /** Holds {@code item}: from the hotbar, or swapped into a hotbar slot holding no weapon or tool. */
    private void takeInHand(int item) {
        int slot = -1;
        for (int s = 0; s < Inventory.SLOTS && slot < 0; s++) {
            if (inventory.itemAt(s) == item) {
                slot = s;
            }
        }
        if (slot < 0) {
            throw new IllegalStateException("no " + ItemClassRegistry.nameOf(item).orElse("sapling") + " carried - " + inventory.summary());
        }
        int hotbar = slot >= Inventory.HOTBAR_FIRST ? slot - Inventory.HOTBAR_FIRST : spareHotbarSlot();
        if (slot < Inventory.HOTBAR_FIRST) {
            session.swapWithHotbar(0, 0, slot, hotbar); // stale state: the server resends the inventory
        }
        session.selectHotbarSlot(hotbar);
        inventory.selectHotbar(hotbar);
    }

    private int spareHotbarSlot() {
        return inventory.spareHotbarSlot(); // Inventory.spareHotbarSlot: one rule for every routine
    }

    private void fail(String why) {
        LOG.warn("{} stops planting for a while - {}", name, why);
        if (claimed != null) {
            shared.failed(claimed, ticks, why); // the cell, not only the forester, kept off for a while
        }
        failure = why;
        cooldown = COOLDOWN_TICKS;
        stop();
    }

    private void endPlanting() {
        planting = null;
        standAt = null;
        if (claimed != null) {
            claims.remove(claimed);
            claimed = null;
        }
    }

    private void stopDigging() {
        if (digger != null) {
            digger.release();
            digger = null;
        }
    }

    /** Stops the job under way, its claims given up. */
    public synchronized void stop() {
        stopDigging();
        endPlanting();
        if (fitting != null) {
            fitters.get(fitting).stop();
            fitting = null;
        }
    }
}
