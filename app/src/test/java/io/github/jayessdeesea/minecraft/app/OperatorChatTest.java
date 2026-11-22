package io.github.jayessdeesea.minecraft.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.OperatorChatPrompts;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderChat;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderPrompts;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiPlatoonLeader;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    /** The platoon leader's model, scripted: each call answered with the next reply, every prompt kept. */
    private final List<Prompt> sent = new CopyOnWriteArrayList<>();
    private final Deque<String> replies = new ArrayDeque<>();
    private final ChatClient client = ChatClient.create((ChatModel) prompt -> {
        sent.add(prompt);
        synchronized (replies) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(replies.removeFirst()))));
        }
    });
    private final SpringAiPlatoonLeader platoonLeader = new SpringAiPlatoonLeader(client, PLATOON_LEADER, "Make camp at x=0 z=0", "");
    private final ChainOfCommand chain = new ChainOfCommand(platoonLeader, ChatClient.create((ChatModel) prompt -> {
        throw new AssertionError("no squad leader's model call expected");
    }), new SquadLeaderPrompts("", ""), new Roster("Alpha:Alice"), true, "drills");
    private final List<String> radio = new CopyOnWriteArrayList<>();
    private final OperatorChat endpoint = new OperatorChat(new PlatoonLeaderChat(client, PROMPTS, platoonLeader), chain, 0);
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
     * Three turns through the endpoint, as the page sends them: the time asked, a farm asked for - given as the farm mission,
     * its steps said - and a flatten ordered, given through the operator's own order, the mission's flatten first while
     * it's under way. Each turn is on the net, and the last is sent with the two before it.
     */
    @Test
    void aThreeTurnConversation() throws Exception {
        chain.setRadio(radio::add);
        chain.onTime(TimeOfDay.of(6000, true));
        List<Map<String, String>> turns = new ArrayList<>();
        synchronized (replies) {
            replies.add("{\"reply\": \"Midday: 12:00. Dusk in 5 min 58 s.\", \"action\": \"NONE\", \"argument\": \"\"}");
            replies.add("{\"reply\": \"WILCO, a farm there.\", \"action\": \"FARM\", \"argument\": \"40 -20\"}");
            replies.add("{\"reply\": \"WILCO, flattening it.\", \"action\": \"FLATTEN\", \"argument\": \"0 0 16 16 64\"}");
        }

        JsonNode first = say(turns, "What time of day is it in Minecraft?");
        JsonNode second = say(turns, "Place a farm at x = 40, z = -20");
        JsonNode third = say(turns, "Then flatten 0 0 16 16 down to 64");

        assertEquals("Midday: 12:00. Dusk in 5 min 58 s.", first.get("reply").asText());
        assertEquals("NONE", first.get("action").asText());
        assertTrue(sent.getFirst().getInstructions().getLast().getText().contains("Minecraft time 12:00 (tick 6000 of the day's 24000)"),
                "the time from the overworld clock, worked out for it");
        assertEquals("WILCO, a farm there. a farm at chunk (2, -2) at y=63: flatten chunk (2, -2), x 32..47 z -32..-17 to y=62, then floor"
                + " chunk (2, -2), x 32..47 z -32..-17 in cobblestone at y=62, then the farm template on chunk (2, -2), x 32..47 z -32..-17 at y=63.",
                second.get("reply").asText());
        assertEquals(java.util.Optional.of(new io.github.jayessdeesea.minecraft.command.FarmMission(2, -2, 63)),
                chain.farmMissions.current().map(Map.Entry::getKey), "given through the operator's !farm");
        assertEquals("FLATTEN", third.get("action").asText());
        assertEquals(new Flatten(32, -32, 47, -17, 62), chain.flatten(), "the mission's flatten first while it's under way");

        List<Message> last = sent.getLast().getInstructions();
        assertEquals(List.of("What time of day is it in Minecraft?", first.get("reply").asText(), "Place a farm at x = 40, z = -20",
                second.get("reply").asText()), last.subList(1, 5).stream().map(Message::getText).toList(), "the earlier turns go with it");
        assertTrue(last.get(5).getText().contains("Then flatten 0 0 16 16 down to 64"));
        assertTrue(radio.stream().anyMatch(l -> l.contains("[Operator -> PL] What time of day is it in Minecraft?")), radio.toString());
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] Midday: 12:00.")), radio.toString());
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO") && l.contains("flatten")), radio.toString());
        assertTrue(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO") && l.contains("a farm at chunk (2, -2) at y=63 - 3 steps")), radio.toString());
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

    /** A reply whose action can't be read fails its turn, loud, with nothing said or given; the next is served. */
    @Test
    void anUnreadableActionFailsItsTurnAlone() throws Exception {
        chain.setRadio(radio::add);
        synchronized (replies) {
            replies.add("{\"reply\": \"WILCO.\", \"action\": \"FLATTEN\", \"argument\": \"the hill\"}");
            replies.add("{\"reply\": \"Still here.\", \"action\": \"NONE\", \"argument\": \"\"}");
        }
        Flatten was = chain.flatten();

        HttpResponse<String> failed = post("application/json", "{\"turns\": [], \"message\": \"flatten the hill\"}");
        HttpResponse<String> next = post("application/json", "{\"turns\": [], \"message\": \"there?\"}");

        assertEquals(500, failed.statusCode());
        assertTrue(JSON.readTree(failed.body()).get("error").asText().contains("the hill"), failed.body());
        assertEquals(was, chain.flatten());
        assertFalse(radio.stream().anyMatch(l -> l.contains("[PL -> Operator] WILCO.")), radio.toString());
        assertEquals(200, next.statusCode());
    }

    /**
     * A FARM's level kept only if the operator said it, else sea level: live (field668), told "at sea level", the model gave
     * y=67 and the mission was to flatten a chunk to 66.
     */
    @Test
    void aFarmsLevelIsTheOperatorsOrSeaLevel() {
        assertEquals("24 56", OperatorChat.levelAsSaid("24 56 67", "Create a farm at chunk (1, 3): x=24 z=56, at sea level."));
        assertEquals("24 56 70", OperatorChat.levelAsSaid("24 56 70", "a farm at x=24 z=56 at y=70"));
        assertEquals("24 56", OperatorChat.levelAsSaid("24 56", "a farm at x=24 z=56"));
        assertEquals("24 56", OperatorChat.levelAsSaid("24 56 6", "a farm at x=24 z=56"), "a digit inside a coordinate isn't a level said");
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
