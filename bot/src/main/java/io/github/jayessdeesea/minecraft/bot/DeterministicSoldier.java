package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Soldier;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The {@code bot} module's implementation of {@link Soldier}: a deterministic, non-LLM per-tick
 * decision core (AGENTS.md's "Behavior modeling" section). {@code app}'s {@code SoldierRunner} is
 * the imperative shell: it calls {@link #tick} at 20Hz and reports the resulting position to the
 * server; tests call it directly with a fake {@code WorldView}.
 *
 * <p>{@code position}/{@code order} are {@code volatile} rather than behind a lock: {@link
 * #receiveOrder} may be called from a different thread than {@link #tick} (a future SquadLeader's
 * own invocation thread) but never concurrently with itself, and {@link #tick} is only ever called
 * by this soldier's own single tick loop - the same "sole writer" guarantee an actor's mailbox
 * gives you, without needing one.
 */
public final class DeterministicSoldier implements Soldier {

    /** Blocks per tick - 4 blocks/s at 20Hz, just under vanilla walk speed (~4.3), not tuned.
     * Revisit if the server starts correcting reported positions (it would send Player Position). */
    private static final double STEP = 0.2;

    private final String name;
    private volatile Position position;
    private volatile Order order = new Order.Idle();
    private final AtomicReference<Position> pendingCorrection = new AtomicReference<>();

    public DeterministicSoldier(String name, Position startingPosition) {
        this.name = name;
        this.position = startingPosition;
    }

    @Override
    public void receiveOrder(Order order) {
        this.order = order;
    }

    /**
     * The server says this soldier is somewhere else (a respawn, or a correction of a reported
     * move) - authoritative, so it wins. Applied at the start of the next {@link #tick} rather than
     * written here, so the tick loop stays the sole writer of {@code position}.
     */
    public void correctPosition(Position serverPosition) {
        pendingCorrection.set(serverPosition);
    }

    @Override
    public SoldierStatus status() {
        return new SoldierStatus(name, position, order);
    }

    /**
     * Ballpark starting value, not tuned against real mob behavior - vanilla hostiles typically
     * notice a player around 16 blocks out, so this reacts well inside that. Revisit once a live
     * run shows soldiers reacting too late or too skittishly.
     */
    public static final double CONTACT_RADIUS = 8.0;

    /**
     * One tick of movement. First the "actions on contact" reflex: if a hostile is within {@link
     * #CONTACT_RADIUS}, step directly away from the nearest one, ignoring the current order
     * entirely - the infantry "break contact" drill, decided here at tick speed with no squad
     * leader involved. If the retreat step is blocked, stand still rather than fall back to the
     * order (which might lead toward the threat).
     *
     * <p>Otherwise advances one step toward the current order's target, unless {@code world}
     * reports the next block as solid - in which case this soldier simply stays put. No pathing
     * around obstacles by design (see {@code Order.TrendToward}'s javadoc): getting stuck on a tree
     * or a mountain is an acceptable outcome for this soldier, not a bug to route around.
     */
    public void tick(WorldView world) {
        Position correction = pendingCorrection.getAndSet(null);
        if (correction != null) {
            position = correction;
            return;
        }

        List<TrackedEntity> threats = world.nearbyHostiles(position, CONTACT_RADIUS);
        if (!threats.isEmpty()) {
            Position threat = threats.get(0).position();
            stepToward(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
            return;
        }

        if (order instanceof Order.TrendToward trend) {
            stepToward(world, trend.x(), trend.z());
        }
    }

    private void stepToward(WorldView world, double targetX, double targetZ) {
        double dx = targetX - position.x();
        double dz = targetZ - position.z();
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
