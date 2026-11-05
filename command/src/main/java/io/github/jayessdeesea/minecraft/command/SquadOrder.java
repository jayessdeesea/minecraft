package io.github.jayessdeesea.minecraft.command;

import java.util.Map;
import java.util.Objects;

/**
 * What a squad leader decides: where the squad goes and how it moves - FRAGO-shaped (only what
 * the squad needs to act on), with the commander's intent attached so soldiers and the squad can
 * act sensibly when the plan meets the terrain. See docs/military-doctrine.md §1 and §5.
 *
 * <p>{@code verticalTolerance} is how far from the objective's height the squad may end up -
 * small to keep everyone on one floor of a building, large in open hills. {@code risk} is how much
 * health the mission justifies spending to get there ({@link Risk}); {@code soldierRisk} overrides
 * it for named soldiers - typically one stuck soldier freed at MEDIUM without the whole squad
 * taking falls (docs/military-doctrine.md §7). {@code rearm} lets soldiers stop to make themselves
 * better kit, by the equipment ladder, when no fight is near (§11).
 * {@link Formations#assign} turns this into each soldier's {@link Order}.
 */
public record SquadOrder(Position objective, Formation formation, Interval interval,
                         double verticalTolerance, Risk risk, Map<String, Risk> soldierRisk, boolean rearm, String intent) {

    public SquadOrder {
        soldierRisk = Map.copyOf(soldierRisk);
    }

    /** A squad-wide risk with no per-soldier overrides, and no stopping to rearm. */
    public SquadOrder(Position objective, Formation formation, Interval interval,
                      double verticalTolerance, Risk risk, String intent) {
        this(objective, formation, interval, verticalTolerance, risk, Map.of(), false, intent);
    }

    /** Per-soldier risk, no stopping to rearm. */
    public SquadOrder(Position objective, Formation formation, Interval interval,
                      double verticalTolerance, Risk risk, Map<String, Risk> soldierRisk, String intent) {
        this(objective, formation, interval, verticalTolerance, risk, soldierRisk, false, intent);
    }

    /** The risk this soldier moves at: his override if he has one, else the squad's. */
    public Risk riskFor(String soldier) {
        return Objects.requireNonNullElse(soldierRisk.get(soldier), risk);
    }
}
