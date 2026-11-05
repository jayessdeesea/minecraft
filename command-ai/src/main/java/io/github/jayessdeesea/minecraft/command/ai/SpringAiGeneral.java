package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Logstat;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resupply;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    private static final Logger LOG = LogManager.getLogger(SpringAiGeneral.class);

    private final ChatClient chatClient;
    private volatile String mission; // written by the operator's thread, read by the general's
    private final Optional<Position> supplyPoint;
    private volatile io.github.jayessdeesea.minecraft.command.Stockpile stockpile; // a soldier's thread writes, the general's reads
    private volatile TimeOfDay time; // a connection's thread writes, the general's reads

    public SpringAiGeneral(@Qualifier("generalChatClient") ChatClient chatClient,
                           @Value("${minecraft.general.mission}") String mission,
                           @Value("${minecraft.supply.point}") String supplyPoint) {
        this.chatClient = chatClient;
        this.mission = mission;
        this.supplyPoint = Resupply.supplyPoint(supplyPoint);
    }

    @Override
    public void receiveStockpile(io.github.jayessdeesea.minecraft.command.Stockpile stockpile) {
        this.stockpile = stockpile;
    }

    @Override
    public void receiveTime(TimeOfDay time) {
        this.time = time;
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
        return toObjectives(callModel(buildContext(mission, Optional.ofNullable(time), supplyPoint, Optional.ofNullable(stockpile), squadStatuses)), squadStatuses);
    }

    /** The time of day in minutes, the unit a commander plans in: a Minecraft day is 20 of them. */
    static String describe(TimeOfDay time) {
        String clock = time.running() ? "" : " The clock is stopped: it stays this way.";
        if (time.dark()) {
            return "Night: monsters spawn anywhere dark." + (time.running()
                    ? String.format(Locale.ROOT, " Light again in %s.", minutes(time.ticksUntilLight())) : clock);
        }
        return "Day." + (time.running()
                ? String.format(Locale.ROOT, " Dark - monsters spawn anywhere unlit - in %s.", minutes(time.ticksUntilDark())) : clock);
    }

    private static String minutes(long ticks) {
        long minutes = Math.round(ticks / 1200.0);
        return minutes < 1 ? "under a minute" : minutes == 1 ? "about 1 minute" : "about " + minutes + " minutes";
    }

    static PromptContext buildContext(String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<io.github.jayessdeesea.minecraft.command.Stockpile> stockpile, List<SquadStatus> squadStatuses) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<mission>\n").append(mission).append("\n</mission>\n\n");
        time.ifPresent(t -> prompt.append("<time>\n").append(describe(t)).append("\n</time>\n\n"));
        supplyPoint.ifPresent(point -> prompt.append("<supply_point>\nA chest stocked with weapons and armour at ")
                .append(SpringAiSquadLeader.describe(point))
                .append(". Give a squad an objective there to resupply it: soldiers short of a weapon or armour draw from it.\n")
                .append("It is also the stockpile: soldiers carrying a full load of gathered logs or cobblestone put it in. ")
                .append(stockpile.map(s -> "Last seen holding " + s.logs() + " logs and " + s.stone() + " cobblestone.")
                        .orElse("Nobody has looked inside yet."))
                .append("\n</supply_point>\n\n"));
        for (SquadStatus squad : squadStatuses) {
            prompt.append("<squad name=\"").append(squad.squadName()).append("\">\n")
                    .append(describeStanding(squad)).append('\n')
                    .append("Squad leader reports: ").append(SpringAiSquadLeader.describe(squad.report())).append('\n');
            for (SoldierStatus soldier : squad.soldiers()) {
                prompt.append("- ").append(soldier.name()).append(" at ").append(SpringAiSquadLeader.describe(soldier.position()))
                        .append(": ").append(SpringAiSquadLeader.describe(soldier.report())).append('\n');
            }
            prompt.append(describe(Logstat.of(squad.soldiers()), squad.baseBlocksLeft())).append('\n');
            prompt.append(describeResources(squad)).append('\n');
            prompt.append("</squad>\n\n");
        }
        prompt.append("Give each squad its objective now.");
        return new PromptContext(prompt.toString());
    }

    /** The order the squad is carrying out, and - where it can be told - how far along it is. */
    static String describeStanding(SquadStatus squad) {
        if (squad.standing().isEmpty()) {
            return "Standing order: none yet - there is nothing to keep: give it an objective, with \"keep\": false.";
        }
        Objective standing = squad.standing().get();
        String task = standing.patrolBase() ? "PATROL_BASE" : standing.gather().map(Enum::name).orElse("NONE");
        StringBuilder line = new StringBuilder("Standing order: ").append(task).append(" - ").append(standing.mission())
                .append(" at ").append(squad.soldiers().isEmpty() ? SpringAiSquadLeader.describe(standing.location())
                        : relative(standing.location(), squad.soldiers().getFirst().position()))
                .append(". Intent: ").append(standing.intent());
        if (standing.patrolBase()) {
            int left = squad.baseBlocksLeft().orElse(-1);
            line.append(squad.holdingBase() ? " Status: the patrol base is built and sealed, the squad inside it."
                    : left < 0 ? " Status: the squad hasn't laid the patrol base out yet."
                    : left == 0 ? " Status: the patrol base is built, but not everyone is inside."
                    : String.format(Locale.ROOT, " Status: the patrol base is laid out, %d blocks of it still to place.", left));
        }
        return line.toString();
    }

    /** What the squad can see to gather, with distance and direction from its point man. */
    static String describeResources(SquadStatus squad) {
        if (squad.resources().isEmpty()) {
            return "Resources seen: no trees or exposed stone within 64 blocks.";
        }
        Position from = squad.soldiers().isEmpty() ? null : squad.soldiers().getFirst().position();
        StringBuilder line = new StringBuilder("Resources seen:");
        for (io.github.jayessdeesea.minecraft.command.ResourceSite site : squad.resources()) {
            line.append(' ').append(site.amount()).append(' ').append(site.resource() == io.github.jayessdeesea.minecraft.command.Resource.WOOD ? "logs" : "exposed stone")
                    .append(" around ").append(from == null ? SpringAiSquadLeader.describe(site.location()) : relative(site.location(), from));
            if (from != null) {
                double dx = site.location().x() - from.x();
                double dz = site.location().z() - from.z();
                line.append(String.format(Locale.ROOT, " (%.0f blocks %s)", Math.hypot(dx, dz), compass(dx, dz)));
            }
            line.append(';');
        }
        line.setCharAt(line.length() - 1, '.');
        return line.toString();
    }

    /**
     * Where {@code point} is, its height given against the squad at {@code squad} rather than as a
     * y: a resource's height is where it grows, and an old order's where that was - neither is ground
     * to send the squad to, and a y written out is a y the model copies.
     */
    static String relative(Position point, Position squad) {
        long dy = Math.round(point.y() - squad.y());
        String level = Math.abs(dy) < 3 ? "about level with the squad" : Math.abs(dy) + (dy < 0 ? " below the squad" : " above the squad");
        return String.format(Locale.ROOT, "x=%d z=%d, %s", Math.round(point.x()), Math.round(point.z()), level);
    }

    /** The eight-point compass direction of a horizontal offset (north is -z). */
    static String compass(double dx, double dz) {
        String[] points = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double degrees = Math.toDegrees(Math.atan2(-dx, dz)); // 0 south, 90 west - Minecraft's yaw
        return points[(int) Math.floorMod(Math.round(degrees / 45), 8)];
    }

    /** The LOGSTAT line of a squad with no patrol base laid out. */
    static String describe(Logstat l) {
        return describe(l, java.util.OptionalInt.empty());
    }

    /**
     * The LOGSTAT line: what the squad is short of, by class of supply. Its materials are measured
     * against the patrol base it has still to build: all of one with none laid out, what's left of
     * one under way, nothing once one is built - its blocks went into the walls.
     */
    static String describe(Logstat l, java.util.OptionalInt baseBlocksLeft) {
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
        line.append(String.format(Locale.ROOT, "casualties - %d below half health, %d deaths so far; materials - %d logs, %d cobblestone",
                l.wounded(), l.deaths(), l.logs(), l.stone()));
        if (baseBlocksLeft.isPresent() && baseBlocksLeft.getAsInt() == 0) {
            return line.append(" - their patrol base is already built.").toString();
        }
        // Worked out here, not left to the model: whether what they carry builds their shelter.
        int blocks = l.logs() + l.stone();
        if (baseBlocksLeft.isPresent()) {
            int left = baseBlocksLeft.getAsInt();
            return line.append(blocks >= left
                    ? String.format(Locale.ROOT, " - enough for the %d blocks of their patrol base still to place.", left)
                    : String.format(Locale.ROOT, " - %d short of the %d blocks of their patrol base still to place.", left - blocks, left))
                    .toString();
        }
        int needed = PatrolBase.hasty(new Position(0, 0, 0), Math.max(1, Math.min(l.soldiers(), 9))).cells().size();
        line.append(blocks >= needed
                ? String.format(Locale.ROOT, " - enough to build a patrol base (%d blocks).", needed)
                : String.format(Locale.ROOT, " - %d short of the %d blocks a patrol base takes.", needed - blocks, needed));
        return line.toString();
    }

    GeneralReply callModel(PromptContext context) {
        LOG.debug("General prompt:\n{}", context.prompt());
        GeneralReply reply = chatClient.prompt()
                .user(context.prompt())
                .call()
                .entity(GeneralReply.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        LOG.debug("General reply: {}", reply);
        return reply;
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
            if (objective.keep()) {
                objectives.add(squad.standing().orElseThrow(() -> new IllegalStateException(
                        "kept a standing order squad " + squad.squadName() + " doesn't have: " + reply)));
                continue;
            }
            if (squad.standing().isPresent()) {
                if (objective.reason() == null || objective.reason().isBlank()) {
                    throw new IllegalStateException("changed squad " + squad.squadName() + "'s objective without a reason: " + reply);
                }
                LOG.info("[General -> {}] changing the objective: {}", squad.squadName(), objective.reason());
            }
            if (!Double.isFinite(objective.x()) || !Double.isFinite(objective.y()) || !Double.isFinite(objective.z())
                    || objective.y() < -64 || objective.y() > 320) {
                throw new IllegalStateException("objective outside the world for squad " + squad.squadName() + ": " + reply);
            }
            if (objective.mission() == null || objective.mission().isBlank()
                    || objective.intent() == null || objective.intent().isBlank()) {
                throw new IllegalStateException("objective without mission or intent for squad " + squad.squadName() + ": " + reply);
            }
            if (objective.task() == null) {
                throw new IllegalStateException("objective without a task (NONE if nothing) for squad " + squad.squadName() + ": " + reply);
            }
            objectives.add(new Objective(objective.mission(), new Position(objective.x(), objective.y(), objective.z()),
                    objective.intent(), switch (objective.task()) {
                        case NONE, PATROL_BASE -> java.util.Optional.empty();
                        case WOOD -> java.util.Optional.of(io.github.jayessdeesea.minecraft.command.Resource.WOOD);
                        case STONE -> java.util.Optional.of(io.github.jayessdeesea.minecraft.command.Resource.STONE);
                    }, objective.task() == GeneralReply.Task.PATROL_BASE));
        }
        if (bySquad.size() != squadStatuses.size()) {
            throw new IllegalStateException("objectives for squads that weren't listed: " + reply);
        }
        return objectives;
    }
}
