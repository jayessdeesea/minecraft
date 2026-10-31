package io.github.jayessdeesea.minecraft.command;

/**
 * A goal handed down from a {@link General} to a {@link SquadLeader} - freeform, unlike
 * {@link Order}. {@code Order} is a closed set because {@code DeterministicSoldier} executes it
 * directly and exhaustively; an {@code Objective} is consumed by another LLM ({@code SquadLeader})
 * that itself decides how to interpret it into concrete {@code Order}s, so there's no fixed
 * vocabulary to close over here.
 */
public record Objective(String description) {
}
