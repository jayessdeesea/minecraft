package io.github.jayessdeesea.minecraft.command.ai;

/**
 * The impure output of a {@code callModel} step (AGENTS.md's "Behavior modeling" section) - the
 * model's raw text response, not yet parsed. Shared by {@link SpringAiSquadLeader} and
 * {@link SpringAiGeneral} rather than each defining its own near-identical wrapper.
 */
record LlmResponse(String text) {
}
