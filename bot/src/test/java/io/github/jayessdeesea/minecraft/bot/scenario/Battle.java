package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * A {@link SyntheticWorld} with simulated hostiles in it. Terrain comes from the world; the
 * hostiles are stepped every tick by {@link #step} and answer {@link #nearbyHostiles}, the same
 * seam the live {@code EntityTracker} fills.
 *
 * <p>Deliberately approximate, not vanilla mob AI (AGENTS.md's framing: the subject
 * is coordination, so the simulation only has to make a soldier's choices matter). The numbers
 * that come from the server jar: zombie attack damage 3, movement speed attributes zombie 0.23,
 * skeleton and creeper 0.25 ({@code createAttributes}), and bow range 15 with a standoff from
 * about 11 to 15 blocks ({@code RangedBowAttackGoal}). Speeds here are scaled from live runs,
 * where zombies chased at about a soldier's walking speed (0.2 blocks per tick). Everything else -
 * reach, cooldowns, knockback, bow range, aim and damage, creeper fuse and blast - is an estimate.
 */
public final class Battle implements WorldView {

    /** Hostile archetypes, named for the vanilla mob each approximates. */
    public enum Archetype {
        /** Closes to contact at walking speed and hits. */
        ZOMBIE(0.2, 20),
        /** Keeps a standoff distance and shoots while it can. */
        SKELETON(0.217, 20),
        /** Closes in, hisses, explodes. */
        CREEPER(0.217, 20);

        final double speed;
        final double health;

        Archetype(double speed, double health) {
            this.speed = speed;
            this.health = health;
        }
    }

    static final double ZOMBIE_REACH = 1.5;
    static final double ZOMBIE_DAMAGE = 3;
    static final int ZOMBIE_COOLDOWN = 20;
    static final double BOW_RANGE = 15;
    /** Holds between 0.75 x 15 and 15 blocks, as {@code RangedBowAttackGoal} strafes (server jar). */
    static final double SKELETON_STANDOFF = 13;
    static final double ARROW_DAMAGE = 3;
    static final int BOW_COOLDOWN = 40;
    static final double CREEPER_TRIGGER = 3;
    static final double CREEPER_ABORT = 7;
    static final int CREEPER_FUSE = 30;
    static final double BLAST_DAMAGE = 22;
    static final double BLAST_RADIUS = 6;
    /** How far a punch shoves a hostile back - vanilla's base knockback, roughly. */
    static final double KNOCKBACK = 0.9;
    /**
     * An arrow flies at 1.6 blocks a tick ({@code AbstractSkeleton.performRangedAttack}) toward
     * where the soldier stood when it was loosed - skeletons don't lead their target - with a little
     * spread. It hits if the soldier is still that close to its line when it arrives: closing
     * straight in stays on the line; moving across it doesn't.
     */
    static final double ARROW_SPEED = 1.6;
    /** Half a body's width plus the arrow's own. */
    static final double ARROW_HIT_RADIUS = 0.55;
    /**
     * Aim spread, radians, one standard deviation: vanilla adds a gaussian times 0.0075 x inaccuracy
     * to each axis of the shot, and a skeleton's inaccuracy is 14 - 4 x difficulty - 6 on normal.
     */
    static final double ARROW_SPREAD = 0.0075 * 6;

    /** An arrow in flight at soldier {@code target}: from (ox, oz) along the line through (ax, az). */
    private record Arrow(int target, double ox, double oz, double ax, double az, int ticksLeft) {
    }

    private List<Arrow> arrows = new ArrayList<>();

    /** One simulated hostile; mutable, stepped only by its battle. */
    public static final class Hostile {
        final int id;
        final Archetype archetype;
        double x;
        double z;
        double health;
        int cooldown;
        int fuse = -1;
        boolean exploded;

        Hostile(int id, Archetype archetype, double x, double z) {
            this.id = id;
            this.archetype = archetype;
            this.x = x;
            this.z = z;
            this.health = archetype.health;
        }

        public boolean alive() {
            return health > 0;
        }
    }

    private final SyntheticWorld world;
    private final Random random;
    private final List<Hostile> hostiles = new ArrayList<>();

    public Battle(SyntheticWorld world, long seed) {
        this.world = world;
        this.random = new Random(seed);
    }

    public Hostile spawn(Archetype archetype, double x, double z) {
        Hostile hostile = new Hostile(1000 + hostiles.size(), archetype, x, z);
        hostiles.add(hostile);
        return hostile;
    }

    public List<Hostile> hostiles() {
        return hostiles;
    }

    private Position positionOf(Hostile h) {
        int bx = (int) Math.floor(h.x);
        int bz = (int) Math.floor(h.z);
        return new Position(h.x, world.ground(bx, bz) + 1, h.z);
    }

    /** A bare-handed blow on {@code entityId}, if that hostile is alive and within {@code reach}. */
    public void strike(int entityId, Position soldier, double reach) {
        strike(entityId, soldier, reach, WeaponStats.HAND.damage());
    }

    /**
     * A blow of {@code damage} on {@code entityId}, if that hostile is alive and within {@code reach} -
     * a full-charge hit, since the soldier swings only once its weapon has recharged.
     */
    public void strike(int entityId, Position soldier, double reach, double damage) {
        for (Hostile h : hostiles) {
            if (h.id == entityId && h.alive() && horizontal(positionOf(h), soldier) <= reach) {
                h.health -= damage;
                move(h, soldier, -KNOCKBACK);
            }
        }
    }

    /**
     * Advances every live hostile one tick against the soldier at {@code soldier} and returns the
     * damage it dealt him this tick.
     */
    public double step(Position soldier) {
        return step(List.of(soldier))[0];
    }

    /** A shield must be up this long before it blocks ({@code BlocksAttacks}' block delay). */
    static final int SHIELD_DELAY_TICKS = 5;

    /**
     * {@link #step(Position)} for a soldier whose shield, if {@code shieldFacing} is present, has
     * been up {@link #SHIELD_DELAY_TICKS} or more, facing that yaw: an arrow from within 90 degrees
     * of where it faces is stopped.
     */
    public double step(Position soldier, OptionalDouble shieldFacing) {
        if (shieldFacing.isPresent()) {
            double yaw = Math.toRadians(shieldFacing.getAsDouble());
            double lookX = -Math.sin(yaw);
            double lookZ = Math.cos(yaw);
            arrows.removeIf(a -> a.ticksLeft() <= 1 && (a.ox() - soldier.x()) * lookX + (a.oz() - soldier.z()) * lookZ > 0
                    && offLine(a, soldier) <= ARROW_HIT_RADIUS && ++blocked > 0);
        }
        return step(soldier);
    }

    /** Arrows a shield has stopped. */
    int blocked;

    @Override
    public List<Position> arrowsNear(Position center, double radius) {
        List<Position> near = new ArrayList<>();
        for (Arrow a : arrows) {
            double lx = a.ax() - a.ox();
            double lz = a.az() - a.oz();
            double length = Math.max(1e-9, Math.hypot(lx, lz));
            double back = Math.min(length, a.ticksLeft() * ARROW_SPEED);
            Position at = new Position(a.ax() - lx / length * back, center.y() + 1, a.az() - lz / length * back);
            if (horizontal(at, center) <= radius) {
                near.add(at);
            }
        }
        near.sort(Comparator.comparingDouble(p -> horizontal(p, center)));
        return near;
    }

    /**
     * Advances every live hostile one tick, each against the nearest of {@code soldiers}, and
     * returns the damage each soldier took this tick (a creeper's blast hits everyone near it).
     */
    public double[] step(List<Position> soldiers) {
        double[] damage = new double[soldiers.size()];
        List<Arrow> flying = new ArrayList<>();
        for (Arrow arrow : arrows) {
            if (arrow.ticksLeft() > 1) {
                flying.add(new Arrow(arrow.target(), arrow.ox(), arrow.oz(), arrow.ax(), arrow.az(), arrow.ticksLeft() - 1));
            } else if (arrow.target() < soldiers.size() && offLine(arrow, soldiers.get(arrow.target())) <= ARROW_HIT_RADIUS) {
                damage[arrow.target()] += ARROW_DAMAGE;
            }
        }
        arrows = flying;
        for (Hostile h : hostiles) {
            if (!h.alive() || soldiers.isEmpty()) {
                continue;
            }
            if (h.cooldown > 0) {
                h.cooldown--;
            }
            Position here = positionOf(h);
            int target = 0;
            for (int i = 1; i < soldiers.size(); i++) {
                if (horizontal(here, soldiers.get(i)) < horizontal(here, soldiers.get(target))) {
                    target = i;
                }
            }
            Position soldier = soldiers.get(target);
            double distance = horizontal(here, soldier);
            switch (h.archetype) {
                case ZOMBIE -> {
                    if (distance > ZOMBIE_REACH * 0.8) {
                        move(h, soldier, h.archetype.speed);
                    }
                    if (distance <= ZOMBIE_REACH && h.cooldown == 0) {
                        damage[target] += ZOMBIE_DAMAGE;
                        h.cooldown = ZOMBIE_COOLDOWN;
                    }
                }
                case SKELETON -> {
                    // Drawing a bow slows it to half speed while the target is in range.
                    double speed = distance <= BOW_RANGE ? h.archetype.speed / 2 : h.archetype.speed;
                    if (distance > SKELETON_STANDOFF + 2) {
                        move(h, soldier, speed);
                    } else if (distance < SKELETON_STANDOFF - 2) {
                        move(h, soldier, -speed);
                    }
                    if (distance <= BOW_RANGE && h.cooldown == 0 && canSee(here, soldier)) {
                        double ax = soldier.x() + random.nextGaussian() * ARROW_SPREAD * distance;
                        double az = soldier.z() + random.nextGaussian() * ARROW_SPREAD * distance;
                        arrows.add(new Arrow(target, h.x, h.z, ax, az, (int) Math.ceil(distance / ARROW_SPEED)));
                        h.cooldown = BOW_COOLDOWN;
                    }
                }
                case CREEPER -> {
                    if (h.fuse >= 0) {
                        if (distance > CREEPER_ABORT) {
                            h.fuse = -1;
                        } else if (++h.fuse >= CREEPER_FUSE) {
                            for (int i = 0; i < soldiers.size(); i++) {
                                damage[i] += Math.max(0, BLAST_DAMAGE * (1 - horizontal(here, soldiers.get(i)) / BLAST_RADIUS));
                            }
                            h.health = 0;
                            h.exploded = true;
                        }
                    } else if (distance <= CREEPER_TRIGGER) {
                        h.fuse = 0;
                    } else {
                        move(h, soldier, h.archetype.speed);
                    }
                }
            }
        }
        return damage;
    }

    /** How far {@code soldier} stands from the arrow's line, horizontally. */
    private static double offLine(Arrow arrow, Position soldier) {
        double lx = arrow.ax() - arrow.ox();
        double lz = arrow.az() - arrow.oz();
        double length = Math.hypot(lx, lz);
        if (length < 1e-9) {
            return Math.hypot(soldier.x() - arrow.ax(), soldier.z() - arrow.az());
        }
        return Math.abs((soldier.x() - arrow.ox()) * lz - (soldier.z() - arrow.oz()) * lx) / length;
    }

    /** Whether nothing solid lies between the two at chest height - a skeleton shoots only what it sees. */
    private boolean canSee(Position from, Position to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.2);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            if (world.isSolid((int) Math.floor(from.x() + dx * t), (int) Math.floor(from.y() + 1.5 + dy * t),
                    (int) Math.floor(from.z() + dz * t))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Step toward (or, with negative speed, away from) the soldier, sliding along an obstacle one
     * axis at a time when the straight step is blocked - a stand-in for mob pathfinding around
     * rocks and trunks, not around walls.
     */
    private void move(Hostile h, Position target, double speed) {
        double dx = target.x() - h.x;
        double dz = target.z() - h.z;
        double length = Math.hypot(dx, dz);
        if (length < 1e-9) {
            return;
        }
        double sx = dx / length * speed;
        double sz = dz / length * speed;
        if (!tryStep(h, h.x + sx, h.z + sz)) {
            double slide = Math.abs(speed);
            boolean xFirst = Math.abs(sx) >= Math.abs(sz);
            double ax = Math.signum(sx) * slide;
            double az = Math.signum(sz) * slide;
            if (!(xFirst ? tryStep(h, h.x + ax, h.z) || tryStep(h, h.x, h.z + (az == 0 ? slide : az))
                    : tryStep(h, h.x, h.z + az) || tryStep(h, h.x + (ax == 0 ? slide : ax), h.z))) {
                tryStep(h, h.x, h.z - (az == 0 ? slide : az));
            }
        }
    }

    private boolean tryStep(Hostile h, double nx, double nz) {
        int bx = (int) Math.floor(nx);
        int bz = (int) Math.floor(nz);
        int here = world.ground((int) Math.floor(h.x), (int) Math.floor(h.z));
        if (!SyntheticWorld.inBounds(bx, bz) || world.ground(bx, bz) > here + 1
                || world.isSolid(bx, world.ground(bx, bz) + 1, bz) || world.isHazard(bx, world.ground(bx, bz) + 1, bz)) {
            return false;
        }
        h.x = nx;
        h.z = nz;
        return true;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return world.isSolid(x, y, z);
    }

    @Override
    public boolean isHazard(int x, int y, int z) {
        return world.isHazard(x, y, z);
    }

    @Override
    public List<TrackedEntity> nearbyHostiles(Position center, double radius) {
        return hostiles.stream().filter(Hostile::alive)
                .map(h -> new TrackedEntity(h.id, HostileMobRegistry.typeIdOf(h.archetype.name().toLowerCase(Locale.ROOT)), positionOf(h)))
                .filter(e -> distance3(e.position(), center) <= radius)
                .sorted(Comparator.comparingDouble(e -> distance3(e.position(), center)))
                .toList();
    }

    private static double horizontal(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private static double distance3(Position a, Position b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }
}
