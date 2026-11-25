package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import static io.github.jayessdeesea.minecraft.bot.Body.*;
import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.*;

/**
 * A soldier's body moving through the world, a tick at a time: where it is, how it's falling, swimming or climbing out
 * of the water, and the step it takes toward where it's going - the walk's physics, apart from what it decides
 * (docs/architecture.md, behaviour modelling; backlog/split-oversized-sources.md: DeterministicSoldier's moves). Its
 * state is the tick loop's: written there, {@link #position} and {@link #onGround} read from other threads.
 */
final class Gait {

    /** Blocks per tick - 4 blocks/s at 20Hz, just under vanilla walk speed (~4.3), not tuned. Revisit if the server
     * starts correcting reported positions (it would send Player Position). */
    static final double STEP = 0.2;
    // Vanilla player physics constants (LivingEntity): per tick, velocity -= 0.08 then *= 0.98;
    // a jump starts at 0.42 (peaks ~1.25 blocks - enough for one block, not two).
    static final double GRAVITY = 0.08;
    static final double DRAG = 0.98;
    static final double JUMP_VELOCITY = 0.42;

    volatile Position position;
    double verticalVelocity;
    volatile boolean onGround = true;
    /** Whether it's swimming: its feet in water. */
    volatile boolean afloat;
    /** Rising out of the water onto a bank this tick: the water doesn't settle it back. */
    boolean climbingOut;
    /** Whether the step under way is to a waypoint on the soldier's own level: a snag is slid along before any jump. */
    boolean levelStep;

    Gait(Position at) {
        this.position = at;
    }

    /** Where it was and how it was moving: for a step tried and taken back. */
    record Snapshot(Position position, boolean onGround, double verticalVelocity, boolean climbingOut) {
    }

    Snapshot snapshot() {
        return new Snapshot(position, onGround, verticalVelocity, climbingOut);
    }

    void restore(Snapshot s) {
        position = s.position();
        onGround = s.onGround();
        verticalVelocity = s.verticalVelocity();
        climbingOut = s.climbingOut();
    }

    /** A tick of falling, landing or floating: gravity and drag on land, the surface held in water. */
    void fall(WorldView world) {
        double x = position.x();
        double z = position.z();
        double y = position.y();
        int fx = (int) Math.floor(x);
        int fz = (int) Math.floor(z);
        int feet = (int) Math.floor(y + EPSILON);
        if (world.isWater(fx, feet, fz)) {
            // Afloat: no gravity, no fall - the body settles to the surface, head out.
            afloat = true;
            onGround = false;
            verticalVelocity = 0;
            if (climbingOut) {
                climbingOut = false;
                return;
            }
            int top = feet;
            while (world.isWater(fx, top + 1, fz)) {
                top++;
            }
            double rise = Math.max(-SWIM_RISE, Math.min(SWIM_RISE, top + SWIM_FLOAT - y));
            position = new Position(x, y + rise, z);
            return;
        }
        afloat = false;
        climbingOut = false;
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

    /**
     * A step toward ({@code targetX}, {@code targetZ}) at {@code pace} a tick - slower swimming - sliding along what it
     * meets, rising out of water onto a bank, jumping a one-block rise: on a level step ({@link #levelStep}) a snag is
     * slid along before any jump.
     */
    void step(WorldView world, double targetX, double targetZ, double pace) {
        double step = pace * (afloat ? SWIM_STEP / STEP : 1);
        double dx = targetX - position.x();
        double dz = targetZ - position.z();
        double distance = Math.hypot(dx, dz);
        if (distance < EPSILON) {
            return;
        }
        // A box that doesn't fit where it stands - a block put since in a column it reaches into - is refused every step
        // that still reaches into that column, and a step is shorter than the reach: it goes to the middle of its own
        // cell, where it fits, first. Only on its feet, and only onto a floor: over a drop it stays where it is held.
        double middleX = Math.floor(position.x()) + 0.5;
        double middleZ = Math.floor(position.z()) + 0.5;
        if (onGround && !bodyFits(world, position.x(), position.y(), position.z()) && bodyFits(world, middleX, position.y(), middleZ)
                && world.isSolid((int) Math.floor(position.x()), (int) Math.floor(position.y()) - 1, (int) Math.floor(position.z()))) {
            double off = Math.hypot(middleX - position.x(), middleZ - position.z());
            double toMiddle = Math.min(step, off);
            position = new Position(position.x() + (middleX - position.x()) / off * toMiddle, position.y(),
                    position.z() + (middleZ - position.z()) / off * toMiddle);
            return;
        }
        double move = Math.min(step, distance); // never overshoot - nor stall just short of the target

        double stepX = (dx / distance) * move;
        double stepZ = (dz / distance) * move;
        double nextX = position.x() + stepX;
        double nextZ = position.z() + stepZ;
        double y = position.y();
        if (bodyFits(world, nextX, y, nextZ)) {
            position = new Position(nextX, y, nextZ);
        } else if (afloat && bodyFits(world, nextX, Math.floor(y) + 1, nextZ)
                && bodyFits(world, position.x(), Math.floor(y) + 1, position.z())) {
            // A bank one block above the water: rise out of it, then step on once clear of the edge.
            position = new Position(position.x(), Math.min(y + SWIM_RISE, Math.floor(y) + 1), position.z());
            climbingOut = true;
        } else if (levelStep && Math.abs(stepX) > 1e-9 && bodyFits(world, nextX, y, position.z())) {
            // On the level, a corner snagged is slid along, not jumped onto: a jump costs hunger, and the route goes round
            // it. Live (field490-494), jumping was the force's biggest burn, most of it walking routes.
            position = new Position(nextX, y, position.z());
        } else if (levelStep && Math.abs(stepZ) > 1e-9 && bodyFits(world, position.x(), y, nextZ)) {
            position = new Position(position.x(), y, nextZ);
        } else if (onGround && !anyHazard(world, nextX, nextZ, (int) Math.floor(y), (int) Math.floor(y))
                && bodyFits(world, nextX, Math.floor(y) + 1, nextZ)
                && bodyFits(world, position.x(), Math.floor(y) + 1, position.z())) {
            // A one-block rise with headroom here and there: jump; the step happens once airborne.
            // Never over water or lava: nothing says where the jump comes down.
            verticalVelocity = JUMP_VELOCITY;
            onGround = false;
        } else if (Math.abs(stepX) > 1e-9 && bodyFits(world, nextX, y, position.z())) {
            // Blocked on the diagonal: slide along the obstacle, one axis at a time - how vanilla
            // resolves collisions. The route is planned on cell centres; a 0.6-wide body standing
            // off-centre can snag a corner the plan doesn't see.
            position = new Position(nextX, y, position.z());
        } else if (Math.abs(stepZ) > 1e-9 && bodyFits(world, position.x(), y, nextZ)) {
            position = new Position(position.x(), y, nextZ);
        }
    }
}
