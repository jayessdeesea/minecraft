package io.github.jayessdeesea.minecraft.command.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandAiConfigurationTest {

    private static final ChatModel LOCAL = FakeChatModels.THROWING;
    private static final ChatModel FRONTIER = FakeChatModels.replying("{}", new java.util.concurrent.atomic.AtomicReference<>());

    @Test
    void eachEchelonGetsTheProviderItNames() {
        Map<String, ChatModel> models = Map.of("ollamaChatModel", LOCAL, "anthropicChatModel", FRONTIER);

        assertSame(FRONTIER, CommandAiConfiguration.chatModel("general", "anthropic", models));
        assertSame(LOCAL, CommandAiConfiguration.chatModel("squad leader", "ollama", models));
    }

    @Test
    void anUnknownProviderStopsStartup() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> CommandAiConfiguration.chatModel("general", "gpt", Map.of("ollamaChatModel", LOCAL)));

        assertTrue(thrown.getMessage().contains("[ollama]"), thrown.getMessage());
    }
}
