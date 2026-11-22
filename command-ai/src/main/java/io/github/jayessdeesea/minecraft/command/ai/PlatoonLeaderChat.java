package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The platoon leader talking with the operator: a conversation, each turn answered from the computed state it's
 * already shown - what it was shown at its last decision ({@link SpringAiPlatoonLeader#lastShown}) and the clock and
 * the ground to flatten as they stand now - never asked to work state out. The earlier turns go with each new one, so
 * follow-ups work; the conversation is the caller's, kept nowhere here. Same {@link #buildContext} (pure) -&gt;
 * {@link #answer} (impure) split as the echelons' decisions.
 */
@Component
public final class PlatoonLeaderChat {

    private static final Logger LOG = LogManager.getLogger(PlatoonLeaderChat.class);

    /** One turn of the conversation: what the operator said, and what the platoon leader answered. */
    public record Turn(String operator, String platoonLeader) {
    }

    private final ChatClient chatClient;
    private final OperatorChatPrompts prompts;
    private final SpringAiPlatoonLeader platoonLeader;

    public PlatoonLeaderChat(@Qualifier("platoonLeaderChatClient") ChatClient chatClient, OperatorChatPrompts prompts,
                             SpringAiPlatoonLeader platoonLeader) {
        this.chatClient = chatClient;
        this.prompts = prompts;
        this.platoonLeader = platoonLeader;
    }

    /** The platoon leader's reply to {@code message}, after {@code earlier}, with the clock and the ground as they stand. */
    public ChatReply answer(List<Turn> earlier, String message, Optional<TimeOfDay> time, Flatten flatten) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("an empty message");
        }
        PromptContext context = buildContext(prompts, message, time, flatten, platoonLeader.lastShown());
        LOG.info("[Chat] the platoon leader is shown, after {} earlier turn(s):\n{}", earlier.size(), context.prompt());
        ChatReply reply = chatClient.prompt()
                .templateRenderer(PromptTemplate.RENDERER)
                .system(prompts.system())
                .messages(history(earlier))
                .user(user -> user.text(context.template()).params(context.params()))
                .call()
                .entity(ChatReply.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        if (reply == null || reply.reply() == null || reply.action() == null) {
            throw new IllegalStateException("the platoon leader's reply has no answer or no action: " + reply);
        }
        return reply;
    }

    /** The earlier turns as the model's own conversation: the operator's words, and its answers. */
    static List<Message> history(List<Turn> earlier) {
        List<Message> messages = new ArrayList<>();
        for (Turn turn : earlier) {
            messages.add(new UserMessage(turn.operator()));
            messages.add(new AssistantMessage(turn.platoonLeader()));
        }
        return messages;
    }

    /** The operator's turn as the user template's parameters: now, the last decision's situation, and what was said. */
    static PromptContext buildContext(OperatorChatPrompts prompts, String message, Optional<TimeOfDay> time, Flatten flatten,
                                      Optional<String> lastShown) {
        Map<String, Object> params = new HashMap<>();
        params.put("clock", time.map(PlatoonLeaderChat::describe).orElse("The time of day: not known - the server hasn't sent it yet."));
        params.put("flatten", flatten.describe());
        params.put("lastDecision", lastShown.<Object>map(s -> s).orElse(false));
        params.put("message", message.strip());
        return new PromptContext(prompts.user(), params);
    }

    /** The clock as it reads now: the game's time, and dusk and dawn counted down - the lookup done for the model. */
    static String describe(TimeOfDay time) {
        String counts = time.running()
                ? String.format(Locale.ROOT, " Next dusk in %d ticks (%s); next dawn in %d ticks (%s).",
                time.ticksToDusk(), minutes(time.ticksToDusk()), time.ticksToDawn(), minutes(time.ticksToDawn()))
                : "";
        return String.format(Locale.ROOT, "Minecraft time %s (tick %d of the day's %d). %s%s", time.clock(), time.ticks(),
                TimeOfDay.DAY_TICKS, SpringAiPlatoonLeader.describe(time), counts);
    }

    private static String minutes(long ticks) {
        long seconds = ticks / 20;
        return seconds < 60 ? seconds + " s" : String.format(Locale.ROOT, "%d min %02d s", seconds / 60, seconds % 60);
    }
}
