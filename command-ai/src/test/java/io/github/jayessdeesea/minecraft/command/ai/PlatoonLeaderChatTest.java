package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatoonLeaderChatTest {

    static final OperatorChatPrompts PROMPTS = OperatorChatPrompts.read(new ClassPathResource("prompts/platoon-leader-chat-system-prompt.md"),
            new ClassPathResource("prompts/platoon-leader-chat-prompt.md"));

    private static final SquadStatus ALPHA = new SquadStatus("Alpha", new Report.Moving(), List.of(
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle(), new Report.Moving(), Optional.empty())));

    /** Answers each call with the next of {@code replies}, keeping every prompt it was sent. */
    private static ChatModel scripted(List<Prompt> sent, String... replies) {
        Deque<String> left = new ArrayDeque<>(List.of(replies));
        return prompt -> {
            sent.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(left.removeFirst()))));
        };
    }

    /**
     * Asked what it's tracking, the platoon leader is shown what it was shown at its last decision - each squad's
     * standing order as its prompt held it - and the clock as it stands now, worked out for it.
     */
    @Test
    void aTurnIsShownTheLastDecisionAndTheClockNow() {
        List<Prompt> sent = new ArrayList<>();
        ChatClient client = ChatClient.create(scripted(sent,
                """
                {"objectives": [{"squad": "Alpha", "keep": false, "reason": "", "mission": "scout north", "x": 0, "y": 70, "z": -60,
                 "intent": "find a way around", "task": "NONE"}]}
                """,
                """
                {"reply": "Alpha is moving, no standing order yet.", "action": "NONE", "argument": ""}
                """));
        SpringAiPlatoonLeader platoonLeader = new SpringAiPlatoonLeader(client, Prompts.PLATOON_LEADER, "win the war", "");
        PlatoonLeaderChat chat = new PlatoonLeaderChat(client, PROMPTS, platoonLeader);
        platoonLeader.decide(List.of(ALPHA));

        ChatReply reply = chat.answer(List.of(new PlatoonLeaderChat.Turn("Who's out?", "Alpha.")), "What objectives are you tracking?",
                Optional.of(TimeOfDay.of(6000, true)), Flatten.KEEP, Optional.of("Objectives open, as each squad last reported:\n- Alpha: scout north (NONE) in chunk (0, -4) - UNDER_WAY for 5 s"));

        assertEquals(new ChatReply("Alpha is moving, no standing order yet.", ChatReply.Action.NONE, ""), reply);
        List<Message> messages = sent.get(1).getInstructions();
        assertEquals(List.of(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER),
                messages.stream().map(Message::getMessageType).toList());
        assertEquals("Who's out?", messages.get(1).getText());
        assertEquals("Alpha.", messages.get(2).getText());
        String user = messages.get(3).getText();
        assertTrue(user.contains(platoonLeader.lastShown().orElseThrow()), user);
        assertTrue(user.contains("win the war"), "the mission, as the last decision was shown it");
        assertTrue(user.contains("Minecraft time 12:00 (tick 6000 of the day's 24000)."), user);
        assertTrue(user.contains("Next dusk in 7178 ticks (5 min 58 s); next dawn in 18000 ticks (15 min 00 s)."), user);
        assertTrue(user.contains("<operator>\nWhat objectives are you tracking?\n</operator>"), user);
        assertTrue(user.contains("- Alpha: scout north (NONE) in chunk (0, -4) - UNDER_WAY for 5 s"), "its registers, as they stand: " + user);
    }

    @Test
    void beforeAnyDecisionItSaysNothingHasBeenShown() {
        String user = PlatoonLeaderChat.buildContext(PROMPTS, "status?", Optional.empty(), Flatten.KEEP, Optional.empty(), Optional.empty()).prompt();

        assertTrue(user.contains("You haven't decided yet"), user);
        assertTrue(user.contains("The time of day: not known"), user);
    }

    @Test
    void aStoppedClockCountsNothingDown() {
        assertEquals("Minecraft time 18:00 (tick 12000 of the day's 24000). Day. The clock is stopped: it stays this way.",
                PlatoonLeaderChat.describe(TimeOfDay.of(12000, false)));
    }
}
