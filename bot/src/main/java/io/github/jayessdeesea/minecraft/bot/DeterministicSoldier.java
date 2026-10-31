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

    // Vanilla player physics constants (LivingEntity): per tick, velocity -= 0.08 then *= 0.98;
    // a jump starts at 0.42 (peaks ~1.25 blocks - enough for one block, not two).
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double BODY_HEIGHT = 1.8;
    private static final double HALF_WIDTH = 0.3; // a player's box is 0.6 x 1.8 x 0.6
    private static final double EPSILON = 1e-9;

    private final String name;
    private volatile Position position;
    private volatile Order order = new Order.Idle();
    private final AtomicReference<Position> pendingCorrection = new AtomicReference<>();
    // Tick-loop-only, like position's writes - no other thread reads it.
    private double verticalVelocity = 0;
    private volatile boolean onGround = true;

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

    /** Whether the last tick ended standing on something - reported to the server with each move. */
    public boolean onGround() {
        return onGround;
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
     * <p>Otherwise advances one step toward the current order's target. A step needs room for the
     * whole ~1.8-block body; a one-block rise is jumped, anything taller stops the soldier. No
     * pathing around obstacles by design (see {@code Order.TrendToward}'s javadoc): getting stuck
     * on a tree or a mountain is an acceptable outcome for this soldier, not a bug to route around.
     *
     * <p>Then vertical movement with vanilla gravity: the server kicks a client that reports
     * standing while hovering over lower ground ("floating too long"), so falling is not optional.
     */
    public void tick(WorldView world) {
        Position correction = pendingCorrection.getAndSet(null);
        if (correction != null) {
            position = correction;
            verticalVelocity = 0;
            return;
        }

        List<TrackedEntity> threats = world.nearbyHostiles(position, CONTACT_RADIUS);
        if (!threats.isEmpty()) {
            Position threat = threats.get(0).position();
            stepToward(world, 2 * position.x() - threat.x(), 2 * position.z() - threat.z());
        } else if (order instanceof Order.TrendToward trend) {
            stepToward(world, trend.x(), trend.z());
        }

        applyVerticalMovement(world);
    }

    private void applyVerticalMovement(WorldView world) {
        double x = position.x();
        double z = position.z();
        double y = position.y();
        double velocity = onGround ? -GRAVITY * DRAG : verticalVelocity;
        double nextY = y + velocity;

        if (velocity < 0) {
            // Land on the highest solid block whose top lies between here and nextY.
            for (int blockY = (int) Math.floor(y + EPSILON) - 1; blockY >= (int) Math.floor(nextY); blockY--) {
                if (anySolid(world, x, z, blockY, blockY)) {
                    position = new Position(position.x(), blockY + 1, position.z());
                    verticalVelocity = 0;
                    onGround = true;
                    return;
                }
            }
        } else if (anySolid(world, x, z, (int) Math.floor(nextY + BODY_HEIGHT), (int) Math.floor(nextY + BODY_HEIGHT))) {
            nextY = y; // bumped a ceiling
            velocity = 0;
        }

        position = new Position(position.x(), nextY, position.z());
        verticalVelocity = (velocity - GRAVITY) * DRAG;
        onGround = false;
    }

    private static boolean bodyFits(WorldView world, double x, double y, double z) {
        return !anySolid(world, x, z, (int) Math.floor(y), (int) Math.floor(y + BODY_HEIGHT - EPSILON));
    }

    /** Whether any block in rows {@code minY..maxY} under the body's 0.6-wide footprint at (x, z) is solid. */
    private static boolean anySolid(WorldView world, double x, double z, int minY, int maxY) {
        int minX = (int) Math.floor(x - HALF_WIDTH);
        int maxX = (int) Math.floor(x + HALF_WIDTH - EPSILON);
        int minZ = (int) Math.floor(z - HALF_WIDTH);
        int maxZ = (int) Math.floor(z + HALF_WIDTH - EPSILON);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isSolid(bx, by, bz)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        double y = position.y();
        if (bodyFits(world, nextX, y, nextZ)) {
            position = new Position(nextX, y, nextZ);
        } else if (onGround && bodyFits(world, nextX, Math.floor(y) + 1, nextZ)
                && bodyFits(world, position.x(), Math.floor(y) + 1, position.z())) {
            // A one-block rise with headroom here and there: jump; the step happens once airborne.
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        }
    }
}
