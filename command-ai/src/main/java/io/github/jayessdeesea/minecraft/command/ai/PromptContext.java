package io.github.jayessdeesea.minecraft.command.ai;

/**
 * The pure output of a {@code buildContext} step (AGENTS.md's "Behavior modeling" section) - the
 * fully-rendered prompt text ready to send to the model. Shared by {@link SpringAiSquadLeader} and
 * {@link SpringAiGeneral} rather than each defining its own near-identical wrapper.
 */
record PromptContext(String prompt) {
}
