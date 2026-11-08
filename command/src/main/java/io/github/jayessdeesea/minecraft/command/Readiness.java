package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How safe a squad is where it stands, and how fit it is to fight (docs/military-doctrine.md §17):
 * derived from where its soldiers are and what they carry, never reported by the squad. Safety
 * goes with the ground, not the unit - a squad that leaves the camp is back to the first night of
 * an air drop until it builds where it is.
 *
 * @param safety    what's built round the squad
 * @param rating    its fighting fitness, a C-rating in AR 220-1's sense: 1 fully ready, 4 not
 * @param shortfall what holds the rating down, in plain words - empty at C-1
 */
public record Readiness(Safety safety, int rating, String shortfall) {

    /** What's built round a squad, in the lodgment's order: each a phase of the foothold met where it stands. */
    public enum Safety {
        /** Away from every base, not sealed in: the first night of an air drop. */
        EXPOSED,
        /** Sealed in a patrol base, or at a base with its shelter and no more. */
        SHELTER,
        /** At a base whose ground is lit and walled. */
        AIRHEAD,
        /** At a base with food and wood that grow and a mine. */
        BUILDUP,
        /** At a base where every phase is met. */
        ESTABLISHED
    }

    /** How near the camp's ground a squad's middle soldier stands to count as at the camp. */
    public static final double AT_BASE = 24;

    /** The thresholds of a C-rating, as AR 220-1's: the share of the squad ready for C-1, C-2 and C-3. */
    private static final double[] RATING_AT_LEAST = {0.9, 0.8, 0.7};

    /** {@code squad}'s readiness, the home base being {@code base}'s camp when there is one. */
    public static Readiness of(SquadStatus squad, Optional<Foothold> base) {
        return new Readiness(safety(squad, base), rating(Logstat.of(squad.soldiers())), shortfall(Logstat.of(squad.soldiers())));
    }

    static Safety safety(SquadStatus squad, Optional<Foothold> base) {
        Optional<Foothold> here = base.filter(b -> at(squad.soldiers(), b.camp()));
        if (here.isPresent()) {
            int reached = here.get().nextPhase().orElse(Safety.values().length) - 1;
            return Safety.values()[Math.max(reached, squad.holdingBase() ? 1 : 0)];
        }
        return squad.holdingBase() ? Safety.SHELTER : Safety.EXPOSED;
    }

    private static boolean at(List<SoldierStatus> soldiers, Position camp) {
        if (soldiers.isEmpty()) {
            return false;
        }
        Position middle = soldiers.stream().map(SoldierStatus::position).sorted(java.util.Comparator.comparingDouble(Position::x))
                .toList().get(soldiers.size() / 2);
        return Math.hypot(middle.x() - camp.x(), middle.z() - camp.z()) <= AT_BASE;
    }

    /** The C-rating: the worst of the shares armed, armoured, fed and unwounded. Nobody reporting yet is C-4. */
    static int rating(Logstat l) {
        if (l.kitReported() == 0) {
            return 4;
        }
        double worst = Math.min(Math.min(1 - (double) l.unarmed() / l.kitReported(), 1 - (double) l.armourEmpty() / l.armourSlots()),
                Math.min(1 - (double) l.hungry() / l.reporting(), 1 - (double) l.wounded() / l.reporting()));
        for (int c = 0; c < RATING_AT_LEAST.length; c++) {
            if (worst >= RATING_AT_LEAST[c]) {
                return c + 1;
            }
        }
        return 4;
    }

    static String shortfall(Logstat l) {
        if (l.kitReported() == 0) {
            return "no soldier has reported his kit yet";
        }
        List<String> short_ = new java.util.ArrayList<>();
        if (l.unarmed() > 0) {
            short_.add(String.format(Locale.ROOT, "%d of %d unarmed", l.unarmed(), l.kitReported()));
        }
        if (l.armourEmpty() > 0) {
            short_.add(String.format(Locale.ROOT, "%d of %d armour slots empty", l.armourEmpty(), l.armourSlots()));
        }
        if (l.hungry() > 0) {
            short_.add(String.format(Locale.ROOT, "%d of %d hungry", l.hungry(), l.reporting()));
        }
        if (l.wounded() > 0) {
            short_.add(String.format(Locale.ROOT, "%d of %d below half health", l.wounded(), l.reporting()));
        }
        return String.join(", ", short_);
    }
}
