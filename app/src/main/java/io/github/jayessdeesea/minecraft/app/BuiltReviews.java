package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.ReviewOrders;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reviews of what the missions built, as the platoon leader orders them ({@link ReviewOrders}): when each was last
 * reviewed, which squad is ordered to which, and which squad found the drift a repair puts right - that squad's task.
 * Held by {@link FarmMissions}, under its lock; the rule is docs/echelons/platoon-leader/decisions.md, "What's built is
 * reviewed".
 */
final class BuiltReviews {

    private static final Logger LOG = LogManager.getLogger(BuiltReviews.class);

    /** How near the chunk, in blocks, a soldier of the squad ordered stands for its review to be made. */
    static final int AT = 4, DEPTH = 8;

    /** When each built mission's chunk was last reviewed, or built. */
    private final Map<FarmMission, Long> reviewedAt = new HashMap<>();
    /** The reviews ordered and not made. */
    private List<ReviewOrders.Order> open = List.of();
    /** Reviews a squad gave up, by squad and site, kept till the site's reviewed: the rule's {@code gaveUp}. */
    private final List<ReviewOrders.Order> gaveUp = new ArrayList<>();
    /** When each chunk gone past {@link ReviewOrders#EVERY} unreviewed was last said so: once a day. */
    private final Map<String, Long> overdueSaid = new HashMap<>();
    /** The squad whose review found each drift: the repair is its task. */
    private final Map<FarmMission, String> repairs = new HashMap<>();

    /** {@code m} built, or built again, at {@code now}: reviewed as it was measured done; its repair over. */
    void built(FarmMission m, long now) {
        reviewedAt.put(m, now);
        repairs.remove(m);
    }

    /**
     * When {@code m} was last reviewed: one never seen reviewed - built before a restart - due in the first day, spread by
     * its name ({@link ReviewOrders#DUE}), so the chunks built come due one after another and not all at once, each urgent
     * half a day after.
     */
    long reviewedAt(FarmMission m, long now) {
        return reviewedAt.computeIfAbsent(m, k -> now - ReviewOrders.DUE + Math.floorMod(m.name().hashCode(), ReviewOrders.DAY_MILLIS));
    }

    /** Whether {@code m} is reviewed: a mine mined dry has no structure left to drift ({@link TemplateDrift#drifted}). */
    static boolean reviewable(FarmMission m) {
        return m.crop() != FarmMission.Crop.MINE;
    }

    /** The reviews open at {@code now} and not past their give-up: their squads are held on them before any step is dealt. */
    List<ReviewOrders.Order> standing(Collection<FarmMission> built, long now) {
        return open.stream().filter(o -> now - o.since() < ReviewOrders.GIVE_UP && built.stream().anyMatch(m -> reviewable(m) && m.name().equals(o.site()))).toList();
    }

    /** The squad ordered to review {@code m}, if one is. */
    Optional<String> orderedTo(FarmMission m) {
        return open.stream().filter(o -> o.site().equals(m.name())).map(ReviewOrders.Order::squad).findFirst();
    }

    /** The squad whose review found {@code m} drifted, while its repair is worked. */
    Optional<String> repairer(FarmMission m) {
        return Optional.ofNullable(repairs.get(m));
    }

    /** Where each of {@code roster}'s squads' soldiers stand, by {@code soldiers}' statuses: what a review ordered is made by. */
    static Map<String, List<Position>> where(Roster roster, List<io.github.jayessdeesea.minecraft.command.SoldierStatus> soldiers) {
        Map<String, List<Position>> at = new HashMap<>();
        roster.squads().forEach(sq -> at.put(sq.name(), soldiers.stream().filter(s -> sq.soldiers().contains(s.name()))
                .map(io.github.jayessdeesea.minecraft.command.SoldierStatus::position).toList()));
        return at;
    }

    /** The objective a squad ordered to review {@code m} is given: be at its chunk, at its level, and look. */
    static Objective objective(FarmMission m) {
        Position centre = m.centre(m.steps().getLast());
        // A tree farm's at its corner: its centre's survey ground is the canopy, and a squad sent there climbs on steps it leaves.
        Position at = m.crop() == FarmMission.Crop.TREES ? new Position(m.minX() + 0.5, centre.y(), m.minZ() + 0.5) : centre;
        return new Objective("Review the " + m.what() + " at chunk (" + m.chunkX() + ", " + m.chunkZ() + ")", at,
                "What's built kept as built: put right what's found", new Objective.Task.None());
    }

    /**
     * The reviews dealt into {@code out} at {@code now}: each squad's standing - those {@code notSent} held, those {@code onSteps}
     * on a mission's step, and held on it by {@code night}, the rest free - and each review standing put on its squad, which
     * is added to {@code held}. A squad taken off a step for one, a review given up or dropped, and a chunk gone past four
     * days with none, each said.
     */
    void deal(List<Objective> out, List<SquadStatus> statuses, Set<Integer> held, Set<Integer> onSteps, Set<String> notSent,
              boolean night, Collection<FarmMission> built, long now) {
        List<ReviewOrders.Site> sites = built.stream().filter(BuiltReviews::reviewable).map(m -> new ReviewOrders.Site(m.name(), m.chunkX(), m.chunkZ(), reviewedAt(m, now))).toList();
        List<ReviewOrders.Squad> squads = new ArrayList<>();
        for (int i = 0; i < statuses.size(); i++) {
            SquadStatus s = statuses.get(i);
            // By night a squad on a step keeps it: taken off it, it would spend the night in the shelter, not at the review.
            ReviewOrders.Standing standing = notSent.contains(s.squadName()) || night && onSteps.contains(i) ? ReviewOrders.Standing.HELD
                    : onSteps.contains(i) ? ReviewOrders.Standing.ON_A_STEP : ReviewOrders.Standing.FREE;
            squads.add(new ReviewOrders.Squad(s.squadName(), s.soldiers().stream().map(io.github.jayessdeesea.minecraft.command.SoldierStatus::position).toList(), standing));
        }
        ReviewOrders.Decision decision = ReviewOrders.decide(sites, squads, open, gaveUp, now);
        for (ReviewOrders.Order o : decision.givenUp()) {
            gaveUp.add(o);
            boolean twice = gaveUp.stream().filter(g -> g.squad().equals(o.squad()) && g.site().equals(o.site())).count() >= ReviewOrders.GIVEN_UP_FOR_GOOD;
            LOG.warn("[Outcome] {}'s review of {} not made in a day of its order - given up{} - SHORT", o.squad(), o.site(),
                    twice ? " twice: it isn't ordered to it again till it's reviewed" : "");
        }
        List<ReviewOrders.Order> before = open;
        open = decision.orders();
        before.stream().filter(o -> !open.contains(o) && !decision.givenUp().contains(o))
                .forEach(o -> LOG.info("[Review] {}'s review of {} dropped - its squad held or gone, or the chunk given again", o.squad(), o.site()));
        for (ReviewOrders.Order o : open) {
            FarmMission m = built.stream().filter(b -> b.name().equals(o.site())).findFirst().orElseThrow();
            int i = statuses.stream().map(SquadStatus::squadName).toList().indexOf(o.squad());
            if (!before.contains(o)) {
                LOG.info("[Review] {} ordered to review {} - last reviewed {} min ago", o.squad(), m.name(), (now - reviewedAt(m, now)) / 60_000);
            }
            if (onSteps.contains(i)) {
                LOG.warn("[Guardrail] {} taken off '{}' to review {}: it's gone {} min unreviewed", o.squad(), out.get(i).mission(), m.name(),
                        (now - reviewedAt(m, now)) / 60_000);
            }
            out.set(i, objective(m));
            held.add(i);
        }
        // The rule's promise checked: a chunk past four days with no review ordered, whatever the cause, is said once a day,
        // for someone to look into.
        for (ReviewOrders.Site s : sites) {
            if (now - s.reviewedAt() >= ReviewOrders.EVERY && open.stream().noneMatch(o -> o.site().equals(s.name()))
                    && (!overdueSaid.containsKey(s.name()) || now - overdueSaid.get(s.name()) >= ReviewOrders.DAY_MILLIS)) {
                overdueSaid.put(s.name(), now);
                LOG.warn("[Alert] {} unreviewed {} min, past four days, and no squad ordered to it - who's near it, and what each holds, says why",
                        s.name(), (now - s.reviewedAt()) / 60_000);
            }
        }
    }

    /**
     * The squad ordered to review {@code m} that stands at it now - a soldier of it within {@link #AT} blocks of its chunk,
     * and of its level by {@link #DEPTH}: not down a shaft under it - by where {@code squads}' soldiers are.
     */
    Optional<String> arrived(FarmMission m, Map<String, List<Position>> squads) {
        return orderedTo(m).filter(squad -> squads.getOrDefault(squad, List.of()).stream().anyMatch(p -> p.x() >= m.minX() - AT && p.x() < m.minX() + 16 + AT
                && p.z() >= m.minZ() - AT && p.z() < m.minZ() + 16 + AT && Math.abs(p.y() - m.level()) <= DEPTH));
    }

    /** A template whose review found its structure gone - {@code gone} what - for its step to be opened again. */
    record Found(FarmMission mission, String gone) {
    }

    /** Whether a review ordered was made, as built, since last asked: the platoon leader woken to give its squad another task. */
    private boolean made;

    /** Why to wake the platoon leader, once, if a review ordered was made as built since last asked. */
    Optional<String> made() {
        Optional<String> why = made ? Optional.of("a review made") : Optional.empty();
        made = false;
        return why;
    }

    /**
     * Each of {@code built} in sight in {@code world} at {@code now} that is due a look - the squad ordered to review it
     * standing at it ({@link #arrived}), or {@code kept} by a squad - measured against its layout
     * ({@link TemplateDrift#drifted}): reviewed, and those drifted returned, each the repair of the squad that found it.
     */
    List<Found> look(WorldView world, Collection<FarmMission> built, Set<FarmMission> kept, Map<String, List<Position>> squads, long now) {
        List<Found> found = new ArrayList<>();
        for (FarmMission m : List.copyOf(built)) {
            if (!world.isLoaded(m.minX(), m.minZ()) || !world.isLoaded(m.minX() + 15, m.minZ() + 15)) {
                continue; // not in sight: looked at next time
            }
            Optional<String> by = arrived(m, squads);
            if (by.isEmpty() && !kept.contains(m)) {
                continue;
            }
            // Its keepers' look is its review: a squad ordered to it and not there yet is done with it, and said so.
            Optional<String> ordered = orderedTo(m).filter(squad -> by.isEmpty());
            ordered.ifPresent(squad -> LOG.info("[Review] {}'s review of {} made by its keepers", squad, m.name()));
            made |= ordered.isPresent();
            Optional<String> gone = TemplateDrift.drifted(world, m);
            reviewed(m, by, gone.isPresent(), now);
            LOG.info("[Review] {} reviewed {} - {}", by.orElse("its keepers"), m.name(), gone.map(g -> "drifted" + by.map(s -> "; its repair " + s + "'s").orElse(""))
                    .orElse("as built"));
            made |= by.isPresent() && gone.isEmpty();
            gone.ifPresent(g -> found.add(new Found(m, g)));
        }
        return found;
    }

    /** {@code m} given again or cancelled: what was kept of its reviews forgotten - it's built anew, or not at all. */
    void forget(FarmMission m) {
        reviewedAt.remove(m);
        repairs.remove(m);
        overdueSaid.remove(m.name());
        open = open.stream().filter(o -> !o.site().equals(m.name())).toList();
        gaveUp.removeIf(o -> o.site().equals(m.name()));
    }

    /** {@code m} reviewed at {@code now}, by {@code squad} if it was ordered there: its order done, and drift found its squad's to repair. */
    private void reviewed(FarmMission m, Optional<String> squad, boolean drifted, long now) {
        reviewedAt.put(m, now);
        overdueSaid.remove(m.name());
        open = open.stream().filter(o -> !o.site().equals(m.name())).toList();
        gaveUp.removeIf(o -> o.site().equals(m.name()));
        if (drifted) {
            squad.ifPresent(s -> repairs.put(m, s));
        }
    }
}
