package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.core.io.Resource;

/**
 * The general's prompts, from the files configuration names ({@code minecraft.general.*-prompt}):
 * its fixed system prompt, the user prompt's template, and the template each squad's section of
 * it is filled in from ({@link PromptTemplate}).
 */
public record GeneralPrompts(String system, String user, String squad) {

    public static GeneralPrompts read(Resource system, Resource user, Resource squad) {
        return new GeneralPrompts(PromptTemplate.read(system), PromptTemplate.read(user), PromptTemplate.read(squad));
    }
}
