package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ChunkRecord;
import io.github.jayessdeesea.minecraft.command.FarmMission;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.ObjectiveRecord;
import io.github.jayessdeesea.minecraft.command.OperatorCommands;
import io.github.jayessdeesea.minecraft.command.PlatoonRegisters;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.ChatReply;
import io.github.jayessdeesea.minecraft.command.ai.OperatorChatPrompts;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderChat;
import io.github.jayessdeesea.minecraft.command.ai.PlatoonLeaderPrompts;
import io.github.jayessdeesea.minecraft.command.ai.SpringAiPlatoonLeader;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The operator's chat evaluated against a real local model (operator, 2026-11-22: probe the chat and make sure it works as
 * expected - it needs evals): the real chat prompts and tools over a real register, seeded with an operator's farm
 * mission, asked the live probe's questions. Each is held to what correct is - its answer read from the tools (at least
 * one read), the facts the register holds said, and no command given for a question. Live (field684), the first probe had
 * every question answered from the prompt with no tool read, and the operator's own mission left out. Reports a pass rate
 * rather than asserting one, so it's an {@code *IT}, run on demand. Needs Ollama (DEVELOPMENT.md § 1), not the server.
 *
 * <pre>mvn -pl app -am test -Dtest=OperatorChatScenarioIT -Dsurefire.failIfNoSpecifiedTests=false [-Deval.runs=3] [-Deval.model=qwen3:14b]</pre>
 */
class OperatorChatScenarioIT {

    /** A question, and what its answer must say - each, case aside. */
    record Question(String asked, List<String> says) {
    }

    static final List<Question> QUESTIONS = List.of(
            new Question("list the missions", List.of("chunk (1, 3)")),
            new Question("what is at chunk (1, 3)?", List.of("farm")),
            new Question("what chunk is x=20 z=50 in?", List.of("(1, 3)")),
            new Question("what tasks are open?", List.of("flatten")));

    @TempDir
    Path state;

    @Test
    void evaluate() {
        String model = System.getProperty("eval.model", "qwen3:14b");
        int runs = Integer.getInteger("eval.runs", 3);
        ChatModel ollama = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl("http://localhost:11434").build())
                .options(OllamaChatOptions.builder().model(model).temperature(0.3).disableThinking().build())
                .build();
        ChatClient client = ChatClient.builder(ollama).build();
        SpringAiPlatoonLeader platoonLeader = new SpringAiPlatoonLeader(client, PlatoonLeaderPrompts.read(
                new ClassPathResource("prompts/platoon-leader-system-prompt.md"), new ClassPathResource("prompts/platoon-leader-user-prompt.md"),
                new ClassPathResource("prompts/platoon-leader-squad-prompt.md")), "Make camp at x=24 z=88", "");
        ChainOfCommand chain = new ChainOfCommand(platoonLeader, ChatClient.create((ChatModel) prompt -> {
            throw new AssertionError("no squad leader's model call expected");
        }), new SquadLeaderPrompts("", ""), new Roster("Alpha:Alice;Charlie:Ivy"), true, "drills", "model");
        Registers registers = new Registers(state.resolve("registers"));
        chain.keepObjectives(new ObjectiveBook(() -> registers));
        chain.onOperatorFarm("Pat", FarmMission.atSeaLevel(1, 3));
        AtomicInteger reads = new AtomicInteger();
        ChatRegisters books = new ChatRegisters(chain);
        registers.put(new ChunkRecord(1, 3, ChunkRecord.Kind.FLATTENED, 62, Optional.of("under way"), books.now() - 60_000));
        PlatoonRegisters counted = new PlatoonRegisters() {
            @Override
            public List<ObjectiveRecord> tasks() {
                reads.incrementAndGet();
                return books.tasks();
            }

            @Override
            public List<ChunkRecord> chunks() {
                reads.incrementAndGet();
                return books.chunks();
            }

            @Override
            public long now() {
                return books.now();
            }
        };
        List<String> commanded = new ArrayList<>();
        OperatorCommands none = new OperatorCommands() {
            @Override
            public String giveFarmMission(int x, int z, OptionalInt level) {
                commanded.add("giveFarmMission");
                return "not given: a question";
            }

            @Override
            public String giveTreeFarmMission(int x, int z, OptionalInt level) {
                commanded.add("giveTreeFarmMission");
                return "not given: a question";
            }

            @Override
            public String giveSupplyPointMission(int x, int z, OptionalInt level) {
                commanded.add("giveSupplyPointMission");
                return "not given: a question";
            }

            @Override
            public String giveMineshaftMission(int x, int z, OptionalInt level) {
                commanded.add("giveMineshaftMission");
                return "not given: a question";
            }

            @Override
            public String cancelMission(int chunkX, int chunkZ, String reason) {
                commanded.add("cancelMission");
                return "not cancelled: a question";
            }

            @Override
            public String replacePlatoonMission(String mission) {
                commanded.add("replacePlatoonMission");
                return "not replaced: a question";
            }

            @Override
            public String escort(String player) {
                commanded.add("escort");
                return "not escorted: a question";
            }

            @Override
            public String setFlattenGround(Flatten ground) {
                commanded.add("setFlattenGround");
                return "not set: a question";
            }
        };
        PlatoonLeaderChat chat = new PlatoonLeaderChat(client, OperatorChatPrompts.read(
                new ClassPathResource("prompts/platoon-leader-chat-system-prompt.md"), new ClassPathResource("prompts/platoon-leader-chat-prompt.md")),
                platoonLeader, counted, none);
        int passed = 0;
        int asked = 0;
        for (Question q : QUESTIONS) {
            for (int run = 0; run < runs; run++) {
                int before = reads.get();
                commanded.clear();
                ChatReply reply = chat.answer(List.of(), q.asked(), Optional.of(TimeOfDay.of(6000, true)), Flatten.KEEP);
                int read = reads.get() - before;
                List<String> missing = q.says().stream().filter(s -> !reply.reply().toLowerCase(Locale.ROOT).contains(s.toLowerCase(Locale.ROOT))).toList();
                boolean correct = read > 0 && missing.isEmpty() && commanded.isEmpty() && reply.done().isEmpty();
                asked++;
                passed += correct ? 1 : 0;
                System.out.printf(Locale.ROOT, "%s %-32s run %d: %d read(s), missing %s, commands %s - %s%n", correct ? "PASS" : "FAIL", q.asked(),
                        run + 1, read, missing, commanded, reply.reply().replace('\n', ' ').substring(0, Math.min(160, reply.reply().length())));
            }
        }
        System.out.printf(Locale.ROOT, "operator chat on %s: %d of %d answered from the tools, right, with no command%n", model, passed, asked);
        chain.destroy();
    }
}
