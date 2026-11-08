package io.github.jayessdeesea.minecraft.bot.supply;

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
import java.util.Set;

/**
 * One soldier planting its squad's {@link TreeFarm}: each tick it does the most pressing job left -
 * <ol>
 *   <li>plant a sapling in a plot cell with nothing growing, a spruce one if it carries any;</li>
 *   <li>with no sapling, break leaves for one - spruce leaves first, since spruce is the camp's tree -
 *       one in twenty drops its sapling.</li>
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
    /** The camp's tree. */
    static final String PREFERRED = "spruce";

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

    public Forester(String name, PlaySession session, Inventory inventory, Set<BlockPos> claims) {
        this.name = name;
        this.session = session;
        this.inventory = inventory;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return digger != null || planting != null;
    }

    /** Where the soldier should be standing for the job under way. */
    public synchronized Optional<Position> standAt() {
        return digger != null ? Optional.of(digger.standAt()) : Optional.ofNullable(standAt);
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
        if (cooldown > 0) {
            return;
        }
        next(world, farm.get());
    }

    private void next(WorldView world, TreeFarm farm) {
        Optional<BlockPos> bare = farm.plot().stream().filter(c -> !claims.contains(c) && !TreeFarm.planted(world, c))
                .min(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())));
        if (bare.isEmpty()) {
            return;
        }
        Optional<Integer> sapling = saplingSlot();
        if (sapling.isPresent()) {
            plant(world, bare.get(), sapling.get());
            return;
        }
        BlockPos at = BlockPos.containing(soldier.x(), soldier.y(), soldier.z());
        Optional<Digger.Spot> leaves = Digger.findSpot(world, at, claims, Digger.Target.SPRUCE_LEAVES)
                .or(() -> Digger.findSpot(world, at, claims, Digger.Target.LEAVES));
        if (leaves.isEmpty()) {
            fail("no sapling, and no leaves within " + Digger.SEARCH_RADIUS + " blocks to get one from");
            return;
        }
        digger = new Digger(name, session, inventory, claims, leaves.get());
    }

    /** The slot of a sapling to plant: a spruce one if carried, any other if not. */
    private Optional<Integer> saplingSlot() {
        Integer any = null;
        for (int slot = 0; slot < Inventory.SLOTS; slot++) {
            int item = inventory.itemAt(slot);
            if (ItemClassRegistry.classOf(item).equals(Optional.of(ItemClass.SAPLING))) {
                if (ItemClassRegistry.nameOf(item).filter((PREFERRED + "_sapling")::equals).isPresent()) {
                    return Optional.of(slot);
                }
                any = any == null ? slot : any;
            }
        }
        return Optional.ofNullable(any);
    }

    private void plant(WorldView world, BlockPos cell, int slot) {
        claimed = cell;
        claims.add(cell);
        standAt = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        int item = inventory.itemAt(slot);
        String sapling = ItemClassRegistry.nameOf(item).orElse("sapling");
        planting = new Script(name, List.of(
                new Step("walk to " + cell, () -> {
                }, new Expect("standing in " + cell, () -> on(cell)), WALK_TICKS),
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
        for (int hotbar = 8; hotbar >= 0; hotbar--) {
            Optional<ItemClass> held = ItemClassRegistry.classOf(inventory.itemAt(Inventory.HOTBAR_FIRST + hotbar));
            if (held.isEmpty() || held.get() != ItemClass.WEAPON && held.get() != ItemClass.PICKAXE) {
                return hotbar;
            }
        }
        throw new IllegalStateException("every hotbar slot holds a weapon or a tool");
    }

    private void fail(String why) {
        LOG.warn("{} stops planting for a while - {}", name, why);
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
    }
}
