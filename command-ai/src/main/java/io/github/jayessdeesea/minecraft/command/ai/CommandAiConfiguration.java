package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * One {@link ChatClient} per echelon, each with its role's system prompt baked in - Spring AI's
 * recommended shape: derive clients from the auto-configured (prototype-scoped) {@code
 * ChatClient.Builder} so observability and customizers apply, put stable instructions in
 * {@code defaultSystem}, and send only the situation per call.
 *
 * <p>Two {@code ChatClient} beans of the same type, so every injection point names the one it
 * wants with {@code @Qualifier} - never {@code @Primary} (AGENTS.md "No fallbacks").
 */
@Configuration
public class CommandAiConfiguration {

    @Bean
    ChatClient squadLeaderChatClient(ChatClient.Builder builder,
                                     @Value("classpath:prompts/squad-leader-system.md") Resource systemPrompt) {
        return builder.defaultSystem(systemPrompt).build();
    }

    @Bean
    ChatClient generalChatClient(ChatClient.Builder builder,
                                 @Value("classpath:prompts/general-system.md") Resource systemPrompt) {
        return builder.defaultSystem(systemPrompt).build();
    }
}
