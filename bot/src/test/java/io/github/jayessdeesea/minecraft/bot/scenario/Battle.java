package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A {@link SyntheticWorld} with simulated hostiles in it. Terrain comes from the world; the
 * hostiles are stepped every tick by {@link #step} and answer {@link #nearbyHostiles}, the same
 * seam the live {@code EntityTracker} fills.
 *
 * <p>Deliberately approximate, not vanilla mob AI (AGENTS.md's framing: the subject
 * is coordination, so the simulation only has to make a soldier's choices matter). The numbers
 * that come from the server jar ({@code createAttributes}): zombie attack damage 3, and movement
 * speed attributes zombie 0.23, skeleton and creeper 0.25. Speeds here are scaled from live runs,
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
    static final double SKELETON_STANDOFF = 8;
    static final double ARROW_DAMAGE = 3;
    static final int BOW_COOLDOWN = 40;
    static final double CREEPER_TRIGGER = 3;
    static final double CREEPER_ABORT = 7;
    static final int CREEPER_FUSE = 30;
    static final double BLAST_DAMAGE = 22;
    static final double BLAST_RADIUS = 6;
    /** A bare fist. */
    public static final double FIST_DAMAGE = 1;
    /** How far a punch shoves a hostile back - vanilla's base knockback, roughly. */
    static final double KNOCKBACK = 0.9;
    /** A skeleton's arrows miss a moving target about half the time. */
    static final double ARROW_HIT_CHANCE = 0.5;

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
    private final java.util.Random random;
    private final List<Hostile> hostiles = new ArrayList<>();

    public Battle(SyntheticWorld world, long seed) {
        this.world = world;
        this.random = new java.util.Random(seed);
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

    /** The soldier's fist lands on {@code entityId}, if that hostile is alive and within {@code reach}. */
    public void strike(int entityId, Position soldier, double reach) {
        for (Hostile h : hostiles) {
            if (h.id == entityId && h.alive() && horizontal(positionOf(h), soldier) <= reach) {
                h.health -= FIST_DAMAGE;
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

    /**
     * Advances every live hostile one tick, each against the nearest of {@code soldiers}, and
     * returns the damage each soldier took this tick (a creeper's blast hits everyone near it).
     */
    public double[] step(List<Position> soldiers) {
        double[] damage = new double[soldiers.size()];
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
                    if (distance <= BOW_RANGE && h.cooldown == 0) {
                        damage[target] += random.nextDouble() < ARROW_HIT_CHANCE ? ARROW_DAMAGE : 0;
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
                .map(h -> new TrackedEntity(h.id, HostileMobRegistry.typeIdOf(h.archetype.name().toLowerCase(java.util.Locale.ROOT)), positionOf(h)))
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
