package io.github.jayessdeesea.minecraft.bot;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.nav.FallAllowance;
import io.github.jayessdeesea.minecraft.bot.nav.Goal;
import io.github.jayessdeesea.minecraft.bot.nav.Pathfinder;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Condition;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import io.github.jayessdeesea.minecraft.command.Soldier;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.*;

/**
 * The soldier's body against the world, pure: whether it fits a cell, what hazard or solid block is under or beside its
 * footprint, a drop at its feet, the cells cut to step up, the surface, and what it can see.
 */
final class Body {

    private Body() {
    }

    /**
     * The nearest standing cell within {@link DeterministicSoldier#COVER_RADIUS} of {@code at} that {@code shooter} can't see - nothing
     * solid between its eyes and the soldier's - if there's one.
     */
    static Optional<Position> coverFrom(WorldView world, Position at, Position shooter) {
        Position shooterEye = new Position(shooter.x(), shooter.y() + EYE_HEIGHT, shooter.z());
        BlockPos here = BlockPos.containing(at.x(), at.y() + EPSILON, at.z());
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -COVER_RADIUS; dx <= COVER_RADIUS; dx++) {
            for (int dz = -COVER_RADIUS; dz <= COVER_RADIUS; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos cell = here.offset(dx, dy, dz);
                    if (!Pathfinder.standable(world, cell)) {
                        continue;
                    }
                    Position spot = new Position(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
                    double distance = Math.hypot(spot.x() - at.x(), spot.z() - at.z());
                    if (distance < bestDistance
                            && !lineOfSight(world, shooterEye, new Position(spot.x(), spot.y() + EYE_HEIGHT, spot.z()))
                            && !lineOfSight(world, shooterEye, new Position(spot.x(), spot.y() + 0.5, spot.z()))) {
                        best = spot;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether no solid block lies on the straight line from {@code from} to {@code to}. */
    static boolean lineOfSight(WorldView world, Position from, Position to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(length / SIGHT_STEP);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            if (world.isSolid((int) Math.floor(from.x() + dx * t), (int) Math.floor(from.y() + dy * t),
                    (int) Math.floor(from.z() + dz * t))) {
                return false;
            }
        }
        return true;
    }

    /** What's still in the way of a step up from {@code here} onto {@code next}: the cells {@link Pathfinder#stepCut} plans to dig. */
    static List<BlockPos> toCut(WorldView world, BlockPos here, BlockPos next, boolean pickaxe) {
        if (next.y() != here.y() + 1 || Math.abs(next.x() - here.x()) + Math.abs(next.z() - here.z()) != 1) {
            return List.of();
        }
        List<BlockPos> solid = Stream.of(next, next.offset(0, 1, 0), here.offset(0, 2, 0))
                .filter(c -> world.isSolid(c.x(), c.y(), c.z())).toList();
        // Only soil, and stone with a pickaxe, is ever dug: anything else put there since the plan - a
        // wall, say - is left be, and the soldier re-plans round it once it's stuck.
        return solid.stream().allMatch(c -> world.isSoil(c.x(), c.y(), c.z()) || pickaxe && world.isStone(c.x(), c.y(), c.z()))
                ? solid : List.of();
    }

    /** Whether {@code cell} is on its column's surface, not under the ground. */
    static boolean onTheSurface(WorldView world, BlockPos cell) {
        return io.github.jayessdeesea.minecraft.bot.nav.TerrainSurveyor.surface(world, cell.x(), cell.z(),
                        io.github.jayessdeesea.minecraft.command.Keep.SKY_TOP)
                .map(top -> top.y() <= cell.y() + UNDER_THE_SURFACE).orElse(true);
    }

    /** Room for the whole body, and no hazard but water (lava, fire) to step into - including when retreating. */
    static boolean bodyFits(WorldView world, double x, double y, double z) {
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + BODY_HEIGHT - EPSILON);
        return !anySolid(world, x, z, minY, maxY) && !anyHazard(world, x, z, minY, maxY, false);
    }

    /** Whether any block in rows {@code minY..maxY} under the body's 0.6-wide footprint at (x, z) is solid. */
    static boolean anySolid(WorldView world, double x, double z, int minY, int maxY) {
        io.github.jayessdeesea.minecraft.bot.nav.Footprint under = io.github.jayessdeesea.minecraft.bot.nav.Footprint.of(x, z);
        for (int bx = under.minX(); bx <= under.maxX(); bx++) {
            for (int bz = under.minZ(); bz <= under.maxZ(); bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isSolid(bx, by, bz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether nothing - no ground, no water - lies under {@code at} within {@link #FIGHT_DROP} blocks of its feet. */
    static boolean overDrop(WorldView world, Position at) {
        int feet = (int) Math.floor(at.y() + EPSILON);
        for (int y = feet - 1; y >= feet - 1 - FIGHT_DROP; y--) {
            if (anySolid(world, at.x(), at.z(), y, y) || world.isWater((int) Math.floor(at.x()), y, (int) Math.floor(at.z()))) {
                return false;
            }
        }
        return true;
    }

    static boolean anyHazard(WorldView world, double x, double z, int minY, int maxY) {
        return anyHazard(world, x, z, minY, maxY, true);
    }

    /** Whether any block in rows {@code minY..maxY} under the body's footprint is a hazard - water only if {@code water}. */
    static boolean anyHazard(WorldView world, double x, double z, int minY, int maxY, boolean water) {
        io.github.jayessdeesea.minecraft.bot.nav.Footprint under = io.github.jayessdeesea.minecraft.bot.nav.Footprint.of(x, z);
        for (int bx = under.minX(); bx <= under.maxX(); bx++) {
            for (int bz = under.minZ(); bz <= under.maxZ(); bz++) {
                for (int by = minY; by <= maxY; by++) {
                    if (world.isHazard(bx, by, bz) && (water || !world.isWater(bx, by, bz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
