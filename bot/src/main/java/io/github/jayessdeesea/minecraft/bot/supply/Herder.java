package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ScoutSectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier scouting its ground for animals of a kind and leading them back: it walks its sector's legs looking,
 * goes up to the ones it sees there with their food in its hand, and walks to where they're wanted at the pace they
 * keep - an animal follows the food only from within {@link Livestock#TEMPT_RANGE}, so it waits for one that lags and
 * goes back for one that has lost it. It says where the land ends: an animal isn't led over water, and the squad's
 * leaders are told where that is. Each tick it says where to walk and whether the food is in hand; the walking is
 * the soldier's.
 *
 * <p>Every part of it has its most time (docs/conventions.md, "A retry changes something"): a leg not reached is
 * passed over, an animal not got to or gone back for too often is left, a walk back that gains no ground ends where
 * it stands - each said, none tried for good.
 */
public final class Herder {

    private static final Logger LOG = LogManager.getLogger(Herder.class);

    /** How far off it looks for the kind: what a soldier's connection tracks of animals round it. */
    static final double SIGHT = 32;
    /** How near an animal it goes to be seen with the food: well inside the range it's seen from. */
    static final double NEAR = 6;
    /** How far behind a led animal may fall before the soldier waits for it: inside the range it follows from. */
    static final double LAG = 7;
    /** How near where they're wanted the soldier stands for them to be brought. */
    static final double ARRIVED = 2;
    /** How far above or below the soldier an animal counts: one in a ravine under it is a long way on foot (Hunter). */
    static final double VERTICAL_REACH = 4;
    /** How far an animal has to have come from where it was first seen to have been led. */
    static final double FOLLOWED = 3;
    /** How far ahead on a walk it looks for water (to an animal: all the way), and how near a place already said another land's end is the same one. */
    static final int WATER_AHEAD = 3, SAME_SHORE = 8;
    /** Ticks it waits on a led animal that comes no nearer before going on without it: one held by ground it can't cross. */
    static final int WAIT_TICKS = 200;
    /** Ticks, all told, it walks at one animal before leaving it; and times it goes back for one that lost the food. */
    static final int APPROACH_TICKS = 600, GONE_BACK_FOR = 3;
    /** Ticks a block of a walk is given, and ticks over: a leg or the way back gaining no ground past these is given up. */
    static final int TICKS_A_BLOCK = 15, TICKS_OVER = 200;

    public enum Phase {
        /** Walking its ground, none of the kind in sight. */
        SEARCH,
        /** Going up to one it sees, the food in hand. */
        APPROACH,
        /** Walking them to where they're wanted. */
        LEAD,
        /** Ended: there with what followed, or its ground walked and none found, or the way back given up. */
        DONE
    }

    /**
     * This tick's answer.
     *
     * @param walkTo    where to walk, or empty to stand
     * @param holdsFood whether the kind's food is to be in hand - not once it's done: put away, they stay where they were brought
     * @param led       the animals of the kind following now
     * @param landEnds  where water was met, the first tick that shore is: for the squad leader to say up
     */
    public record Step(Phase phase, Optional<Position> walkTo, boolean holdsFood, int led, Optional<Position> landEnds) {
    }

    private final String name;
    private final Livestock kind;
    private final ScoutSectors.Sector sector;
    private final List<Position> legs;
    private final Position bringTo;
    private Phase phase = Phase.SEARCH;
    private int leg;
    /** Ticks on the walk under way - a leg, or the way back - and the nearest it has come to its end. */
    private int walkTicks;
    private double walkNearest = Double.MAX_VALUE;
    /** Where each animal of its own was first seen: an animal first seen in another's sector is the other's. */
    private final Map<Integer, Position> firstSeen = new HashMap<>();
    /** Animals left: not got to, gone back for too often, or held back by ground they don't cross. */
    private final Set<Integer> left = new HashSet<>();
    private final Map<Integer, Integer> approachTicks = new HashMap<>(), goneBackFor = new HashMap<>();
    private final Set<Integer> picked = new HashSet<>();
    private final List<Position> shores = new ArrayList<>();
    private int waitTicks;
    private int brought;

    /** A soldier looking for {@code kind} over {@code sector} of the ground round {@code place}, to bring them to {@code bringTo}. */
    public Herder(String name, Livestock kind, ScoutSectors.Sector sector, Position place, Position bringTo) {
        this.name = name;
        this.kind = kind;
        this.sector = sector;
        this.legs = sector.legs(place);
        this.bringTo = bringTo;
    }

    /** How many it had following at any time, and how many of those were with it where they were wanted: a shortfall is a miss to dig into. */
    public synchronized int picked() {
        return picked.size();
    }

    public synchronized int brought() {
        return brought;
    }

    /** One tick, the soldier at {@code soldier}. */
    public synchronized Step tick(Position soldier, WorldView world) {
        if (phase == Phase.DONE) {
            return new Step(phase, Optional.empty(), false, brought, Optional.empty());
        }
        List<TrackedEntity> seen = mine(soldier, world);
        List<TrackedEntity> led = seen.stream().filter(e -> across(soldier, e.position()) <= Livestock.TEMPT_RANGE
                && across(e.position(), firstSeen.get(e.entityId())) >= FOLLOWED).toList();
        led.forEach(e -> picked.add(e.entityId()));
        if (phase == Phase.SEARCH) {
            if (!seen.isEmpty()) {
                phase = Phase.APPROACH;
            } else if (leg >= legs.size()) {
                return done(soldier, List.of(), "walked its ground and found no " + kind);
            } else if (across(soldier, legs.get(leg)) <= ARRIVED || overdue(soldier, legs.get(leg))) {
                nextWalk();
                leg++;
                return new Step(phase, Optional.empty(), false, 0, Optional.empty());
            } else if (water(soldier, legs.get(leg), WATER_AHEAD, world).isPresent()) {
                nextWalk();
                leg++; // not walked past: what lies over water isn't brought back
                return new Step(phase, Optional.empty(), false, 0, shore(soldier));
            } else {
                return new Step(phase, Optional.of(legs.get(leg)), false, 0, Optional.empty());
            }
        }
        if (phase == Phase.APPROACH) {
            // One with water on the straight way to it, however near, is left, and its bank said: it won't come over.
            for (TrackedEntity e : seen) {
                Optional<Position> bank = water(soldier, e.position(), SIGHT, world);
                if (bank.isPresent()) {
                    leave(e, "water between them");
                    return new Step(phase, Optional.empty(), true, led.size(), shore(bank.get()));
                }
            }
            Optional<TrackedEntity> out = seen.stream().filter(e -> across(soldier, e.position()) > NEAR)
                    .min(Comparator.comparingDouble(e -> across(soldier, e.position())));
            if (out.isEmpty()) {
                phase = seen.isEmpty() ? Phase.SEARCH : Phase.LEAD;
                nextWalk();
                return new Step(phase, Optional.empty(), true, led.size(), Optional.empty());
            }
            if (approachTicks.merge(out.get().entityId(), 1, Integer::sum) > APPROACH_TICKS) {
                leave(out.get(), "not got to");
                return new Step(phase, Optional.empty(), true, led.size(), Optional.empty());
            }
            return new Step(phase, Optional.of(out.get().position()), true, led.size(), Optional.empty());
        }
        if (across(soldier, bringTo) <= ARRIVED) {
            return done(soldier, led, "brought " + led.size() + " of " + picked.size() + " " + kind + " to " + bringTo);
        }
        if (overdue(soldier, bringTo)) {
            return done(soldier, List.of(), "gives up the way back to " + bringTo + " at " + soldier + ", " + led.size() + " " + kind + " with it");
        }
        Optional<TrackedEntity> lost = seen.stream().filter(e -> across(soldier, e.position()) > Livestock.TEMPT_RANGE).findFirst();
        if (lost.isPresent()) {
            if (goneBackFor.merge(lost.get().entityId(), 1, Integer::sum) > GONE_BACK_FOR) {
                leave(lost.get(), "gone back for " + GONE_BACK_FOR + " times and lost again");
            } else {
                phase = Phase.APPROACH;
            }
            return new Step(phase, Optional.empty(), true, led.size(), Optional.empty());
        }
        List<TrackedEntity> lagging = seen.stream().filter(e -> across(soldier, e.position()) > LAG).toList();
        if (!lagging.isEmpty() && ++waitTicks > WAIT_TICKS) {
            lagging.forEach(e -> leave(e, "it comes no nearer"));
            lagging = List.of();
        }
        if (lagging.isEmpty()) {
            waitTicks = 0;
            return new Step(phase, Optional.of(bringTo), true, led.size(), water(soldier, bringTo, WATER_AHEAD, world).flatMap(this::shore));
        }
        walkTicks--; // a tick stood waiting isn't a tick of the walk
        return new Step(phase, Optional.empty(), true, led.size(), Optional.empty());
    }

    /**
     * The animals of its kind in sight that are its own to bring: on its level, not left, first seen in its sector -
     * and not where they're wanted already: one grazing there is brought nowhere, and going up to it finds nothing.
     */
    private List<TrackedEntity> mine(Position soldier, WorldView world) {
        List<TrackedEntity> kinds = world.gameNear(soldier, SIGHT).stream()
                .filter(e -> e.typeId() == kind.typeId() && Math.abs(e.position().y() - soldier.y()) <= VERTICAL_REACH).toList();
        kinds.forEach(e -> firstSeen.putIfAbsent(e.entityId(), e.position()));
        return kinds.stream().filter(e -> !left.contains(e.entityId()))
                .filter(e -> across(firstSeen.get(e.entityId()), bringTo) > Livestock.TEMPT_RANGE)
                .filter(e -> sector.holds(firstSeen.get(e.entityId()).x(), firstSeen.get(e.entityId()).z())).toList();
    }

    /** Whether the walk to {@code end} has had its time: counted from its first tick, more for every block it has gained since. */
    private boolean overdue(Position soldier, Position end) {
        double off = across(soldier, end);
        if (walkNearest == Double.MAX_VALUE) {
            walkNearest = off;
            walkTicks = 0;
        }
        if (off < walkNearest - 1) {
            walkNearest = off;
            walkTicks = 0;
        }
        return ++walkTicks > TICKS_A_BLOCK + TICKS_OVER;
    }

    private void nextWalk() {
        walkTicks = 0;
        walkNearest = Double.MAX_VALUE;
    }

    private void leave(TrackedEntity animal, String why) {
        LOG.info("{} leaves the {} at {}: {}", name, kind, animal.position(), why);
        left.add(animal.entityId());
    }

    /** The land's end at {@code at}, if no shore within {@link #SAME_SHORE} of it has been said. */
    private Optional<Position> shore(Position at) {
        if (shores.stream().anyMatch(s -> across(s, at) <= SAME_SHORE)) {
            return Optional.empty();
        }
        shores.add(at);
        LOG.info("{} meets water at {}: the land ends here", name, at);
        return Optional.of(at);
    }

    private Step done(Position soldier, List<TrackedEntity> with, String said) {
        phase = Phase.DONE;
        brought = with.size();
        LOG.info("{} {}", name, said);
        return new Step(phase, Optional.empty(), false, brought, Optional.empty());
    }

    /**
     * The last dry place on the straight way from {@code from} to {@code to} before water, if water lies within
     * {@code reach} blocks along it - at its feet's height or the ground under them.
     */
    private static Optional<Position> water(Position from, Position to, double reach, WorldView world) {
        double d = across(from, to);
        int feet = (int) Math.floor(from.y());
        for (int ahead = 1; ahead <= reach && ahead <= d; ahead++) {
            double x = from.x() + (to.x() - from.x()) / d * ahead, z = from.z() + (to.z() - from.z()) / d * ahead;
            if (world.isWater((int) Math.floor(x), feet, (int) Math.floor(z)) || world.isWater((int) Math.floor(x), feet - 1, (int) Math.floor(z))) {
                return Optional.of(new Position(from.x() + (to.x() - from.x()) / d * (ahead - 1), from.y(), from.z() + (to.z() - from.z()) / d * (ahead - 1)));
            }
        }
        return Optional.empty();
    }

    private static double across(Position a, Position b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }
}
