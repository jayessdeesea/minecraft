package io.github.jayessdeesea.minecraft.command;

/**
 * Marks a leader that decides by standing rules, no model: its answer costs nothing and comes at once. The shell
 * that asks a leader to decide reads this to pace it - a rule is asked whenever there's something to decide on, a
 * model on a slower clock (docs/architecture.md, the latency architecture). A leader without it is paced as a model.
 */
public interface DecidesByRules {
}
