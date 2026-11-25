package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.OperatorCommands;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.PlatoonRegisters;
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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operator's chat with the platoon leader, the model scripted: each reply the next of a script - words, or tool calls -
 * every prompt kept, so what each tool answered the model is checked as it was sent.
 */
class PlatoonLeaderChatTest {

    static final OperatorChatPrompts PROMPTS = OperatorChatPrompts.read(new ClassPathResource("prompts/platoon-leader-chat-system-prompt.md"),
            new ClassPathResource("prompts/platoon-leader-chat-prompt.md"));

    private static final SquadStatus ALPHA = new SquadStatus("Alpha", new Report.Moving(), List.of(
            new SoldierStatus("Alice", new Position(1, 64, 2), new Order.Idle(), new Report.Moving(), Optional.empty())));

    private static final FarmMission FARM = FarmMission.atSeaLevel(1, 3), WRONG = new FarmMission(1, 3, 67);

    /**
     * The registers as a garrison's might hold them: the farm at chunk (1, 3) at its floor, Charlie on it; one at y=67 for the
     * same chunk superseded; Alpha on a task of the platoon leader's own; the chunk flattened.
     */
    private static final PlatoonRegisters REGISTERS = new PlatoonRegisters() {
        @Override
        public List<ObjectiveRecord> tasks() {
            List<ObjectiveRecord> rows = new ArrayList<>();
            for (FarmMission.Step s : WRONG.steps()) {
                rows.add(new ObjectiveRecord(1 + s.index(), WRONG.name(), s.index(), s.task(), 1, 3, Optional.empty(), ObjectiveRecord.State.SUPERSEDED,
                        10_000, Optional.of("superseded by " + FARM.name())));
            }
            List<ObjectiveRecord.State> states = List.of(ObjectiveRecord.State.DONE, ObjectiveRecord.State.UNDER_WAY, ObjectiveRecord.State.PLANNED);
            for (FarmMission.Step s : FARM.steps()) {
                rows.add(new ObjectiveRecord(4 + s.index(), FARM.name(), s.index(), s.task(), 1, 3, s.index() == 1 ? Optional.of("Charlie") : Optional.empty(),
                        states.get(s.index()), 30_000, s.index() == 0 ? Optional.of("nothing over y=62 in the chunk") : Optional.empty()));
            }
            rows.add(new ObjectiveRecord(7, "Secure the camp", 0, "SECURE", 0, 0, Optional.of("Alpha"), ObjectiveRecord.State.UNDER_WAY, 40_000,
                    Optional.of("12 cells to light")));
            return rows;
        }

        @Override
        public List<ChunkRecord> chunks() {
            return List.of(new ChunkRecord(1, 3, ChunkRecord.Kind.FLATTENED, 62, Optional.of("CUT"), 30_000));
        }

        @Override
        public long now() {
            return 70_000;
        }
    };

    /** Commands that fail the test if any is given: a question never gives one. */
    private static final OperatorCommands NO_COMMANDS = (OperatorCommands) java.lang.reflect.Proxy.newProxyInstance(
            OperatorCommands.class.getClassLoader(), new Class<?>[]{OperatorCommands.class}, (proxy, method, args) -> {
                throw new AssertionError("no command expected: " + method.getName() + Arrays.toString(args));
            });

    /** Commands kept as given, each answering as {@code answer} says. */
    private static OperatorCommands recording(List<String> given, Function<String, String> answer) {
        return (OperatorCommands) java.lang.reflect.Proxy.newProxyInstance(OperatorCommands.class.getClassLoader(), new Class<?>[]{OperatorCommands.class},
                (proxy, method, args) -> {
                    String call = method.getName() + Arrays.toString(args);
                    given.add(call);
                    return answer.apply(call);
                });
    }

    /** Answers each call with the next of {@code replies}, keeping every prompt it was sent. */
    static ChatModel scripted(List<Prompt> sent, ChatResponse... replies) {
        Deque<ChatResponse> left = new ArrayDeque<>(List.of(replies));
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                sent.add(prompt);
                return left.removeFirst();
            }

            /** A provider's options carry tools; a bare model's would drop them. */
            @Override
            public ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }
        };
    }

    private static ChatResponse words(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** The model calling each of {@code calls} - a tool's name and its arguments as JSON - at once. */
    private static ChatResponse calls(String... calls) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < calls.length; i += 2) {
            toolCalls.add(new AssistantMessage.ToolCall("call-" + i + "-" + calls[i], "function", calls[i], calls[i + 1]));
        }
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("").toolCalls(toolCalls).build())));
    }

    /** What the tools answered the model in {@code prompt}: each tool's name and its result. */
    private static Map<String, String> answered(Prompt prompt) {
        return prompt.getInstructions().stream().filter(m -> m instanceof ToolResponseMessage).map(m -> (ToolResponseMessage) m)
                .flatMap(m -> m.getResponses().stream()).collect(Collectors.toMap(ToolResponseMessage.ToolResponse::name,
                        ToolResponseMessage.ToolResponse::responseData, (a, b) -> a + "\n" + b));
    }

    private static PlatoonLeaderChat chat(ChatModel model, OperatorCommands commands) {
        ChatClient client = ChatClient.create(model);
        return new PlatoonLeaderChat(client, PROMPTS, new SpringAiPlatoonLeader(client, Prompts.PLATOON_LEADER, "win the war", ""), REGISTERS, commands);
    }

    private static ChatReply ask(PlatoonLeaderChat chat, String message) {
        return chat.answer(List.of(), message, Optional.of(TimeOfDay.of(6000, true)), Flatten.KEEP);
    }

    /**
     * Asked what it's tracking, the platoon leader is shown what it was shown at its last decision - each squad's
     * standing order as its prompt held it - and the clock as it stands now, worked out for it.
     */
    @Test
    void aTurnIsShownTheClockNowAndTheLastDecisionOnlyThroughItsTool() {
        List<Prompt> sent = new ArrayList<>();
        ChatClient client = ChatClient.create(scripted(sent, words("""
                {"objectives": [{"squad": "Alpha", "keep": false, "reason": "", "mission": "scout north", "x": 0, "y": 70, "z": -60,
                 "intent": "find a way around", "task": "NONE"}]}
                """), words("Alpha is moving, no standing order yet.")));
        SpringAiPlatoonLeader platoonLeader = new SpringAiPlatoonLeader(client, Prompts.PLATOON_LEADER, "win the war", "");
        PlatoonLeaderChat chat = new PlatoonLeaderChat(client, PROMPTS, platoonLeader, REGISTERS, NO_COMMANDS);
        platoonLeader.decide(List.of(ALPHA));

        ChatReply reply = chat.answer(List.of(new PlatoonLeaderChat.Turn("Who's out?", "Alpha.")), "What's under way?",
                Optional.of(TimeOfDay.of(6000, true)), Flatten.KEEP);

        assertEquals(new ChatReply("Alpha is moving, no standing order yet.", List.of()), reply);
        List<Message> messages = sent.get(1).getInstructions();
        assertEquals(List.of(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT, MessageType.USER),
                messages.stream().map(Message::getMessageType).toList());
        assertEquals("Who's out?", messages.get(1).getText());
        assertEquals("Alpha.", messages.get(2).getText());
        String user = messages.get(3).getText();
        // Not its last decision in the turn's prompt - shown it live (field684), a model answered from it and read nothing.
        assertTrue(!user.contains("win the war"), "the last decision kept out of the turn's prompt: " + user);
        assertEquals(platoonLeader.lastShown().orElseThrow(), chat.tools("why?").lastDecision(), "the last decision, word for word, its tool's");
        assertTrue(user.contains("Minecraft time 12:00 (tick 6000 of the day's 24000)."), user);
        assertTrue(user.contains("Next dusk in 7178 ticks (5 min 58 s); next dawn in 18000 ticks (15 min 00 s)."), user);
        assertTrue(user.contains("<operator>\nWhat's under way?\n</operator>"), user);
    }

    /** "List the missions": each mission with its operation's phases under it, as computed; nothing given. */
    @Test
    void listTheMissionsGivesEachWithItsPhases() {
        List<Prompt> sent = new ArrayList<>();
        PlatoonLeaderChat chat = chat(scripted(sent, calls("missions", "{}"), words("One mission open: the farm at chunk (1, 3), at its floor.")), NO_COMMANDS);

        ChatReply reply = ask(chat, "List the missions");

        assertEquals(List.of(), reply.done(), "a question gives no command");
        String missions = answered(sent.getLast()).get("missions");
        assertTrue(missions.contains("Missions from the operator, open (1 of 2 kept):"), missions);
        assertTrue(missions.contains("- a farm at chunk (1, 3) at y=63 - OPEN, at phase 2 of 3. Objective: chunk (1, 3), x 16..31 z 48..63."), missions);
        assertTrue(missions.contains("  - phase 1: flatten chunk (1, 3), x 16..31 z 48..63 to y=62 - DONE 40 s ago: nothing over y=62 in the chunk (task #4)"), missions);
        assertTrue(missions.contains("  - phase 2: floor chunk (1, 3), x 16..31 z 48..63 in cobblestone at y=62 - UNDER_WAY, Charlie for 40 s (task #5)"), missions);
        assertTrue(missions.contains("  - phase 3: the farm template on chunk (1, 3), x 16..31 z 48..63 at y=63 - PLANNED, no squad yet (task #6)"), missions);
        assertTrue(!missions.contains("y=67"), "the superseded one isn't open: " + missions);
        assertTrue(missions.contains("- task #7: Secure the camp (SECURE) - UNDER_WAY, Alpha for 30 s: 12 cells to light."), missions);
    }

    /** "What objectives are open for Charlie": the tasks, filtered - "objectives" read as the squads' tasks. */
    @Test
    void openObjectivesForASquadAreItsTasks() {
        List<Prompt> sent = new ArrayList<>();
        PlatoonLeaderChat chat = chat(scripted(sent, calls("tasks", "{\"squad\": \"charlie\", \"state\": \"OPEN\"}"),
                words("Charlie: task #5, the floor of the farm at chunk (1, 3).")), NO_COMMANDS);

        ask(chat, "What objectives are open for Charlie?");

        assertEquals("1 task(s) open for charlie:\n- task #5: a farm at chunk (1, 3) at y=63, phase 2 of 3: floor chunk (1, 3), x 16..31 z 48..63"
                + " in cobblestone at y=62 - UNDER_WAY, Charlie for 40 s. Objective: chunk (1, 3), x 16..31 z 48..63.", answered(sent.getLast()).get("tasks"));
    }

    /** "What's at chunk (1, 3)" and "what chunk is x=20 z=50 in": the chunk as kept, and the block's chunk worked out. */
    @Test
    void aChunkAndTheChunkABlockIsIn() {
        List<Prompt> sent = new ArrayList<>();
        PlatoonLeaderChat chat = chat(scripted(sent, calls("chunk", "{\"chunkX\": 1, \"chunkZ\": 3}", "chunkOfBlock", "{\"x\": 20, \"z\": 50}"),
                words("Chunk (1, 3) is flattened to y=62; x=20 z=50 is in it.")), NO_COMMANDS);

        ask(chat, "What's at chunk (1, 3), and what chunk is x=20 z=50 in?");

        Map<String, String> answered = answered(sent.getLast());
        assertTrue(answered.get("chunk").startsWith("chunk (1, 3), x 16..31 z 48..63: FLATTENED at y=62, CUT, measured 40 s ago.\nTasks open on it:\n- task #5:"),
                answered.get("chunk"));
        assertTrue(answered.get("chunkOfBlock").startsWith("Block x=20 z=50 is in chunk (1, 3).\nchunk (1, 3), x 16..31 z 48..63: FLATTENED"), answered.get("chunkOfBlock"));
    }

    /** A chunk nothing is kept of is said so, for the model to tell the operator. */
    @Test
    void aChunkNotKeptIsSaidSo() {
        List<Prompt> sent = new ArrayList<>();
        PlatoonLeaderChat chat = chat(scripted(sent, calls("chunk", "{\"chunkX\": 9, \"chunkZ\": 9}"), words("Nothing at chunk (9, 9).")), NO_COMMANDS);

        ask(chat, "What's at chunk (9, 9)?");

        assertEquals("Nothing is kept of chunk (9, 9), x 144..159 z 144..159: the force has made nothing of it that's measured.\nNo task is open on it.",
                answered(sent.getLast()).get("chunk"));
    }

    /** "Cancel the farm at (1, 3)": the command given with the operator's reason, what it did in the reply's done. */
    @Test
    void cancelTheFarmGivesTheCancel() {
        List<Prompt> sent = new ArrayList<>();
        List<String> given = new ArrayList<>();
        PlatoonLeaderChat chat = chat(scripted(sent, calls("cancelMission", "{\"chunkX\": 1, \"chunkZ\": 3, \"reason\": \"we have bread enough\"}"),
                words("WILCO, the farm at chunk (1, 3) is cancelled.")), recording(given, call -> "WILCO: a farm at chunk (1, 3) at y=63 cancelled"));

        ChatReply reply = ask(chat, "Cancel the farm at (1, 3), we have bread enough");

        assertEquals(List.of("cancelMission[1, 3, we have bread enough]"), given);
        assertEquals(List.of("WILCO: a farm at chunk (1, 3) at y=63 cancelled"), reply.done());
        assertEquals("WILCO: a farm at chunk (1, 3) at y=63 cancelled", answered(sent.getLast()).get("cancelMission"));
    }

    /** A farm's level the operator didn't say is dropped - sea level - and one said is kept (field668). */
    @Test
    void aFarmsLevelIsTheOperatorsOrSeaLevel() {
        List<String> given = new ArrayList<>();
        PlatoonLeaderChat chat = chat(scripted(new ArrayList<>(), calls("giveFarmMission", "{\"x\": 24, \"z\": 56, \"y\": 67}"), words("WILCO.")),
                recording(given, call -> "WILCO"));

        ask(chat, "Create a farm at chunk (1, 3): x=24 z=56, at sea level.");

        assertEquals(List.of("giveFarmMission[24, 56, OptionalInt.empty]"), given);
        assertEquals(OptionalInt.of(70), PlatoonLeaderTools.levelAsSaid(70, "a farm at x=24 z=56 at y=70"));
        assertEquals(OptionalInt.empty(), PlatoonLeaderTools.levelAsSaid(6, "a farm at x=24 z=56"), "a digit inside a coordinate isn't a level said");
        assertEquals(OptionalInt.empty(), PlatoonLeaderTools.levelAsSaid(null, "a farm at x=24 z=56 at y=70"));
    }

    /** A command that can't be carried out as given fails the turn, loud - not handed back to the model to guess round. */
    @Test
    void aCommandThatFailsFailsTheTurn() {
        PlatoonLeaderChat chat = chat(scripted(new ArrayList<>(), calls("giveFarmMission", "{\"x\": 24, \"z\": 56, \"y\": 900}"), words("WILCO.")),
                recording(new ArrayList<>(), call -> {
                    throw new IllegalArgumentException("its level must be inside the world");
                }));

        RuntimeException failed = assertThrows(RuntimeException.class, () -> ask(chat, "A farm at x=24 z=56 at y=900"));
        assertTrue(String.valueOf(failed.getMessage()).contains("inside the world") || String.valueOf(failed.getCause()).contains("inside the world"),
                failed.toString());
    }

    /** "Missions" and "objectives" both understood: the terms in the system prompt and in the tools' own descriptions. */
    @Test
    void missionsAndObjectivesAreBothUnderstood() {
        String system = PROMPTS.system();
        assertTrue(system.contains("A mission is a task with its purpose"), system);
        assertTrue(system.contains("An operation is the whole carried out for one mission. Its stages are phases"), system);
        assertTrue(system.contains("A task is what you assign a squad"), system);
        assertTrue(system.contains("An objective is a location an operation is oriented on"), system);
        assertTrue(system.contains("loosely, \"objectives\" for the squads' tasks"), system);
        assertTrue(system.contains("Use a command only when the operator asks for it in this turn"), system);

        Map<String, ToolDefinition> tools = Arrays.stream(ToolCallbacks.from(chat(scripted(new ArrayList<>()), NO_COMMANDS).tools("")))
                .map(ToolCallback::getToolDefinition).collect(Collectors.toMap(ToolDefinition::name, d -> d));
        assertEquals(List.of("cancelMission", "chunk", "chunkOfBlock", "chunks", "escort", "giveAnimalFarmMission", "giveFarmMission", "giveMineshaftMission", "giveScoutMission", "giveSupplyPointMission", "giveTemplateMission", "giveTreeFarmMission", "inventory", "lastDecision", "missions", "replacePlatoonMission",
                "setFlattenGround", "stores", "task", "tasks"), tools.keySet().stream().sorted().toList());
        assertTrue(tools.get("missions").description().contains("when the operator asks about missions or operations"));
        assertTrue(tools.get("tasks").description().contains("or about 'objectives' meaning the squads' assignments"));
        for (String command : List.of("cancelMission", "escort", "giveAnimalFarmMission", "giveFarmMission", "giveMineshaftMission", "giveScoutMission", "giveSupplyPointMission", "giveTemplateMission", "giveTreeFarmMission", "replacePlatoonMission", "setFlattenGround")) {
            assertTrue(tools.get(command).description().startsWith("A command - only when the operator asks for it in this turn."), command);
        }
        assertTrue(tools.get("giveFarmMission").inputSchema().contains("\"y\""), tools.get("giveFarmMission").inputSchema());
    }

    @Test
    void beforeAnyDecisionItSaysNothingHasBeenShown() {
        String user = PlatoonLeaderChat.buildContext(PROMPTS, "status?", Optional.empty(), Flatten.KEEP).prompt();

        assertTrue(!user.contains("last_decision") && !user.contains("standing order"), "nothing of the books in the turn's prompt: " + user);
        assertTrue(user.contains("The time of day: not known"), user);
    }

    @Test
    void aStoppedClockCountsNothingDown() {
        assertEquals("Minecraft time 18:00 (tick 12000 of the day's 24000). Day. The clock is stopped: it stays this way.",
                PlatoonLeaderChat.describe(TimeOfDay.of(12000, false)));
    }

    /**
     * The operator asks what people carry and what the force has (2026-11-22): the inventory tool gives a line a soldier,
     * its items by class - narrowed by a soldier's name or a squad's - and the stores tool what every chest known holds.
     */
    @Test
    void theInventoryAndStoresToolsSayWhatIsCarriedAndHeld() {
        PlatoonRegisters kept = new PlatoonRegisters() {
            @Override
            public List<ObjectiveRecord> tasks() {
                return List.of();
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.command.ChunkRecord> chunks() {
                return List.of();
            }

            @Override
            public long now() {
                return 0;
            }

            @Override
            public Map<String, Map<String, Integer>> carried() {
                Map<String, Map<String, Integer>> all = new java.util.LinkedHashMap<>();
                all.put("Alice (Alpha)", new java.util.TreeMap<>(Map.of("pickaxe", 2, "water_bucket", 1, "cobblestone", 64)));
                all.put("Dana (Bravo)", Map.of("dirt", 22));
                return all;
            }

            @Override
            public Map<String, Integer> stores() {
                Map<String, Integer> held = new java.util.LinkedHashMap<>();
                held.put("logs", 1013);
                held.put("rawIron", 0);
                held.put("ironIngots", 7);
                return held;
            }
        };
        PlatoonLeaderTools tools = new PlatoonLeaderTools(kept, NO_COMMANDS, "", Optional::empty);

        assertEquals("2 soldier(s):\n- Alice (Alpha): cobblestone 64, pickaxe 2, water bucket 1\n- Dana (Bravo): dirt 22", tools.inventory(null));
        assertEquals("1 soldier(s):\n- Dana (Bravo): dirt 22", tools.inventory("bravo"));
        assertEquals("1 soldier(s):\n- Alice (Alpha): cobblestone 64, pickaxe 2, water bucket 1", tools.inventory("Alice"));
        assertEquals("No soldier present is named, or is in a squad named, 'Zed'.", tools.inventory("Zed"));
        assertEquals("In every chest known, in all: logs 1013, raw iron 0, iron ingots 7.", tools.stores());
    }
}
