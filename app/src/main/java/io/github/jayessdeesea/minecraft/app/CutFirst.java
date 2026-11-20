package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Dirt from ground that has to go anyway (operator, 2026-11-20: take the blocks that have to go anyway): wanted near a
 * site, it's fetched from the nearest of the camp's ground over its level, not from a pit opened beside the site - the
 * pits beside every hut and wall left the camp holed all over. Pure.
 */
final class CutFirst {

    /** How far from where it's wanted dirt is fetched from ground to go, rather than dug beside the site. */
    static final double REACH = 24;

    private CutFirst() {
    }

    /** Where to dig dirt wanted near {@code near}: over the nearest of {@code cut} within {@link #REACH}, if any. */
    static Optional<Position> near(List<BlockPos> cut, Position near) {
        return cut.stream().filter(c -> Math.hypot(c.x() + 0.5 - near.x(), c.z() + 0.5 - near.z()) <= REACH)
                .min(Comparator.comparingDouble(c -> Math.hypot(c.x() + 0.5 - near.x(), c.z() + 0.5 - near.z())))
                .map(c -> new Position(c.x() + 0.5, c.y() + 1, c.z() + 0.5));
    }
}
