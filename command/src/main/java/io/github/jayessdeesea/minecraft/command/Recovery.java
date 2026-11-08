package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recovering a stranded soldier (docs/military-doctrine.md §7): one with no route to its place that
 * doesn't cost it a fall, and nothing to build steps with - a replacement respawned on a ledge, say -
 * is fetched by the nearest squadmate carrying blocks. The squadmate goes to it; on the way it
 * builds the steps up that it needs, and they stay, so the stranded one walks down them.
 *
 * <p>Pure and stateless like the other drills, run on the squad's own reports: a soldier's CANTCO
 * says whether its route failed, before the link-up drill relabels a replacement.
 */
public final class Recovery {

    /** Blocks a squadmate needs carried to be sent: a stair up a cliff is two a step. */
    public static final int BLOCKS_TO_FETCH = 24;
    /** Fewer blocks than this carried, and a stranded soldier can't build its own way down. */
    public static final int CANT_BUILD_BELOW = 2;
    static final double TOLERANCE = 1.5;

    private Recovery() {
    }

    /** Whether {@code soldier} is stranded: no route, and too few blocks to build one. */
    public static boolean stranded(SoldierStatus soldier) {
        return soldier.report() instanceof Report.Cantco(String reason) && reason.startsWith("no route from")
                && soldier.condition().flatMap(Condition::kit).map(k -> k.stone() + k.dirt() < CANT_BUILD_BELOW).orElse(false);
    }

    /**
     * {@code orders} with, for each stranded soldier in {@code reported} (the squad's own reports,
     * in squad order), the nearest squadmate carrying {@link #BLOCKS_TO_FETCH} blocks sent to it. A
     * squadmate fetches one at a time, and none is sent while the squad is in contact.
     */
    public static List<Order> orders(List<SoldierStatus> reported, List<Order> orders) {
        if (reported.stream().anyMatch(s -> s.report() instanceof Report.InContact)) {
            return orders;
        }
        List<Order> result = new ArrayList<>(orders);
        Set<Integer> fetching = new HashSet<>();
        for (int i = 0; i < reported.size(); i++) {
            SoldierStatus stuck = reported.get(i);
            if (!stranded(stuck)) {
                continue;
            }
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int j = 0; j < reported.size(); j++) {
                SoldierStatus mate = reported.get(j);
                boolean carries = mate.condition().flatMap(Condition::kit).map(k -> k.stone() + k.dirt() >= BLOCKS_TO_FETCH).orElse(false);
                double distance = Cohesion.distance(mate.position(), stuck.position());
                if (j != i && carries && !fetching.contains(j) && !stranded(mate) && !(mate.report() instanceof Report.LinkingUp)
                        && distance < bestDistance) {
                    best = j;
                    bestDistance = distance;
                }
            }
            if (best >= 0) {
                fetching.add(best);
                Position at = stuck.position();
                result.set(best, new Order.MoveTo(at.x(), at.y(), at.z(), TOLERANCE, TOLERANCE, Risk.LOW));
            }
        }
        return List.copyOf(result);
    }
}
