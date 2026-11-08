package io.github.jayessdeesea.minecraft.command.ai;

import java.util.Map;

/**
 * The pure output of a {@code buildContext} step (AGENTS.md's "Behavior modeling" section): the
 * per-call user prompt's template and the parameters it's filled in with. The role and doctrine
 * are the echelon's fixed system prompt, so they aren't repeated here. A section shown only
 * sometimes has its parameter {@code false} when it isn't.
 */
record PromptContext(String template, Map<String, Object> params) {

    PromptContext {
        params = Map.copyOf(params);
    }

    /** The user prompt as the model reads it. */
    String prompt() {
        return PromptTemplate.render(template, params);
    }
}
