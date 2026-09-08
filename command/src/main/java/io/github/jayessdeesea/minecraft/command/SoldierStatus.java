package io.github.jayessdeesea.minecraft.command;

/**
 * What a {@link Soldier} reports upward. This is the input a future {@code SquadLeader}'s decision
 * core builds its LLM prompt context from ({@code buildContext(List<SoldierStatus>)} in AGENTS.md's
 * "Behavior modeling" section) - kept to plain, provider-agnostic value types for exactly that
 * reason.
 */
public record SoldierStatus(String name, Position position, Order order) {
}
