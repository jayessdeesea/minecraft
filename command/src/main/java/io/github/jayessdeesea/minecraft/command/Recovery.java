package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Recovering a stranded soldier (docs/military-doctrine.md §7): one with no route to its place that
 * doesn't cost it a fall, and nothing to build steps with - a replacement respawned on a ledge, say -
 * is fetched by the nearest squadmate carrying blocks. The squadmate builds the stair the engineer
 * lays out from where the stranded one stands down to the ground, and the stranded one walks down
 * it. Where the engineer can lay none, the squadmate is sent to it, to build what steps it finds.
 *
 * <p>A recovery, once begun, lasts until the stranded soldier has moved off where it was stuck: its
 * own report flickers as it tries its route again, and a recovery dropped each time would hand the
 * stair from one squadmate to another and never build it. One per squad, like {@link LinkUp}; the
 * squad's own reports are what it reads, before the link-up drill relabels a replacement.
 */
public final class Recovery {

    /** Blocks a squadmate needs carried to be sent: a stair down from a ledge is two a step. */
    public static final int BLOCKS_TO_FETCH = 24;
    /** Fewer blocks than this carried, and a stranded soldier can't build its own way down. */
    public static final int CANT_BUILD_BELOW = 2;
    /** Moved this far from where it was stuck, a soldier is on its way down: recovered. */
    static final double MOVED_OFF = 2;
    static final double TOLERANCE = 1.5;

    private static final class Episode {
        final Position at;
        final Optional<List<Cell>> stair;
        String fetcher;

        Episode(Position at, Optional<List<Cell>> stair) {
            this.at = at;
            this.stair = stair;
        }
    }

    /** Each stranded soldier being recovered, by name, in the order they were found. */
    private final Map<String, Episode> recovering = new LinkedHashMap<>();

    /** Whether {@code soldier} is stranded: no route, and too few blocks to build one. */
    public static boolean stranded(SoldierStatus soldier) {
        return soldier.report() instanceof Report.Cantco(String reason) && reason.startsWith("no route from")
                && blocks(soldier) < CANT_BUILD_BELOW;
    }

    /**
     * {@code orders} with, for each soldier being recovered, its fetcher - the nearest squadmate
     * that carried {@link #BLOCKS_TO_FETCH} blocks when it was picked - set to build {@code stair},
     * the engineer's stair down from where it was stranded, laid out once when it's found stranded,
     * or else sent to it. A squadmate fetches one at a time, and none is sent while the squad is in
     * contact.
     */
    public List<Order> orders(List<SoldierStatus> reported, List<Order> orders, Function<SoldierStatus, Optional<List<Cell>>> stair) {
        Map<String, SoldierStatus> byName = new LinkedHashMap<>();
        reported.forEach(s -> byName.put(s.name(), s));
        recovering.entrySet().removeIf(e -> !byName.containsKey(e.getKey())
                || Cohesion.distance(byName.get(e.getKey()).position(), e.getValue().at) > MOVED_OFF);
        for (SoldierStatus s : reported) {
            if (stranded(s) && !recovering.containsKey(s.name())) {
                recovering.put(s.name(), new Episode(s.position(), stair.apply(s)));
            }
        }
        if (reported.stream().anyMatch(s -> s.report() instanceof Report.InContact)) {
            return orders;
        }
        List<Order> result = new ArrayList<>(orders);
        List<String> names = List.copyOf(byName.keySet());
        for (Map.Entry<String, Episode> e : recovering.entrySet()) {
            Episode episode = e.getValue();
            if (episode.fetcher == null || !fetching(episode.fetcher, byName)) {
                episode.fetcher = nearestCarrier(e.getKey(), episode.at, reported);
            }
            if (episode.fetcher == null) {
                continue;
            }
            Position at = episode.at;
            result.set(names.indexOf(episode.fetcher), episode.stair.filter(cells -> !cells.isEmpty()).<Order>map(Order.Place::new)
                    .orElse(new Order.MoveTo(at.x(), at.y(), at.z(), TOLERANCE, TOLERANCE, Risk.LOW)));
        }
        return List.copyOf(result);
    }

    /** Whether {@code fetcher} can go on fetching: still in the squad, and neither stranded nor rejoining itself. */
    private boolean fetching(String fetcher, Map<String, SoldierStatus> byName) {
        SoldierStatus mate = byName.get(fetcher);
        return mate != null && !recovering.containsKey(fetcher) && !(mate.report() instanceof Report.LinkingUp);
    }

    private String nearestCarrier(String stuck, Position at, List<SoldierStatus> reported) {
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        for (SoldierStatus mate : reported) {
            boolean taken = recovering.values().stream().anyMatch(e -> mate.name().equals(e.fetcher));
            double distance = Cohesion.distance(mate.position(), at);
            if (!mate.name().equals(stuck) && !taken && blocks(mate) >= BLOCKS_TO_FETCH && !recovering.containsKey(mate.name())
                    && !(mate.report() instanceof Report.LinkingUp) && distance < bestDistance) {
                best = mate.name();
                bestDistance = distance;
            }
        }
        return best;
    }

    private static int blocks(SoldierStatus soldier) {
        return soldier.condition().flatMap(Condition::kit).map(k -> k.stone() + k.dirt()).orElse(0);
    }
}
