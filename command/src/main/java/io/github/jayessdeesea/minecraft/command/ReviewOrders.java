package io.github.jayessdeesea.minecraft.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The review of what's built, ordered by the platoon leader (operator, 2026-11-26: anti-entropy is ordered - the platoon
 * leader knows where the squads are, and orders them to review the constructed chunks near them, each at least once
 * every four days, and to put right what the review finds). A standing rule, pure over where the squads stand and when
 * each constructed chunk was last reviewed; the rule and its reasons are in docs/echelons/platoon-leader/decisions.md,
 * "What's built is reviewed".
 */
public final class ReviewOrders {

    /** A garrison day and night. */
    public static final long DAY_MILLIS = 20 * 60_000;
    /** No constructed chunk goes longer than this unreviewed. */
    public static final long EVERY = 4 * DAY_MILLIS;
    /** From when a chunk is due a review: a day before it must have had one, so a squad has a day to come free. */
    public static final long DUE = EVERY - DAY_MILLIS;
    /** From when a chunk's review takes a squad off a mission's step: half a day left to walk there and look. */
    public static final long URGENT = EVERY - DAY_MILLIS / 2;
    /** How far, in chunks each way, a squad is sent to review: the force near it, no outpost with nobody there. */
    public static final int REACH = 8;
    /** How long a squad has to reach the chunk it's ordered to review; past it, the review's given to another. */
    public static final long GIVE_UP = DAY_MILLIS;
    /** How many times a squad gives a site's review up before it's ordered to it no more: a retry changes something. */
    public static final int GIVEN_UP_FOR_GOOD = 2;

    private ReviewOrders() {
    }

    /** A constructed chunk, {@code name} its mission's, last reviewed - or built - at {@code reviewedAt}. */
    public record Site(String name, int chunkX, int chunkZ, long reviewedAt) {
    }

    /** What a squad holds when the reviews are dealt. */
    public enum Standing {
        /** Nothing a review mayn't take it from: tending, holding, its standing work. */
        FREE,
        /** A mission's step: taken off it only for a review that can't wait ({@link #URGENT}). */
        ON_A_STEP,
        /** The force's food, the operator's scouting, or by night a mission's step: never sent, and taken off a review it holds. */
        HELD
    }

    /** A squad by name, where its soldiers stand, and what it holds. */
    public record Squad(String name, List<Position> soldiers, Standing standing) {
        public Squad {
            soldiers = List.copyOf(soldiers);
        }
    }

    /** {@code squad} ordered to review {@code site}, since {@code since}. */
    public record Order(String squad, String site, long since) {
    }

    /** The reviews standing after a decision, and those it gave up - each said once. */
    public record Decision(List<Order> orders, List<Order> givenUp) {
        public Decision {
            orders = List.copyOf(orders);
            givenUp = List.copyOf(givenUp);
        }
    }

    /**
     * The reviews that stand at {@code now}: each {@code open} one kept while its site and squad stand and it's younger
     * than {@link #GIVE_UP}; then each site due ({@link #DUE}) and not under review, the longest unreviewed first,
     * ordered to the nearest squad within {@link #REACH} that has no review - a free one, and only when the review is
     * {@link #URGENT} one on a mission's step, the free first; as near, the first in the order of battle. Never a squad
     * {@link Standing#HELD}, nor one that gave up that site ({@code gaveUp}) in the day after, nor at all once it has given it
     * up {@link #GIVEN_UP_FOR_GOOD} times ({@code gaveUp} kept till the site's reviewed). One review a squad.
     */
    public static Decision decide(List<Site> sites, List<Squad> squads, List<Order> open, List<Order> gaveUp, long now) {
        Set<String> siteNames = new HashSet<>();
        sites.forEach(s -> siteNames.add(s.name()));
        List<Order> orders = new ArrayList<>(), givenUp = new ArrayList<>();
        for (Order o : open) {
            if (!siteNames.contains(o.site()) || squads.stream().noneMatch(q -> q.name().equals(o.squad()))) {
                continue;
            }
            // Past its day it's given up whatever the squad holds now: a squad held since is still the one that didn't make it.
            if (now - o.since() >= GIVE_UP) {
                givenUp.add(o);
            } else if (squads.stream().anyMatch(q -> q.name().equals(o.squad()) && q.standing() != Standing.HELD)) {
                orders.add(o);
            }
        }
        // A squad that gave a review up isn't given it back for a day after: then it may be, if it's still the one near - and
        // having given it up twice, never: the third try would be the same as the two before.
        List<Order> excluded = new ArrayList<>(gaveUp.stream().filter(o -> now - o.since() < 2 * GIVE_UP
                || gaveUp.stream().filter(g -> g.squad().equals(o.squad()) && g.site().equals(o.site())).count() >= GIVEN_UP_FOR_GOOD).toList());
        excluded.addAll(givenUp);
        List<Site> due = sites.stream().filter(s -> now - s.reviewedAt() >= DUE)
                .sorted(Comparator.comparingLong(Site::reviewedAt).thenComparing(Site::name)).toList();
        for (Site site : due) {
            if (orders.stream().anyMatch(o -> o.site().equals(site.name()))) {
                continue;
            }
            boolean urgent = now - site.reviewedAt() >= URGENT;
            Optional<Squad> to = squads.stream()
                    .filter(q -> q.standing() == Standing.FREE || urgent && q.standing() == Standing.ON_A_STEP)
                    .filter(q -> orders.stream().noneMatch(o -> o.squad().equals(q.name())))
                    .filter(q -> excluded.stream().noneMatch(o -> o.squad().equals(q.name()) && o.site().equals(site.name())))
                    .filter(q -> distance(q, site) <= REACH)
                    // Stable over the order of battle: as near and as free, the first listed.
                    .min(Comparator.comparingInt((Squad q) -> q.standing() == Standing.FREE ? 0 : 1).thenComparingInt(q -> distance(q, site)));
            to.ifPresent(q -> orders.add(new Order(q.name(), site.name(), now)));
        }
        return new Decision(orders, givenUp);
    }

    /** The chunks from {@code squad}'s nearest soldier to {@code site}, the most of the two ways; none in the world, no reach. */
    static int distance(Squad squad, Site site) {
        return squad.soldiers().stream().mapToInt(p -> Math.max(Math.abs(Math.floorDiv((int) Math.floor(p.x()), 16) - site.chunkX()),
                Math.abs(Math.floorDiv((int) Math.floor(p.z()), 16) - site.chunkZ()))).min().orElse(Integer.MAX_VALUE);
    }
}
