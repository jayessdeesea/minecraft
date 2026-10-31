package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@link General} whose strategic decisions come from an LLM call - same
 * {@link #buildContext} (pure) -&gt; {@link #callModel} (impure) -&gt; {@link #parseObjectives}
 * (pure) split {@link SpringAiSquadLeader} uses, per AGENTS.md's "Behavior modeling" section.
 *
 * <p>A {@code @Component}, unlike {@link SpringAiSquadLeader}: exactly one {@code General} exists
 * project-wide (AGENTS.md's Objective section), so unlike a per-squad squad leader, a single
 * autowired instance is the correct shape.
 */
@Component
public final class SpringAiGeneral implements General {

    private final ChatClient chatClient;

    public SpringAiGeneral(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * An empty squad list has nothing to command - short-circuits before touching the model, both
     * to avoid a pointless call and so this path is testable without one.
     */
    @Override
    public List<Objective> decide(List<SquadStatus> squadStatuses) {
        if (squadStatuses.isEmpty()) {
            return List.of();
        }

        PromptContext context = buildContext(squadStatuses);
        LlmResponse response = callModel(context);
        return parseObjectives(response, squadStatuses);
    }

    static PromptContext buildContext(List<SquadStatus> squadStatuses) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the general commanding several squads of Minecraft player bots.\n");
        prompt.append("Decide a strategic objective for each squad based on its current status.\n\n");
        prompt.append("Current squad status:\n");
        for (SquadStatus squad : squadStatuses) {
            prompt.append("- ").append(squad.squadName()).append(": ").append(squad.soldiers().size())
                    .append(" soldier(s)");
            for (SoldierStatus soldier : squad.soldiers()) {
                prompt.append("; ").append(soldier.name()).append(" at ").append(describe(soldier.position()));
            }
            prompt.append('\n');
        }
        prompt.append("\nRespond with exactly one line per squad, in this exact order, and no other text\n");
        prompt.append("or commentary. Each line must be exactly:\n");
        prompt.append("<squadName>: <objective, a single line of plain text with no colons>\n");
        return new PromptContext(prompt.toString());
    }

    LlmResponse callModel(PromptContext context) {
        return new LlmResponse(chatClient.prompt(context.prompt()).call().content());
    }

    static List<Objective> parseObjectives(LlmResponse response, List<SquadStatus> squadStatuses) {
        List<String> lines = response.text().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        if (lines.size() != squadStatuses.size()) {
            throw new IllegalStateException("expected " + squadStatuses.size() + " objective line(s), got "
                    + lines.size() + " - raw response: " + response.text());
        }

        List<Objective> objectives = new ArrayList<>(squadStatuses.size());
        for (int i = 0; i < squadStatuses.size(); i++) {
            String expectedName = squadStatuses.get(i).squadName();
            String line = lines.get(i);
            String prefix = expectedName + ":";
            if (!line.startsWith(prefix)) {
                throw new IllegalStateException("expected line " + i + " to start with \"" + prefix
                        + "\", got \"" + line + "\" - raw response: " + response.text());
            }
            String description = line.substring(prefix.length()).trim();
            if (description.isEmpty()) {
                throw new IllegalStateException("empty objective in line \"" + line
                        + "\" - raw response: " + response.text());
            }
            objectives.add(new Objective(description));
        }
        return objectives;
    }

    private static String describe(Position position) {
        return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", position.x(), position.y(), position.z());
    }
}
