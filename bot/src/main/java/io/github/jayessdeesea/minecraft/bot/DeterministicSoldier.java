package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Soldier;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

/**
 * The {@code bot} module's implementation of {@link Soldier}: a deterministic, non-LLM per-tick
 * decision core (AGENTS.md's "Behavior modeling" section) wrapped by whatever imperative shell
 * eventually drives it from a live, persistent Play connection - no such shell exists yet, so
 * {@link #tick} is called directly (by a test today, by a real scheduler once one exists).
 *
 * <p>{@code position}/{@code order} are {@code volatile} rather than behind a lock: {@link
 * #receiveOrder} may be called from a different thread than {@link #tick} (a future SquadLeader's
 * own invocation thread) but never concurrently with itself, and {@link #tick} is only ever called
 * by this soldier's own single tick loop - the same "sole writer" guarantee an actor's mailbox
 * gives you, without needing one.
 */
public final class DeterministicSoldier implements Soldier {

    /** Blocks per tick - ballpark vanilla walk speed, not wire-verified (nothing sends real
     * movement packets yet). Revisit once a real shell exists to send actual position packets. */
    private static final double STEP = 0.2;

    private final String name;
    private volatile Position position;
    private volatile Order order = new Order.Idle();

    public DeterministicSoldier(String name, Position startingPosition) {
        this.name = name;
        this.position = startingPosition;
    }

    @Override
    public void receiveOrder(Order order) {
        this.order = order;
    }

    @Override
    public SoldierStatus status() {
        return new SoldierStatus(name, position, order);
    }

    /**
     * Advances one step toward the current order's target, unless {@code world} reports the next
     * block as solid - in which case this soldier simply stays put. No pathing around obstacles by
     * design (see {@code Order.TrendToward}'s javadoc): getting stuck on a tree or a mountain is an
     * acceptable outcome for this soldier, not a bug to route around.
     */
    public void tick(WorldView world) {
        if (!(order instanceof Order.TrendToward trend)) {
            return;
        }

        double dx = trend.x() - position.x();
        double dz = trend.z() - position.z();
        double distance = Math.hypot(dx, dz);
        if (distance < STEP) {
            return;
        }

        double nextX = position.x() + (dx / distance) * STEP;
        double nextZ = position.z() + (dz / distance) * STEP;
        if (world.isSolid((int) Math.floor(nextX), (int) Math.floor(position.y()), (int) Math.floor(nextZ))) {
            return;
        }

        position = new Position(nextX, position.y(), nextZ);
    }
}
