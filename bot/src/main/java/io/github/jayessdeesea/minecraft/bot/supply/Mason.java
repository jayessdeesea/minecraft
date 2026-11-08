package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.script.Script;
import io.github.jayessdeesea.minecraft.bot.script.Script.Expect;
import io.github.jayessdeesea.minecraft.bot.script.Script.Step;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier building its squad's {@link Perimeter}: it takes the nearest open cell of the wall no
 * squadmate has taken, stands just inside it, and puts a block there - cobblestone first, then dirt;
 * logs and planks are kept for tools. An expect script: done when the server's block update shows
 * the cell solid. Soldiers on one wall share {@code claims}.
 */
public final class Mason {

    private static final Logger LOG = LogManager.getLogger(Mason.class);

    static final int WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    static final int COOLDOWN_TICKS = 100;
    /** What a wall is built of, in the order it's used. */
    static final List<ItemClass> MATERIALS = List.of(ItemClass.COBBLESTONE, ItemClass.DIRT);

    private final String name;
    private final Inventory inventory;
    private final Footing footing;
    private final Set<BlockPos> claims;

    private Script job;
    private BlockPos claimed;
    private Position standAt;
    private Position soldier;
    private int cooldown;
    private String failure;

    public Mason(String name, Inventory inventory, Footing footing, Set<BlockPos> claims) {
        this.name = name;
        this.inventory = inventory;
        this.footing = footing;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return job != null;
    }

    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Blocks carried to build with. */
    public static int blocks(Inventory inventory) {
        return MATERIALS.stream().mapToInt(inventory::count).sum();
    }

    /** Each tick: builds the {@code open} cells of the wall {@code radius} out round {@code centre} while {@code free}. */
    public synchronized void tick(Position soldier, WorldView world, BlockPos centre, int radius, List<BlockPos> open, boolean free) {
        this.soldier = soldier;
        if (cooldown > 0) {
            cooldown--;
        }
        if (!free || open.isEmpty()) {
            stop();
            return;
        }
        if (job != null) {
            switch (job.tick()) {
                case Script.Status.Running _ -> {
                }
                case Script.Status.Done _, Script.Status.Interrupted _ -> end();
                case Script.Status.Failed(String step, String reason) -> fail(step + ": " + reason);
            }
            return;
        }
        if (cooldown > 0 || blocks(inventory) == 0) {
            return;
        }
        // Any cell with a block to go against - the one under it, or for the lip the wall beside it -
        // so each column rises from its own ground: on a slope the wall's rows are at many heights.
        // The nearest with somewhere inside to stand: one without it waits, rather than holding up the rest.
        open.stream().filter(c -> !claims.contains(c) && Builder.support(world, c).isPresent())
                .sorted(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())))
                .flatMap(cell -> inside(world, cell, centre, radius).map(stand -> java.util.Map.entry(cell, stand)).stream())
                .findFirst().ifPresent(job -> start(world, job.getKey(), job.getValue()));
    }

    /**
     * Where to stand to build {@code cell}: the column nearest it just inside the wall {@code radius}
     * out - beside a wall cell, two in from the lip, never on the wall's top - at a standing height
     * near the cell's.
     */
    static Optional<BlockPos> inside(WorldView world, BlockPos cell, BlockPos centre, int radius) {
        int x = centre.x() + Math.clamp(cell.x() - centre.x(), -(radius - 1), radius - 1);
        int z = centre.z() + Math.clamp(cell.z() - centre.z(), -(radius - 1), radius - 1);
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos stand = new BlockPos(x, cell.y() + dy, z);
            if (Pathfinder.standable(world, stand)) {
                return Optional.of(stand);
            }
        }
        return Optional.empty();
    }

    private void start(WorldView world, BlockPos cell, BlockPos stand) {
        claimed = cell;
        claims.add(cell);
        standAt = new Position(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
        ItemClass material = MATERIALS.stream().filter(m -> inventory.count(m) > 0).findFirst().orElseThrow();
        job = new Script(name, List.of(
                new Step("walk to " + stand, () -> {
                }, new Expect("standing in " + stand, () -> on(stand)), WALK_TICKS),
                new Step("wall at " + cell, () -> world.isSolid(cell.x(), cell.y(), cell.z()), () -> {
                    // A torch put down on the wall's line when the camp was lit holds the cell.
                    if (!footing.placeClearing(cell, material, Builder.support(world, cell).orElseThrow(
                            () -> new IllegalStateException("nothing beside " + cell + " to put a block against")))) {
                        throw new IllegalStateException("no " + material + " carried - " + inventory.summary());
                    }
                }, new Expect("a block at " + cell, () -> world.isSolid(cell.x(), cell.y(), cell.z())), ANSWER_TICKS)));
    }

    private boolean on(BlockPos cell) {
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6 && Math.abs(soldier.y() - cell.y()) < 0.6;
    }

    private void fail(String why) {
        LOG.warn("{} stops walling for a while - {}", name, why);
        failure = why;
        cooldown = COOLDOWN_TICKS;
        stop();
    }

    private void end() {
        job = null;
        standAt = null;
        if (claimed != null) {
            claims.remove(claimed);
            claimed = null;
        }
    }

    public synchronized void stop() {
        end();
    }
}
