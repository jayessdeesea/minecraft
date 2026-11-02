package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Logstat;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@link General}: same {@link #buildContext} (pure) -&gt; {@link #callModel} (impure) -&gt;
 * {@link #toObjectives} (pure validation) split as {@link SpringAiSquadLeader}. Its own orders come
 * from the operator - the one piece of intent that originates outside the chain of command:
 * {@code minecraft.general.mission} at start, replaced by {@link #receiveMission} during a run.
 *
 * <p>A {@code @Component}: exactly one general exists (AGENTS.md's Objective section).
 */
@Component
public final class SpringAiGeneral implements General {

    private final ChatClient chatClient;
    private volatile String mission; // written by the operator's thread, read by the general's

    public SpringAiGeneral(@Qualifier("generalChatClient") ChatClient chatClient,
                           @Value("${minecraft.general.mission}") String mission) {
        this.chatClient = chatClient;
        this.mission = mission;
    }

    @Override
    public void receiveMission(String mission) {
        if (mission == null || mission.isBlank()) {
            throw new IllegalArgumentException("an empty mission");
        }
        this.mission = mission;
    }

    /** No squads, nothing to command - returns without calling the model. */
    @Override
    public List<Objective> decide(List<SquadStatus> squadStatuses) {
        if (squadStatuses.isEmpty()) {
            return List.of();
        }
        return toObjectives(callModel(buildContext(mission, squadStatuses)), squadStatuses);
    }

    static PromptContext buildContext(String mission, List<SquadStatus> squadStatuses) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<mission>\n").append(mission).append("\n</mission>\n\n");
        for (SquadStatus squad : squadStatuses) {
            prompt.append("<squad name=\"").append(squad.squadName()).append("\">\n")
                    .append("Squad leader reports: ").append(SpringAiSquadLeader.describe(squad.report())).append('\n');
            for (SoldierStatus soldier : squad.soldiers()) {
                prompt.append("- ").append(soldier.name()).append(" at ").append(SpringAiSquadLeader.describe(soldier.position()))
                        .append(": ").append(SpringAiSquadLeader.describe(soldier.report())).append('\n');
            }
            prompt.append(describe(Logstat.of(squad.soldiers()))).append('\n');
            prompt.append("</squad>\n\n");
        }
        prompt.append("Give each squad its objective now.");
        return new PromptContext(prompt.toString());
    }

    /** The LOGSTAT line: what the squad is short of, by class of supply. */
    static String describe(Logstat l) {
        if (l.reporting() == 0) {
            return "LOGSTAT: no soldier has reported his condition yet.";
        }
        StringBuilder line = new StringBuilder("LOGSTAT: ");
        line.append(String.format(Locale.ROOT, "food - %d of %d hungry (not regenerating health); ", l.hungry(), l.reporting()));
        if (l.kitReported() == 0) {
            line.append("weapons and armour not reported yet; ");
        } else {
            line.append(String.format(Locale.ROOT, "weapons - %d of %d bare-handed, %d arrows; armour - %d of %d slots empty; ",
                    l.unarmed(), l.kitReported(), l.arrows(), l.armourEmpty(), l.armourSlots()));
        }
        line.append(String.format(Locale.ROOT, "casualties - %d below half health, %d deaths so far.", l.wounded(), l.deaths()));
        return line.toString();
    }

    GeneralReply callModel(PromptContext context) {
        return chatClient.prompt()
                .user(context.prompt())
                .call()
                .entity(GeneralReply.class, spec -> spec.useProviderStructuredOutput().validateSchema());
    }

    /** One objective per listed squad, in the same order - anything else is a violated contract. */
    static List<Objective> toObjectives(GeneralReply reply, List<SquadStatus> squadStatuses) {
        if (reply == null || reply.objectives() == null) {
            throw new IllegalStateException("general model returned no objectives");
        }
        Map<String, GeneralReply.ObjectiveReply> bySquad = new HashMap<>();
        for (GeneralReply.ObjectiveReply objective : reply.objectives()) {
            if (bySquad.put(objective.squad(), objective) != null) {
                throw new IllegalStateException("two objectives for squad " + objective.squad() + ": " + reply);
            }
        }
        List<Objective> objectives = new ArrayList<>(squadStatuses.size());
        for (SquadStatus squad : squadStatuses) {
            GeneralReply.ObjectiveReply objective = bySquad.get(squad.squadName());
            if (objective == null) {
                throw new IllegalStateException("no objective for squad " + squad.squadName() + ": " + reply);
            }
            if (!Double.isFinite(objective.x()) || !Double.isFinite(objective.y()) || !Double.isFinite(objective.z())
                    || objective.y() < -64 || objective.y() > 320) {
                throw new IllegalStateException("objective outside the world for squad " + squad.squadName() + ": " + reply);
            }
            if (objective.mission() == null || objective.mission().isBlank()
                    || objective.intent() == null || objective.intent().isBlank()) {
                throw new IllegalStateException("objective without mission or intent for squad " + squad.squadName() + ": " + reply);
            }
            objectives.add(new Objective(objective.mission(), new Position(objective.x(), objective.y(), objective.z()),
                    objective.intent()));
        }
        if (bySquad.size() != squadStatuses.size()) {
            throw new IllegalStateException("objectives for squads that weren't listed: " + reply);
        }
        return objectives;
    }
}
