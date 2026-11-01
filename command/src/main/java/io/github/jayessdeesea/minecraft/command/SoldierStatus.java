package io.github.jayessdeesea.minecraft.command;

/**
 * What a {@link Soldier} reports upward - the input a {@code SquadLeader}'s decision core builds
 * its LLM prompt context from (AGENTS.md's "Behavior modeling" section), kept to plain,
 * provider-agnostic value types for exactly that reason. Together it's a soldier's SITREP (docs/military-doctrine.md §4): where, tasked with
 * what, and how it's going - including CANTCO when the order can't be carried out - plus its own
 * {@link Condition} (LACE), empty until the server has first reported it.
 */
public record SoldierStatus(String name, Position position, Order order, Report report,
                            java.util.Optional<Condition> condition) {
}
