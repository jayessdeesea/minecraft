package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.core.io.Resource;

/**
 * The platoon leader's prompts, from the files configuration names ({@code minecraft.platoon-leader.*-prompt}):
 * its fixed system prompt, the user prompt's template, and the template each squad's section of
 * it is filled in from ({@link PromptTemplate}).
 */
public record PlatoonLeaderPrompts(String system, String user, String squad) {

    public static PlatoonLeaderPrompts read(Resource system, Resource user, Resource squad) {
        return new PlatoonLeaderPrompts(PromptTemplate.read(system), PromptTemplate.read(user), PromptTemplate.read(squad));
    }
}
