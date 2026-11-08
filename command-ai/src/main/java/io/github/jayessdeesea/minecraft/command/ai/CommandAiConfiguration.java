package io.github.jayessdeesea.minecraft.command.ai;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Map;
import java.util.TreeSet;

/**
 * One {@link ChatClient} per echelon, each on its own model provider, and each echelon's prompts: the general can think on a frontier model while the squad leaders run on a local
 * open-weight one - the tiering this project measures. Which provider each echelon uses, and
 * which files its prompts are, is configuration ({@code minecraft.general.chat-model},
 * {@code minecraft.general.system-prompt} and so on), never code, and has no default. A client
 * carries no default system prompt either: each call names its own. Every provider whose starter is on the classpath is auto-configured as a
 * {@code <provider>ChatModel} bean; each echelon picks one by name.
 *
 * <p>Two {@code ChatClient} beans of the same type, so every injection point names the one it
 * wants with {@code @Qualifier} - never {@code @Primary} (AGENTS.md "No fallbacks").
 */
@Configuration
public class CommandAiConfiguration {

    private static final Logger LOG = LogManager.getLogger(CommandAiConfiguration.class);

    @Bean
    ChatClient squadLeaderChatClient(Map<String, ChatModel> models,
                                     @Value("${minecraft.squad-leader.chat-model}") String provider) {
        return ChatClient.builder(chatModel("squad leader", provider, models)).build();
    }

    @Bean
    ChatClient generalChatClient(Map<String, ChatModel> models,
                                 @Value("${minecraft.general.chat-model}") String provider) {
        return ChatClient.builder(chatModel("general", provider, models)).build();
    }

    @Bean
    SquadLeaderPrompts squadLeaderPrompts(@Value("${minecraft.squad-leader.system-prompt}") Resource system,
                                          @Value("${minecraft.squad-leader.user-prompt}") Resource user) {
        return SquadLeaderPrompts.read(system, user);
    }

    @Bean
    GeneralPrompts generalPrompts(@Value("${minecraft.general.system-prompt}") Resource system,
                                  @Value("${minecraft.general.user-prompt}") Resource user,
                                  @Value("${minecraft.general.squad-prompt}") Resource squad) {
        return GeneralPrompts.read(system, user, squad);
    }

    /**
     * The model {@code echelon} thinks with: the {@code <provider>ChatModel} bean.
     *
     * @throws IllegalStateException if no provider by that name is configured - startup stops,
     *                               rather than an echelon quietly running on another model
     */
    static ChatModel chatModel(String echelon, String provider, Map<String, ChatModel> models) {
        ChatModel model = models.get(provider + "ChatModel");
        if (model == null) {
            throw new IllegalStateException("no chat model '" + provider + "' for the " + echelon + "; configured: "
                    + new TreeSet<>(models.keySet().stream().map(name -> name.replaceFirst("ChatModel$", "")).toList()));
        }
        LOG.info("The {} thinks with {}", echelon, provider);
        return model;
    }
}
