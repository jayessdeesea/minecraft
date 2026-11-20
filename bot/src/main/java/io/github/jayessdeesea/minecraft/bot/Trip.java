package io.github.jayessdeesea.minecraft.bot;

/**
 * A walk to a goal, for the prediction ledger (backlog/simulator-fidelity.md): the steps its first route to the goal
 * planned, and the ticks it took to stand there.
 */
public record Trip(int planned, int ticks) {
}
