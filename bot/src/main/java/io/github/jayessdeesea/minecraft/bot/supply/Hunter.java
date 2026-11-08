package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * One hungry soldier with nothing to eat killing an animal for meat (docs/military-doctrine.md §11,
 * pre-combat checks): the nearest cow, pig, sheep, chicken or rabbit within {@link #SEARCH_RADIUS}.
 * It walks at it, strikes it from arm's length each time the weapon in hand is charged, and once it
 * dies walks over where it fell for the meat. Raw meat is a ration, eaten as any other.
 *
 * <p>An animal runs when hit, so this is chase, not a script: each tick it re-reads where the
 * animal is. One out of sight for {@link #LOST_TICKS} is let go. The swing goes out through the
 * soldier's connection; this only says when and at whom.
 */
public final class Hunter {

    private static final Logger LOG = LogManager.getLogger(Hunter.class);

    /** How far a soldier looks for game. */
    public static final double SEARCH_RADIUS = 24;
    /** Ticks without sight of the quarry before it's given up. */
    static final int LOST_TICKS = 100;
    /** Ticks spent standing where the quarry fell, for its drops to be picked up. */
    static final int PICKUP_TICKS = 40;
    /** Ticks a hunt may take, all told, before the soldier stops chasing. */
    static final int HUNT_TICKS = 1200;

    private final String name;
    private Integer quarry;
    private Position lastSeen;
    private int unseenTicks;
    private int pickupTicks;
    private int huntTicks;
    private int chargeTicks;

    public Hunter(String name) {
        this.name = name;
    }

    /** Whether there's game within {@link #SEARCH_RADIUS} of {@code soldier} to hunt. */
    public static boolean gameInSight(WorldView world, Position soldier) {
        return !world.gameNear(soldier, SEARCH_RADIUS).isEmpty();
    }

    public synchronized boolean active() {
        return quarry != null || pickupTicks > 0;
    }

    /** Where the soldier should be: at the quarry, or where it fell. */
    public synchronized Optional<Position> standAt() {
        return active() ? Optional.ofNullable(lastSeen) : Optional.empty();
    }

    /**
     * Each tick, while {@code wanted} (hungry, nothing to eat, and free to go): the entity to strike
     * this tick, if any - the caller sends it. {@code charge} is how many ticks the weapon in hand
     * takes to charge.
     */
    public synchronized OptionalInt tick(Position soldier, WorldView world, boolean wanted, int charge) {
        if (chargeTicks > 0) {
            chargeTicks--;
        }
        if (!wanted) {
            stop();
            return OptionalInt.empty();
        }
        if (pickupTicks > 0) {
            pickupTicks--;
            return OptionalInt.empty();
        }
        if (quarry == null) {
            Optional<TrackedEntity> nearest = world.gameNear(soldier, SEARCH_RADIUS).stream().findFirst();
            if (nearest.isEmpty()) {
                return OptionalInt.empty();
            }
            quarry = nearest.get().entityId();
            lastSeen = nearest.get().position();
            unseenTicks = 0;
            huntTicks = 0;
            LOG.info("{} hunts the animal at {} for food", name, lastSeen);
        }
        Optional<TrackedEntity> seen = world.gameNear(soldier, SEARCH_RADIUS * 2).stream()
                .filter(e -> e.entityId() == quarry).findFirst();
        if (seen.isEmpty()) {
            // Gone from the tracker: killed, most likely, if it was within reach last we saw it.
            if (++unseenTicks > LOST_TICKS || distance(soldier, lastSeen) <= DeterministicSoldier.ATTACK_REACH + 1) {
                LOG.info("{} {} the animal it hunted at {}", name, unseenTicks > LOST_TICKS ? "lost" : "killed", lastSeen);
                quarry = null;
                pickupTicks = PICKUP_TICKS;
            }
            return OptionalInt.empty();
        }
        unseenTicks = 0;
        lastSeen = seen.get().position();
        if (++huntTicks > HUNT_TICKS) {
            LOG.info("{} gives up the chase at {}", name, lastSeen);
            stop();
            return OptionalInt.empty();
        }
        if (distance(soldier, lastSeen) <= DeterministicSoldier.ATTACK_REACH && chargeTicks == 0) {
            chargeTicks = charge;
            return OptionalInt.of(quarry);
        }
        return OptionalInt.empty();
    }

    private static double distance(Position a, Position b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }

    /** Stops the hunt under way. */
    public synchronized void stop() {
        quarry = null;
        lastSeen = null;
        pickupTicks = 0;
    }
}
