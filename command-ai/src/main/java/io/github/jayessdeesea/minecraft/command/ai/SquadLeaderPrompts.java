package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.core.io.Resource;

/**
 * A squad leader's prompts, from the files configuration names ({@code minecraft.squad-leader.*-prompt}):
 * its fixed system prompt and the user prompt's template ({@link PromptTemplate}).
 */
public record SquadLeaderPrompts(String system, String user) {

    public static SquadLeaderPrompts read(Resource system, Resource user) {
        return new SquadLeaderPrompts(PromptTemplate.read(system), PromptTemplate.read(user));
    }
}
