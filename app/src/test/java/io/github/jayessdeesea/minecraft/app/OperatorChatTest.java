package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.OperatorChatPrompts;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderChat;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderPrompts;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiPlatoonLeader;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperatorChatTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final OperatorChatPrompts PROMPTS = OperatorChatPrompts.read(
            new ClassPathResource("prompts/platoon-leader-chat-system-prompt.md"), new ClassPathResource("prompts/platoon-leader-chat-prompt.md"));
    private static final PlatoonLeaderPrompts PLATOON_LEADER = PlatoonLeaderPrompts.read(
            new ClassPathResource("prompts/platoon-leader-system-prompt.md"), new ClassPathResource("prompts/platoon-leader-user-prompt.md"),
            new ClassPathResource("prompts/platoon-leader-squad-prompt.md"));

    /** The platoon leader's model, scripted: each call answered with the next reply - words, or tool calls - every prompt kept. */
    private final List<Prompt> sent = new CopyOnWriteArrayList<>();
    private final Deque<ChatResponse> replies = new ArrayDeque<>();
    private final ChatClient client = ChatClient.create(new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            sent.add(prompt);
            synchronized (replies) {
                return replies.removeFirst();
            }
        }

        /** A provider's options carry tools; a bare model's would drop them. */
        @Override
        public ChatOptions getOptions() {
            return ToolCallingChatOptions.builder().build();
        }
    });

    private void reply(String words) {
        synchronized (replies) {
            replies.add(new ChatResponse(List.of(new Generation(new AssistantMessage(words)))));
        }
    }

    /** The model calling the tool {@code name} with {@code arguments}, as JSON. */
    private void call(String name, String arguments) {
        synchronized (replies) {
            replies.add(new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + replies.size(), "function", name, arguments))).build()))));
        }
    }

    private final SpringAiPlatoonLeader platoonLeader = new SpringAiPlatoonLeader(client, PLATOON_LEADER, "Make camp at x=0 z=0", "");
    private final ChainOfCommand chain = new ChainOfCommand(platoonLeader, ChatClient.create((ChatModel) prompt -> {
        throw new AssertionError("no squad leader's model call expected");
    }), new SquadLeaderPrompts("", ""), new Roster("Alpha:Alice"), true, "drills");
    private final List<String> radio = new CopyOnWriteArrayList<>();
    private final OperatorChat endpoint = new OperatorChat(new PlatoonLeaderChat(client, PROMPTS, platoonLeader, new ChatRegisters(chain),
            new ChatCommands(chain)), chain, 0);
    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        endpoint.destroy();
        chain.destroy();
        http.close();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + endpoint.port() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String type, String body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + endpoint.port() + "/chat"))
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** One of the page's turns: the conversation so far and the new message, answered 200 with the reply. */
    private JsonNode say(List<Map<String, String>> turns, String message) throws IOException, InterruptedException {
        HttpResponse<String> response = post("application/json", JSON.writeValueAsString(Map.of("turns", turns, "message", message)));
        assertEquals(200, response.statusCode(), response.body());
        JsonNode reply = JSON.readTree(response.body());
        turns.add(Map.of("operator", message, "platoonLeader", reply.get("reply").asText()));
        return reply;
    }

    /**
     * Three turns through the endpoint, as the page sends them: the time asked, a farm asked for - given as the farm mission
     * through the operator's own order, its phases said as computed - and a flatten ordered, the mission's flatten first
     * while it's under way. Each turn is on the net, and the last is sent with the two before it.
     */
    @Test
    void aThreeTurnConversation() throws Exception {
        chain.setRadio(radio::add);
        chain.onTime(TimeOfDay.of(6000, true));
        List<Map<String, String>> turns = new ArrayList<>();
        reply("Midday: 12:00. Dusk in 5 min 58 s.");
        call("giveFarmMission", "{\"x\": 40, \"z\": -20}");
        reply("WILCO, a farm at chunk (2, -2).");
        call("setFlattenGround", "{\"x1\": 0, \"z1\": 0, \"x2\": 16, \"z2\": 16, \"y\": 64}");
        reply("WILCO, flattening it.");

        JsonNode first = say(turns, "What time of day is it in Minecraft?");
        JsonNode second = say(turns, "Place a farm at x = 40, z = -20");
        JsonNode third = say(turns, "Then flatten 0 0 16 16 down to 64");

        assertEquals("Midday: 12:00. Dusk in 5 min 58 s.", first.get("reply").asText());
        assertEquals(0, first.get("done").size(), "a question gives no command");
        assertTrue(sent.getFirst().getInstructions().getLast().getText().contains("Minecraft time 12:00 (tick 6000 of the day's 24000)"),
                "the time from the overworld clock, worked out for it");
        assertEquals("WILCO: a farm at chunk (2, -2) at y=63, in 3 phases, each started when the one before is measured done: phase 1, flatten"
                + " chunk (2, -2), x 32..47 z -32..-17 to y=62; phase 2, floor chunk (2, -2), x 32..47 z -32..-17 in cobblestone at y=62; phase 3, the"
                + " farm template on chunk (2, -2), x 32..47 z -32..-17 at y=63.", second.get("done").get(0).asText());
        assertEquals(java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.FarmMission(2, -2, 63)),
                chain.farmMissions.current().map(Map.Entry::getKey), "given through the operator's !farm");
        assertTrue(third.get("done").get(0).asText().startsWith("WILCO: the ground to flatten is "), third.toString());
        assertTrue(third.get("done").get(0).asText().contains("is worked first while it's under way."), third.toString());
        assertEquals(new Flatten(32, -32, 47, -17, 62), chain.flatten(), "the mission's flatten first while it's under way");

        List<Message> last = sent.getLast().getInstructions();
        assertEquals(List.of("What time of day is it in Minecraft?", first.get("reply").asText(), "Place a farm at x = 40, z = -20",
                second.get("reply").asText()), last.subList(1, 5).stream().map(Message::getText).toList(), "the earlier turns go with it");
        assertTrue(last.get(5).getText().contains("Then flatten 0 0 16 16 down to 64"));
        assertTrue(radio.stream().anyMatch(l -> l.contains("[Operator -> PL] What time of day is it in Minecraft?")), radio.toString());
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] Midday: 12:00.")), radio.toString());
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO") && l.contains("flatten")), radio.toString());
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO") && l.contains("a farm at chunk (2, -2) at y=63 - 3 phases")), radio.toString());
    }

    /**
     * "Cancel the farm at (1, 3)": its phases not done closed SUPERSEDED with the operator's reason, none deleted, said on the
     * net - and the registers carried on after a restart don't open it again. A question after it reads the missions and
     * gives nothing; cancelled again, there's none open to cancel.
     */
    @Test
    void cancelTheFarmClosesItsTasksAndARestartKeepsItClosed(@TempDir Path state) throws Exception {
        Registers registers = new Registers(state.resolve("registers"));
        chain.keepObjectives(new ObjectiveBook(() -> registers));
        chain.setRadio(radio::add);
        List<Map<String, String>> turns = new ArrayList<>();
        call("giveFarmMission", "{\"x\": 24, \"z\": 56}");
        reply("WILCO.");
        call("cancelMission", "{\"chunkX\": 1, \"chunkZ\": 3, \"reason\": \"bread enough\"}");
        reply("WILCO, cancelled.");
        call("missions", "{\"which\": \"ALL\"}");
        reply("None open; the farm at chunk (1, 3) was cancelled.");
        call("cancelMission", "{\"chunkX\": 1, \"chunkZ\": 3, \"reason\": \"again\"}");
        reply("No farm is open there.");

        say(turns, "A farm at x=24 z=56");
        JsonNode cancelled = say(turns, "Cancel the farm at (1, 3), we have bread enough");
        JsonNode asked = say(turns, "What missions have there been?");
        JsonNode again = say(turns, "Cancel the farm at (1, 3)");

        assertEquals("WILCO: a farm at chunk (1, 3) at y=63 cancelled - its phases not done closed SUPERSEDED, \"bread enough\" kept as why.",
                cancelled.get("done").get(0).asText());
        List<ObjectiveRecord> rows = registers.objectives();
        assertEquals(3, rows.size(), "none deleted: " + rows);
        assertTrue(rows.stream().allMatch(r -> r.state() == ObjectiveRecord.State.SUPERSEDED
                && r.why().equals(java.util.Optional.of("cancelled by Operator: bread enough"))), rows.toString());
        assertEquals(java.util.Optional.empty(), chain.farmMissions.current(), "no longer worked");
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO") && l.contains("a farm at chunk (1, 3) at y=63 cancelled - bread enough")),
                radio.toString());
        assertEquals(0, asked.get("done").size(), "a question gives no command");
        String missions = sent.get(sent.size() - 3).getInstructions().stream().filter(m -> m instanceof ToolResponseMessage)
                .map(m -> ((ToolResponseMessage) m).getResponses().getLast().responseData()).reduce((x, y) -> y).orElseThrow();
        assertTrue(missions.contains("- a farm at chunk (1, 3) at y=63 - CLOSED, SUPERSEDED: cancelled by Operator: bread enough."), missions);
        assertEquals("CANTCO: no farm mission is open on chunk (1, 3), x 16..31 z 48..63 - nothing cancelled.", again.get("done").get(0).asText());

        assertEquals(Map.of(), new ObjectiveBook(() -> registers).openMissions(), "after a restart, nothing to carry on");
        FarmMissions restarted = new FarmMissions(cause -> { });
        restarted.keptIn(new ObjectiveBook(() -> registers));
        restarted.give(FarmMission.atSeaLevel(5, 5), 9_000);
        assertEquals(FarmMission.atSeaLevel(5, 5), restarted.current().orElseThrow().getKey(), "the cancelled one isn't carried on ahead of a new one");
    }

    /** The clock as JSON for the page's strip: unknown until sent, then the time of day and the ticks to dawn and dusk. */
    @Test
    void theClockAsJson() throws Exception {
        JsonNode before = JSON.readTree(get("/clock").body());
        assertFalse(before.get("known").asBoolean());

        chain.onTime(TimeOfDay.of(18000, true));
        HttpResponse<String> response = get("/clock");

        assertEquals("application/json", response.headers().firstValue("Content-Type").orElseThrow());
        JsonNode clock = JSON.readTree(response.body());
        assertTrue(clock.get("known").asBoolean());
        assertTrue(clock.get("running").asBoolean());
        assertTrue(clock.get("dark").asBoolean());
        assertEquals(18000, clock.get("ticks").asLong());
        assertEquals("00:00", clock.get("clock").asText());
        assertEquals(6000, clock.get("ticksToDawn").asLong());
        assertEquals(6000 + TimeOfDay.DUSK, clock.get("ticksToDusk").asLong());
    }

    @Test
    void thePageIsServedAtTheRoot() throws Exception {
        HttpResponse<String> page = get("/");

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("fetch(\"/clock\""));
        assertFalse(page.body().contains("https://"), "nothing loaded from off the machine");
    }

    /** A form post - what a page from elsewhere can send without asking - is refused, and the model never called. */
    @Test
    void onlyJsonIsTakenAsATurn() throws Exception {
        assertEquals(415, post("text/plain", "{\"message\": \"!mission go\"}").statusCode());
        assertTrue(sent.isEmpty());
    }

    /** A command that can't be carried out as given fails its turn, loud, with nothing given; the next is served. */
    @Test
    void aCommandThatCantBeCarriedOutFailsItsTurnAlone() throws Exception {
        chain.setRadio(radio::add);
        call("setFlattenGround", "{\"x1\": 0, \"z1\": 0, \"x2\": 16, \"z2\": 16, \"y\": 900}");
        reply("Still here.");
        Flatten was = chain.flatten();

        HttpResponse<String> failed = post("application/json", "{\"turns\": [], \"message\": \"flatten it to 900\"}");
        HttpResponse<String> next = post("application/json", "{\"turns\": [], \"message\": \"there?\"}");

        assertEquals(500, failed.statusCode());
        assertTrue(failed.body().contains("900"), failed.body());
        assertEquals(was, chain.flatten());
        assertFalse(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO")), radio.toString());
        assertEquals(200, next.statusCode());
        assertEquals("Still here.", JSON.readTree(next.body()).get("reply").asText());
    }

    @Test
    void onlyThisMachinesNamesAreServed() {
        assertTrue(OperatorChat.local("127.0.0.1:8125"));
        assertTrue(OperatorChat.local("localhost:8125"));
        assertTrue(OperatorChat.local("[::1]:8125"));
        assertFalse(OperatorChat.local("evil.example:8125"), "a name rebound to the loopback address");
        assertFalse(OperatorChat.local(null));
    }
}
