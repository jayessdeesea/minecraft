package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import java.util.OptionalDouble;

/** A soldier's aim and footing, pure: an arrow's flight as the server flies it, its line of fire, the cell it stands in. */
final class Ballistics {

    private Ballistics() {
    }

    /** An arrow loosed at full draw: blocks a tick, then 1% drag and 0.05 gravity each tick (vanilla's). */
    static final double ARROW_SPEED = 3.0;

    static final double ARROW_DRAG = 0.99;

    static final double ARROW_GRAVITY = 0.05;

    /**
     * The pitch, in degrees as the game counts them (up is negative), that sends an arrow loosed at
     * full draw from the eyes to a point {@code across} blocks away horizontally and {@code up}
     * blocks higher - the flatter of the two arcs, found by flying the arrow as the server does.
     * Empty when no arc reaches that far.
     */
    static OptionalDouble aimPitch(double across, double up) {
        OptionalDouble best = OptionalDouble.empty();
        double bestMiss = 0.5; // hit within half a block, or not at all
        for (double elevation = -60; elevation <= 45; elevation += 0.25) {
            double theta = Math.toRadians(elevation);
            double x = 0, y = 0, vx = ARROW_SPEED * Math.cos(theta), vy = ARROW_SPEED * Math.sin(theta);
            for (int tick = 0; tick < 100 && x < across; tick++) {
                double nx = x + vx, ny = y + vy;
                if (nx >= across) {
                    y += vy * (across - x) / vx;
                    x = across;
                    break;
                }
                x = nx;
                y = ny;
                vx *= ARROW_DRAG;
                vy = vy * ARROW_DRAG - ARROW_GRAVITY;
            }
            if (x >= across && Math.abs(y - up) < bestMiss) {
                bestMiss = Math.abs(y - up);
                best = OptionalDouble.of(-elevation);
                if (bestMiss < 0.05) {
                    break; // the flat arc, close enough: a steeper one only flies longer
                }
            }
        }
        return best;
    }

    /**
     * Whether any of {@code others} stands within {@link #LINE_OF_FIRE_CLEAR} of the line from the eyes of a
     * soldier at {@code from} to {@code target}'s middle, between the two. Live (field463), Ned was killed by
     * Alice's arrow at a mob beyond him.
     */
    static boolean inTheLine(io.github.jayessdeesea.minecraft.command.Position from, io.github.jayessdeesea.minecraft.command.Position target,
                             java.util.List<io.github.jayessdeesea.minecraft.command.Position> others) {
        double ex = from.x(), ey = from.y() + DeterministicSoldier.EYE_HEIGHT, ez = from.z();
        double tx = target.x() - ex, ty = target.y() + DeterministicSoldier.TARGET_HEIGHT - ey, tz = target.z() - ez;
        double length2 = tx * tx + ty * ty + tz * tz;
        if (length2 == 0) {
            return false;
        }
        for (io.github.jayessdeesea.minecraft.command.Position p : others) {
            double px = p.x() - ex, py = p.y() + DeterministicSoldier.PLAYER_HEIGHT / 2 - ey, pz = p.z() - ez;
            double along = (px * tx + py * ty + pz * tz) / length2;
            if (along <= 0 || along >= 1) {
                continue;
            }
            double dx = px - along * tx, dy = py - along * ty, dz = pz - along * tz;
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= LINE_OF_FIRE_CLEAR) {
                return true;
            }
        }
        return false;
    }

    /** How far off an arrow's line a squadmate must stand for it to be loosed: a body's width and the arc's give. */
    static final double LINE_OF_FIRE_CLEAR = 1.2;

    /** Yaw toward a horizontal direction, in Minecraft's convention. */
    static float yawToward(double dx, double dz) {
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
    }

    /**
     * The cell a route starts from: the one {@code at} is in, unless that has no floor and the body,
     * 0.6 wide, stands on the edge of a block beside it - then that block's cell, the nearest such.
     * Live, a farmer stood on a block's edge over the crop beside it, every plan started from the
     * crop's cell, with no floor, and it reported no route for good.
     */
    static BlockPos standingCell(WorldView world, Position at) {
        return Pathfinder.standingCell(world, at.x(), at.y(), at.z());
    }
}
