package io.github.jayessdeesea.minecraft.command.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Hand-written {@link ChatModel} fakes - the one impure seam, faked without Mockito. */
final class FakeChatModels {

    private FakeChatModels() {
    }

    /** Fails the test if the model is ever called. */
    static final ChatModel THROWING = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("model should not have been called");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            throw new AssertionError("model should not have been called");
        }
    };

    /** Always answers {@code reply}, and records the last prompt it was sent. */
    static ChatModel replying(String reply, AtomicReference<Prompt> lastPrompt) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                lastPrompt.set(prompt);
                return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
