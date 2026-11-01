package io.github.jayessdeesea.minecraft.command.ai;

/**
 * The pure output of a {@code buildContext} step (AGENTS.md's "Behavior modeling" section): the
 * per-call user prompt - the situation, in XML-tagged sections. The role and doctrine are the
 * client's fixed system prompt, so they aren't repeated here.
 */
record PromptContext(String prompt) {
}
