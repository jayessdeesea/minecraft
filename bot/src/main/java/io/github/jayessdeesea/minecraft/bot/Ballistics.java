package io.github.jayessdeesea.minecraft.bot;

import java.util.OptionalDouble;

/** An arrow's flight, as the server flies it: how to aim a bow at a mark. Pure. */
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
}
