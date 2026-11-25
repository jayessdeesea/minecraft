package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.EntityTracker;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The simulated server's animals, as the pinned server has them where the force's work meets them: each stands where
 * it spawned till a player within {@link #TEMPT_RANGE} holds its kind's food, then walks after that player, stopping
 * {@link #STOP} short. It walks level ground and a block up or down, never into water and never off a drop: an animal
 * is not led across a pond.
 */
final class SimAnimals {

    /** How far off an animal sees its food in a player's hand. */
    static final double TEMPT_RANGE = 10;
    /** How near the player it comes to a stand. */
    static final double STOP = 2.5;
    /** Blocks a tick a led animal walks: a little over a player's walk, well under its sprint. */
    static final double STEP = 0.25;
    /** The cow, in protocol 777's registry. */
    static final int COW_TYPE_ID = 30;

    /** A player as an animal sees it: where it stands and what is in its hand. */
    record Tempter(double x, double y, double z, Optional<ItemClass> held) {
    }

    /** Each animal by entity ID: x, y, z, health, type. */
    private final Map<Integer, double[]> animals = new LinkedHashMap<>();
    private final SimServer.Terrain blocks;

    SimAnimals(SimServer.Terrain blocks) {
        this.blocks = blocks;
    }

    /** The food that leads an animal of {@code type}, if it is one that is led. */
    static Optional<ItemClass> food(int type) {
        return type == EntityTracker.SHEEP_TYPE_ID || type == COW_TYPE_ID ? Optional.of(ItemClass.WHEAT)
                : type == EntityTracker.CHICKEN_TYPE_ID ? Optional.of(ItemClass.SEEDS) : Optional.empty();
    }

    void spawn(int id, int type, double x, double y, double z, double health) {
        animals.put(id, new double[]{x, y, z, health, type});
    }

    /** The animal {@code id}'s x, y, z, health and type, live - or null if there is none. */
    double[] at(int id) {
        return animals.get(id);
    }

    /** Every animal as a connection tracks it: its entity ID, type and place. */
    List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> tracked() {
        return animals.entrySet().stream().map(e -> new io.github.jayessdeesea.minecraft.bot.world.TrackedEntity(e.getKey(), (int) e.getValue()[4],
                new io.github.jayessdeesea.minecraft.command.Position(e.getValue()[0], e.getValue()[1], e.getValue()[2]))).toList();
    }

    /** What a blow at an entity came to. */
    enum Hit { NONE, OUT_OF_REACH, HURT, KILLED }

    /** A blow of {@code damage} at {@code id} from a player at ({@code x}, {@code z}): none of these, out of reach past 3.5, hurt, or killed and gone. */
    Hit hit(int id, double x, double z, double damage) {
        double[] a = animals.get(id);
        if (a == null) {
            return Hit.NONE;
        }
        if (Math.hypot(a[0] - x, a[2] - z) > 3.5) {
            return Hit.OUT_OF_REACH;
        }
        a[3] -= damage;
        if (a[3] > 0) {
            return Hit.HURT;
        }
        if ((int) a[4] != EntityTracker.SHEEP_TYPE_ID) {
            throw new IllegalStateException("the kill of an animal of type " + (int) a[4] + " isn't modelled: only a sheep's drops are");
        }
        animals.remove(id);
        return Hit.KILLED;
    }

    /** One tick: each animal a step after the nearest of {@code tempters} holding its food, {@code moved} told of each that moved. */
    void follow(List<Tempter> tempters, BiConsumer<Integer, double[]> moved) {
        animals.forEach((id, a) -> {
            Optional<ItemClass> food = food((int) a[4]);
            Optional<Tempter> leader = tempters.stream().filter(t -> food.isPresent() && t.held().equals(food))
                    .filter(t -> Math.hypot(t.x() - a[0], t.z() - a[2]) <= TEMPT_RANGE && Math.abs(t.y() - a[1]) <= TEMPT_RANGE)
                    .min(Comparator.comparingDouble(t -> Math.hypot(t.x() - a[0], t.z() - a[2])));
            if (leader.isEmpty()) {
                return;
            }
            double dx = leader.get().x() - a[0], dz = leader.get().z() - a[2], d = Math.hypot(dx, dz);
            if (d <= STOP) {
                return;
            }
            double nx = a[0] + dx / d * STEP, nz = a[2] + dz / d * STEP;
            int cx = (int) Math.floor(nx), cz = (int) Math.floor(nz), feet = (int) Math.floor(a[1]);
            for (int y : new int[]{feet, feet + 1, feet - 1}) {
                if (stands(cx, y, cz)) {
                    a[0] = nx;
                    a[1] = y;
                    a[2] = nz;
                    moved.accept(id, a);
                    return;
                }
            }
        });
    }

    /** Whether an animal can stand with its feet in the cell: room for it, and ground - not water - under it. */
    private boolean stands(int x, int y, int z) {
        int under = blocks.at(x, y - 1, z);
        return blocks.at(x, y, z) == SimServer.AIR && blocks.at(x, y + 1, z) == SimServer.AIR && under != SimServer.AIR && under != SimServer.WATER;
    }
}
