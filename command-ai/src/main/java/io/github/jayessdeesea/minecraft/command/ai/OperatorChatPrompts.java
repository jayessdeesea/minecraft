package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.core.io.Resource;

/**
 * The platoon leader's prompts for talking with the operator, from the files configuration names
 * ({@code minecraft.platoon-leader.chat-system-prompt}, {@code .chat-prompt}): the fixed system prompt, and the
 * template each of the operator's turns is filled in from ({@link PromptTemplate}).
 */
public record OperatorChatPrompts(String system, String user) {

    public static OperatorChatPrompts read(Resource system, Resource user) {
        return new OperatorChatPrompts(PromptTemplate.read(system), PromptTemplate.read(user));
    }
}
