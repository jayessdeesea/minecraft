package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A soldier picking up what lies near it once nothing threatens it: a spider's string, a
 * chicken's feather, a dead squadmate's kit. The server gives a dropped stack to whoever walks onto
 * it, so this only walks there. A stack it can't reach in {@link #TRY_TICKS} is left for a while -
 * across water, on a ledge - so it isn't walked at forever.
 */
public final class Scavenger {

    private static final Logger LOG = LogManager.getLogger(Scavenger.class);

    /** How far a soldier goes for something lying on the ground. */
    public static final double RADIUS = 6;
    static final int TRY_TICKS = 100;
    /** How long a stack it couldn't reach is left before it tries again. */
    static final int LEAVE_TICKS = 1200;

    private final String name;
    private Position going;
    private int tryTicks;
    private int ticks;
    private final Map<Position, Integer> left = new HashMap<>();

    public Scavenger(String name) {
        this.name = name;
    }

    public synchronized boolean active() {
        return going != null;
    }

    public synchronized Optional<Position> standAt() {
        return Optional.ofNullable(going);
    }

    /** Each tick, while {@code wanted} (free, nothing near): walks onto the nearest stack it hasn't given up on. */
    public synchronized void tick(Position soldier, WorldView world, boolean wanted) {
        ticks++;
        left.values().removeIf(until -> until <= ticks);
        if (!wanted) {
            going = null;
            return;
        }
        Optional<Position> nearest = world.itemsNear(soldier, RADIUS).stream()
                .filter(item -> left.keySet().stream().noneMatch(l -> distance(l, item) < 1)).findFirst();
        if (nearest.isEmpty()) {
            going = null;
            return;
        }
        if (going == null || distance(going, nearest.get()) >= 1) {
            going = nearest.get();
            tryTicks = 0;
        }
        if (++tryTicks > TRY_TICKS) {
            LOG.info("{} leaves what lies at {}: out of reach", name, going);
            left.put(going, ticks + LEAVE_TICKS);
            going = null;
        }
    }

    private static double distance(Position a, Position b) {
        return Math.sqrt(Math.pow(a.x() - b.x(), 2) + Math.pow(a.y() - b.y(), 2) + Math.pow(a.z() - b.z(), 2));
    }
}
