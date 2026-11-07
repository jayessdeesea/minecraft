package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
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
 * One soldier lighting the ground its squad secures ({@link Lighting}): it walks to the nearest
 * cell of the plan no squadmate has taken, and puts a torch down where it stands. An expect script
 * (AGENTS.md § Behavior modeling): the job is done when the server's block update shows the torch.
 * Soldiers lighting one area share {@code claims}, so each takes a different cell. Torches are the
 * torchmaker's to make; with none carried this has nothing to do.
 */
public final class Lamplighter {

    private static final Logger LOG = LogManager.getLogger(Lamplighter.class);

    static final int WALK_TICKS = 400;
    static final int ANSWER_TICKS = 40;
    static final int COOLDOWN_TICKS = 200;

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

    public Lamplighter(String name, Inventory inventory, Footing footing, Set<BlockPos> claims) {
        this.name = name;
        this.inventory = inventory;
        this.footing = footing;
        this.claims = claims;
    }

    public synchronized boolean active() {
        return job != null;
    }

    /** Where the soldier should be standing for the torch under way. */
    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    /** Why the last torch failed, while the soldier waits before the next. */
    public synchronized Optional<String> failure() {
        return cooldown > 0 ? Optional.ofNullable(failure) : Optional.empty();
    }

    /** Each tick: lights the {@code plan}'s cells while {@code free}; anything else stops the torch under way. */
    public synchronized void tick(Position soldier, WorldView world, List<BlockPos> plan, boolean free) {
        this.soldier = soldier;
        if (cooldown > 0) {
            cooldown--;
        }
        if (!free || plan.isEmpty()) {
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
        if (cooldown > 0 || inventory.count(ItemClass.TORCH) == 0) {
            return;
        }
        plan.stream().filter(c -> !claims.contains(c) && !world.isTorch(c.x(), c.y(), c.z()))
                .min(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - soldier.x(), c.z() + 0.5 - soldier.z())))
                .ifPresent(cell -> start(world, cell));
    }

    private void start(WorldView world, BlockPos cell) {
        claimed = cell;
        claims.add(cell);
        standAt = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
        job = new Script(name, List.of(
                new Step("walk to " + cell, () -> {
                }, new Expect("standing in " + cell, this::onCell), WALK_TICKS),
                new Step("torch at " + cell, () -> world.isTorch(cell.x(), cell.y(), cell.z()), () -> {
                    if (!footing.place(cell, ItemClass.TORCH)) {
                        throw new IllegalStateException("no torch carried - " + inventory.summary());
                    }
                    LOG.info("{} lights {} with a torch", name, cell);
                }, new Expect("a torch at " + cell, () -> world.isTorch(cell.x(), cell.y(), cell.z())), ANSWER_TICKS)));
    }

    private boolean onCell() {
        BlockPos cell = claimed;
        return Math.hypot(soldier.x() - (cell.x() + 0.5), soldier.z() - (cell.z() + 0.5)) < 0.6 && Math.abs(soldier.y() - cell.y()) < 0.6;
    }

    private void fail(String why) {
        LOG.warn("{} stops lighting for a while - {}", name, why);
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

    /** Stops the torch under way, its claim given up. */
    public synchronized void stop() {
        end();
    }
}
