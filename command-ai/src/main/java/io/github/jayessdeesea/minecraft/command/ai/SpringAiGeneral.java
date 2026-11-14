package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.General;
import io.github.jayessdeesea.minecraft.command.Logstat;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PatrolBase;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.Resupply;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentLinkedDeque;

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
    private final GeneralPrompts prompts;
    private volatile String mission; // written by the operator's thread, read by the general's
    private final Optional<Position> supplyPoint;
    private volatile Stockpile stockpile; // a soldier's thread writes, the general's reads
    private volatile TimeOfDay time; // a connection's thread writes, the general's reads
    /** The operator's last few remarks, oldest first - a connection's thread writes, the general's reads. */
    private final Deque<String> remarks = new ConcurrentLinkedDeque<>();
    /** The camp's progress through the lodgment's phases - a soldier's thread writes, the general's reads. */
    private volatile Foothold foothold;
    /** Daylight left, in ticks, above which a squad still in its patrol base is told the night is over: two minutes. */
    static final long MORNING_AT_LEAST = 2400;
    /** How many of the operator's remarks the general keeps in view. */
    static final int REMARKS_KEPT = 3;

    public SpringAiGeneral(@Qualifier("generalChatClient") ChatClient chatClient, GeneralPrompts prompts,
                           @Value("${minecraft.general.mission}") String mission,
                           @Value("${minecraft.supply.point}") String supplyPoint) {
        this.chatClient = chatClient;
        this.prompts = prompts;
        this.mission = mission;
        this.supplyPoint = Resupply.supplyPoint(supplyPoint);
    }

    @Override
    public void receiveStockpile(Stockpile stockpile) {
        this.stockpile = stockpile;
    }

    @Override
    public void receiveFoothold(Foothold foothold) {
        this.foothold = foothold;
    }

    @Override
    public void receiveRemark(String remark) {
        remarks.addLast(remark);
        while (remarks.size() > REMARKS_KEPT) {
            remarks.pollFirst();
        }
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
        return toObjectives(callModel(context(squadStatuses)), squadStatuses);
    }

    /** The situation the next decision would be asked about: the user prompt, as the model sees it - for reading. */
    public String prompt(List<SquadStatus> squadStatuses) {
        return context(squadStatuses).prompt();
    }

    private PromptContext context(List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, Optional.ofNullable(time), supplyPoint, Optional.ofNullable(stockpile), List.copyOf(remarks),
                Optional.ofNullable(foothold), squadStatuses);
    }

    /** The foothold's phases and standards, the earliest unmet phase named: the lookup done for the model. */
    static String describe(Foothold foothold) {
        String[] phases = {"", "shelter", "airhead", "buildup", "equipment"};
        StringBuilder out = new StringBuilder("The camp at ").append(SpringAiSquadLeader.describe(foothold.camp()))
                .append(", built up phase by phase:\n");
        for (Foothold.Standard s : foothold.standards()) {
            out.append(String.format(Locale.ROOT, "- Phase %d, %s: %s - %s: %s%s\n", s.phase(), phases[s.phase()], s.name(),
                    s.met() ? "met" : "NOT MET", s.status(), s.met() ? "" : " (task " + s.task() + ")"));
        }
        foothold.nextPhase().ifPresentOrElse(p -> {
            List<String> open = foothold.standards().stream().filter(s -> s.phase() == p && !s.met())
                    .map(s -> s.name() + " (" + s.task() + ")").distinct().toList();
            out.append("Next, by day - phase ").append(p).append(", ").append(phases[p]).append(": ").append(String.join(" and ", open))
                    .append(open.size() > 1 ? ", one squad on each.\n" : ".\n");
        }, () -> out.append("Every phase is met.\n"));
        return out.toString();
    }
    /** The time of day in minutes, the unit a commander plans in: a Minecraft day is 20 of them. */
    static String describe(TimeOfDay time) {
        String clock = time.running() ? "" : " The clock is stopped: it stays this way.";
        if (time.dark()) {
            return "Night - already dark: monsters spawn anywhere unlit." + (time.running()
                    ? String.format(Locale.ROOT, " Light again in %s.", minutes(time.ticksUntilLight())) : clock);
        }
        return "Day." + (time.running()
                ? String.format(Locale.ROOT, " Dark - monsters spawn anywhere unlit - in %s.", minutes(time.ticksUntilDark())) : clock);
    }

    private static String minutes(long ticks) {
        long minutes = Math.round(ticks / 1200.0);
        return minutes < 1 ? "under a minute" : minutes == 1 ? "about 1 minute" : "about " + minutes + " minutes";
    }

    static PromptContext buildContext(GeneralPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, time, supplyPoint, stockpile, List.of(), squadStatuses);
    }

    static PromptContext buildContext(GeneralPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<String> remarks,
                                      List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, time, supplyPoint, stockpile, remarks, Optional.empty(), squadStatuses);
    }

    /**
     * The situation, as {@code prompts}' user template's parameters: what the operator has said
     * lately ({@link #receiveRemark}) and the camp's progress, when there's any, and each squad's
     * section filled in from the squad template.
     */
    static PromptContext buildContext(GeneralPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<String> remarks,
                                      Optional<Foothold> foothold, List<SquadStatus> squadStatuses) {
        StringBuilder squads = new StringBuilder();
        for (SquadStatus squad : squadStatuses) {
            StringBuilder soldiers = new StringBuilder();
            for (SoldierStatus soldier : squad.soldiers()) {
                soldiers.append("- ").append(soldier.name()).append(" at ").append(SpringAiSquadLeader.describe(soldier.position()))
                        .append(": ").append(SpringAiSquadLeader.describe(soldier.report())).append('\n');
            }
            squads.append(PromptTemplate.render(prompts.squad(), Map.of(
                    "name", squad.squadName(),
                    "standing", describeStanding(squad),
                    // A request first, where a small model reads it: in the LOGSTAT's middle it went unanswered.
                    "request", squad.classOne().filter(c -> c.rating() == Subsistence.Rating.RED)
                            .map(c -> c.line() + " Answer it with \"task\": \"FARM\" at ground to farm by water.")
                            // Class IV, the keep's logs, after food: a squad eats before it builds.
                            .or(squad::classFour).<Object>map(r -> r).orElse(false),
                    // Said where the squad is listed, not left to be put together from <time> and its state.
                    "night", time.filter(TimeOfDay::dark).isPresent() && !squad.holdingBase(),
                    // And the morning: nothing else says the night is over for a squad still sheltering.
                    "morning", time.filter(t -> !t.dark() && t.ticksUntilDark() > MORNING_AT_LEAST).isPresent() && (squad.holdingBase()
                            || squad.standing().filter(Objective::patrolBase).isPresent()),
                    "report", SpringAiSquadLeader.describe(squad.report()),
                    "soldiers", soldiers.toString(),
                    "logstat", describe(Logstat.of(squad.soldiers()), squad.baseBlocksLeft())
                            + squad.classOne().filter(c -> c.rating() != Subsistence.Rating.RED)
                            .map(c -> " " + c.line()).orElse(""),
                    "resources", describeResources(squad, time.filter(TimeOfDay::dark).isEmpty())))).append('\n');
        }
        // What the operator sees from where they stand: worth weighing, not an order.
        StringBuilder said = new StringBuilder();
        remarks.forEach(r -> said.append("- ").append(r).append('\n'));
        Map<String, Object> params = new HashMap<>();
        params.put("mission", mission);
        params.put("remarks", remarks.isEmpty() ? false : said.toString());
        params.put("time", time.<Object>map(SpringAiGeneral::describe).orElse(false));
        params.put("supplyPoint", supplyPoint.<Object>map(SpringAiSquadLeader::describe).orElse(false));
        params.put("stockpile", stockpile.map(s -> "Last seen holding " + s.logs() + " logs and " + s.stone() + " cobblestone.")
                .orElse("Nobody has looked inside yet."));
        params.put("foothold", foothold.<Object>map(SpringAiGeneral::describe).orElse(false));
        params.put("squads", squads.toString());
        return new PromptContext(prompts.user(), params);
    }

    /** The order the squad is carrying out, and - where it can be told - how far along it is. */
    static String describeStanding(SquadStatus squad) {
        if (squad.standing().isEmpty()) {
            return "Standing order: none yet - there is nothing to keep: give it an objective, with \"keep\": false.";
        }
        Objective standing = squad.standing().get();
        String task = switch (standing.task()) {
            case Objective.Task.None() -> "NONE";
            case Objective.Task.Gather(var resource) -> resource.name();
            case Objective.Task.PatrolBase() -> "PATROL_BASE";
            case Objective.Task.Farm() -> "FARM";
            case Objective.Task.TreeFarm() -> "TREE_FARM";
            case Objective.Task.Secure() -> "SECURE";
            case Objective.Task.Fortify() -> "FORTIFY";
            case Objective.Task.Mine() -> "MINE";
            case Objective.Task.Patrol() -> "PATROL";
            case Objective.Task.Keep() -> "KEEP";
        };
        // The mission in quotes, its ground apart: written as "<mission> at <place>", the model copied
        // the whole of it into its next mission, and the place grew a clause with every decision.
        StringBuilder line = new StringBuilder("Standing order: ").append(task).append(" - \"").append(standing.mission())
                .append("\"; its ground ").append(squad.soldiers().isEmpty() ? SpringAiSquadLeader.describe(standing.location())
                        : relative(standing.location(), squad.soldiers().getFirst().position()))
                .append(". Intent: ").append(standing.intent());
        if (standing.patrolBase()) {
            int left = squad.baseBlocksLeft().orElse(-1);
            line.append(squad.holdingBase() ? " Status: the patrol base is built and sealed, the squad inside it."
                    : left < 0 ? " Status: the squad hasn't laid the patrol base out yet."
                    : left == 0 ? " Status: the patrol base is built, but not everyone is inside."
                    : String.format(Locale.ROOT, " Status: the patrol base is laid out, %d blocks of it still to place.", left));
        }
        // The task's acknowledgement, measured: a finished task and a stalled one otherwise read as one to keep.
        squad.progress().ifPresent(p -> line.append(switch (p.state()) {
            case DONE -> " Status: DONE - " + p.detail() + ". Nothing is left to do here: give the squad its next task.";
            case STALLED -> " Status: STALLED - " + p.detail() + ". Kept, the squad stands idle: change the task or the place.";
            case UNDER_WAY -> " Status: under way - " + p.detail() + ".";
        }));
        return line.toString();
    }

    /** What the squad can see to gather, with distance and direction from its point man. */
    static String describeResources(SquadStatus squad) {
        return describeResources(squad, true);
    }

    /** As {@link #describeResources(SquadStatus)}, with the word on growing wood only by {@code day}: at night a squad shelters. */
    static String describeResources(SquadStatus squad, boolean day) {
        String noTrees = day ? NO_TREES : "";
        if (squad.resources().isEmpty()) {
            return "Resources seen: no trees, exposed stone or ground to farm within 64 blocks." + noTrees;
        }
        Position from = squad.soldiers().isEmpty() ? null : squad.soldiers().getFirst().position();
        StringBuilder line = new StringBuilder("Resources seen:");
        for (ResourceSite site : squad.resources()) {
            line.append(' ').append(site.amount()).append(' ').append(switch (site.resource()) {
                        case WOOD -> "logs";
                        case STONE -> "exposed stone";
                        case SOIL -> "dirt";
                        case FLINT -> "gravel";
                        case FARMLAND -> "cells of ground to farm by the water";
                    })
                    .append(" around ").append(from == null ? SpringAiSquadLeader.describe(site.location()) : relative(site.location(), from));
            if (from != null) {
                double dx = site.location().x() - from.x();
                double dz = site.location().z() - from.z();
                line.append(String.format(Locale.ROOT, " (%.0f blocks %s)", Math.hypot(dx, dz), compass(dx, dz)));
            }
            line.append(';');
        }
        line.setCharAt(line.length() - 1, '.');
        if (squad.resources().stream().noneMatch(site -> site.resource() == Resource.WOOD)) {
            line.append(" No trees in sight.").append(noTrees);
        }
        return line.toString();
    }

    /** Said wherever a squad sees no trees: the lookup done for the model, not left to it. */
    static final String NO_TREES = " Wood here has to be grown: TREE_FARM on open ground near the camp.";

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
        return describe(l, OptionalInt.empty());
    }

    /**
     * The LOGSTAT line: what the squad is short of, by class of supply. Its materials are measured
     * against the patrol base it has still to build: all of one with none laid out, what's left of
     * one under way, nothing once one is built - its blocks went into the walls.
     */
    static String describe(Logstat l, OptionalInt baseBlocksLeft) {
        if (l.reporting() == 0) {
            return "LOGSTAT: no soldier has reported his condition yet.";
        }
        StringBuilder line = new StringBuilder("LOGSTAT: ");
        line.append(String.format(Locale.ROOT, "food - %d of %d hungry (not regenerating health)", l.hungry(), l.reporting()));
        line.append(l.kitReported() == 0 ? "; " : String.format(Locale.ROOT, ", %d rations carried; ", l.rations()));
        if (l.kitReported() == 0) {
            line.append("weapons and armour not reported yet; ");
        } else {
            line.append(String.format(Locale.ROOT, "weapons - %d of %d bare-handed, %d arrows; armour - %d of %d slots empty; ",
                    l.unarmed(), l.kitReported(), l.arrows(), l.armourEmpty(), l.armourSlots()));
        }
        line.append(String.format(Locale.ROOT, "casualties - %d below half health, %d deaths so far; materials - %d logs, %d cobblestone%s",
                l.wounded(), l.deaths(), l.logs(), l.stone(), l.dirt() > 0 ? ", " + l.dirt() + " dirt" : ""));
        if (baseBlocksLeft.isPresent() && baseBlocksLeft.getAsInt() == 0) {
            return line.append(" - their patrol base is already built.").toString();
        }
        // Worked out here, not left to the model: whether what they carry builds their shelter.
        int blocks = l.logs() + l.stone() + l.dirt();
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
                : String.format(Locale.ROOT, " - %d short of the %d blocks a patrol base takes; set PATROL_BASE anyway when it needs"
                        + " shelter: its soldiers dig the rest as dirt beside the site.", needed - blocks, needed));
        return line.toString();
    }

    GeneralReply callModel(PromptContext context) {
        LOG.debug("General prompt:\n{}", context.prompt());
        GeneralReply reply = chatClient.prompt()
                .templateRenderer(PromptTemplate.RENDERER)
                .system(prompts.system())
                .user(user -> user.text(context.template()).params(context.params()))
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
                        case NONE -> new Objective.Task.None();
                        case WOOD -> new Objective.Task.Gather(Resource.WOOD);
                        case STONE -> new Objective.Task.Gather(Resource.STONE);
                        case PATROL_BASE -> new Objective.Task.PatrolBase();
                        case FARM -> new Objective.Task.Farm();
                        case TREE_FARM -> new Objective.Task.TreeFarm();
                        case SECURE -> new Objective.Task.Secure();
                        case FORTIFY -> new Objective.Task.Fortify();
                        case MINE -> new Objective.Task.Mine();
                        case PATROL -> new Objective.Task.Patrol();
                        case KEEP -> new Objective.Task.Keep();
                    }));
        }
        if (bySquad.size() != squadStatuses.size()) {
            throw new IllegalStateException("objectives for squads that weren't listed: " + reply);
        }
        return objectives;
    }
}
