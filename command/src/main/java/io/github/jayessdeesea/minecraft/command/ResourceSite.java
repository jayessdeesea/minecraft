package io.github.jayessdeesea.minecraft.command;

/**
 * A place a squad has seen a material it could gather (docs/military-doctrine.md §12): a stand of
 * trees, centred at {@code location}, with {@code amount} of it in sight (logs, for wood). Part of a
 * squad's report to the general, so the general can send a squad to wood rather than guess.
 */
public record ResourceSite(Resource resource, Position location, int amount) {
}
