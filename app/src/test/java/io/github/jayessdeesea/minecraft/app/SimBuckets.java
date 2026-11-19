package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;

import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * A bucket used in the squad simulation, as the server uses one: a ray cast along the player's look from its eyes,
 * out to its reach. An empty bucket takes the first water source the ray meets - a pond's source with two more
 * beside it stays, as it fills again; a lone one is gone - and a full one pours a source into the open cell before
 * the first block the ray meets, water and all passed through.
 */
final class SimBuckets {

    private static final double EYES = 1.62, REACH = 5, STEP = 0.02;
    static final int BUCKET = SimServer.itemOf(ItemClass.BUCKET), WATER_BUCKET = SimServer.itemOf(ItemClass.WATER_BUCKET);

    private SimBuckets() {
    }

    private static boolean water(int state) {
        return state >= SimServer.WATER && state <= SimServer.WATER + 15;
    }

    /** What's in hand after using {@code held} looking ({@code yaw}, {@code pitch}) from {@code at}; empty if it's no bucket. */
    static OptionalInt use(SimServer world, PlayerPosition at, float yaw, float pitch, int held, Consumer<String> log) {
        if (held != BUCKET && held != WATER_BUCKET) {
            return OptionalInt.empty();
        }
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double dx = -Math.sin(y) * Math.cos(p), dy = -Math.sin(p), dz = Math.cos(y) * Math.cos(p);
        int[] last = null;
        for (double t = 0; t <= REACH; t += STEP) {
            int[] c = {(int) Math.floor(at.x() + dx * t), (int) Math.floor(at.y() + EYES + dy * t), (int) Math.floor(at.z() + dz * t)};
            int state = world.block(c[0], c[1], c[2]);
            if (held == BUCKET && state == SimServer.WATER) {
                int beside = 0;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    beside += world.block(c[0] + d[0], c[1], c[2] + d[1]) == SimServer.WATER ? 1 : 0;
                }
                if (beside < 2) {
                    world.set(c[0], c[1], c[2], SimServer.AIR);
                }
                log.accept("filled a bucket at " + c[0] + "," + c[1] + "," + c[2]);
                return OptionalInt.of(WATER_BUCKET);
            }
            if (state != SimServer.AIR && !water(state)) {
                if (held == WATER_BUCKET && last != null) {
                    world.set(last[0], last[1], last[2], SimServer.WATER);
                    log.accept("poured water at " + last[0] + "," + last[1] + "," + last[2]);
                    return OptionalInt.of(BUCKET);
                }
                return OptionalInt.of(held);
            }
            last = c;
        }
        return OptionalInt.of(held);
    }
}
