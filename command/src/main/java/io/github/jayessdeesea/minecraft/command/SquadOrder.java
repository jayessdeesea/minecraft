package io.github.jayessdeesea.minecraft.command;

/**
 * What a squad leader decides: where the squad goes and how it moves - FRAGO-shaped (only what
 * the squad needs to act on), with the commander's intent attached so soldiers and the squad can
 * act sensibly when the plan meets the terrain. See docs/military-doctrine.md §1 and §5.
 *
 * <p>{@code verticalTolerance} is how far from the objective's height the squad may end up -
 * small to keep everyone on one floor of a building, large in open hills. {@code risk} is how much
 * health the mission justifies spending to get there ({@link Risk}).
 * {@link Formations#assign} turns this into each soldier's {@link Order}.
 */
public record SquadOrder(Position objective, Formation formation, Interval interval,
                         double verticalTolerance, Risk risk, String intent) {
}
