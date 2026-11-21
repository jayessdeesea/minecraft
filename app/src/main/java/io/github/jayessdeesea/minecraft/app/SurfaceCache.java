package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each column's surface as last scanned, for {@link #KEPT_MILLIS}: whether a soldier is underground is asked every
 * tick by night, and each answer scanned its column down from the sky - live (profile at commit 816), 3% of the
 * garrison's time. A surface a second or two old is as good for it. Thread-safe.
 */
final class SurfaceCache {

    /** How long a column's surface is kept before it's scanned again. */
    static final long KEPT_MILLIS = 2_000;

    private record Seen(Optional<BlockPos> top, long at) {
    }

    private final Map<Long, Seen> columns = new ConcurrentHashMap<>();

    /** Whether {@code at} is under the ground of {@code world} at {@code now}: its column's surface more than {@code depth} over it. */
    boolean underground(WorldView world, Position at, int depth, long now) {
        int x = (int) Math.floor(at.x()), z = (int) Math.floor(at.z());
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Seen seen = columns.get(key);
        if (seen == null || now - seen.at() > KEPT_MILLIS) {
            // The ground, read through a tree's canopy: live (field598), Ned under an oak 13 high read as down the mine.
            seen = new Seen(TerrainSurveyor.ground(world, x, z, Keep.SKY_TOP), now);
            columns.put(key, seen);
            if (columns.size() > 4096) {
                columns.values().removeIf(s -> now - s.at() > KEPT_MILLIS);
            }
        }
        return seen.top().map(top -> top.y() > at.y() + depth).orElse(false);
    }
}
