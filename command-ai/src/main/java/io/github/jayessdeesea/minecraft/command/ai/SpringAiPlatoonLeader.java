package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
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
 * The {@link PlatoonLeader}: same {@link #buildContext} (pure) -&gt; {@link #callModel} (impure) -&gt;
 * {@link #toObjectives} (pure validation) split as {@link SpringAiSquadLeader}. Its own orders come
 * from the operator - the one piece of intent that originates outside the chain of command:
 * {@code minecraft.platoon-leader.mission} at start, replaced by {@link #receiveMission} during a run.
 *
 * <p>A {@code @Component}: exactly one platoon leader exists (AGENTS.md's Objective section).
 */
@Component
public final class SpringAiPlatoonLeader implements PlatoonLeader {

    private static final Logger LOG = LogManager.getLogger(SpringAiPlatoonLeader.class);

    private final ChatClient chatClient;
    private final PlatoonLeaderPrompts prompts;
    private volatile String mission; // written by the operator's thread, read by the platoon leader's
    private final Optional<Position> supplyPoint;
    private volatile Stockpile stockpile; // a soldier's thread writes, the platoon leader's reads
    private volatile TimeOfDay time; // a connection's thread writes, the platoon leader's reads
    /** The operator's last few remarks, oldest first - a connection's thread writes, the platoon leader's reads. */
    private final Deque<String> remarks = new ConcurrentLinkedDeque<>();
    /** The camp's progress through the lodgment's phases - a soldier's thread writes, the platoon leader's reads. */
    private volatile Foothold foothold;
    private volatile io.github.jayessdeesea.minecraft.command.DayWork dayWork; // the chain of command writes before it asks
    /** The user prompt of its last decision - its thread writes, the operator's chat reads ({@link PlatoonLeaderChat}). */
    private volatile String lastShown;
    /** Daylight left, in ticks, above which a squad still in its patrol base is told the night is over: two minutes. */
    static final long MORNING_AT_LEAST = 2400;
    /** How many of the operator's remarks the platoon leader keeps in view. */
    static final int REMARKS_KEPT = 3;

    public SpringAiPlatoonLeader(@Qualifier("platoonLeaderChatClient") ChatClient chatClient, PlatoonLeaderPrompts prompts,
                           @Value("${minecraft.platoon-leader.mission}") String mission,
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
    public void receiveDayWork(io.github.jayessdeesea.minecraft.command.DayWork work) {
        this.dayWork = work;
    }

    /** The day's work as the prompt says it: each shortfall with the task that answers it, the stocks, the flatten. */
    static String describe(io.github.jayessdeesea.minecraft.command.DayWork work) {
        StringBuilder out = new StringBuilder();
        if (work.unarmed() > 0) {
            out.append(work.unarmed()).append(" soldier(s) short of a sword or a full set of armour.\n");
        }
        out.append("Phase: ").append(work.phase()).append(" - ").append(work.phase().meaning())
                .append(work.toNextPhase().isEmpty() ? "" : "; " + work.toNextPhase()).append(".\n");
        if (work.shortfalls().isEmpty() && !work.phase().beforeBuildup()) {
            out.append("The force is short of nothing: every squad but the farm squad can flatten (KEEP).\n");
        }
        for (io.github.jayessdeesea.minecraft.command.PlatoonSergeant.Shortfall s : work.shortfalls()) {
            out.append(switch (s) {
                case FOOD -> "Short of FOOD: a squad on FARM at a \"ground to farm\" site" + (work.ripeWheat() > 0 ? "; and with "
                        + work.ripeWheat() + " ripe wheat standing within 100 blocks of the camp, each squad whose Class I is RED on FORAGE" : "") + ".";
                case WOOD -> "Short of WOOD: a squad on WOOD at trees it has seen, or on TREE_FARM to plant a grove.";
                case IRON -> "Short of IRON: a squad on MINE.";
            }).append('\n');
        }
        work.stores().ifPresent(st -> out.append("The stockpile, each against its least and most: ").append(work.levels().describe(st))
                .append(", raw iron ").append(st.rawIron()).append(".\n"));
        out.append("The ground to flatten (KEEP): ").append(work.flatten().describe());
        work.keepLevel().ifPresent(level -> out.append(", now at y=").append(level).append(" with ").append(work.keepBlocksLeft())
                .append(" block(s) left on that level"));
        out.append(".\n");
        return out.toString();
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
        PromptContext shown = context(squadStatuses);
        lastShown = shown.prompt();
        PlatoonLeaderReply reply = callModel(shown);
        // Every squad its one objective (operator, 2026-11-20: it's a defect if one hasn't): the squads left out or named
        // twice asked again, alone. Live (field533-545), nearly every decision missed one, and it got no new order.
        List<SquadStatus> unanswered = unanswered(reply, squadStatuses);
        if (!unanswered.isEmpty()) {
            LOG.warn("[PL] no single objective for {} - asked again for {}", unanswered.stream().map(SquadStatus::squadName).toList(),
                    unanswered.size() == 1 ? "it" : "them");
        }
        // Merged even when every squad had its one: a squad not listed in the reply is dropped, not a broken decision.
        return toObjectives(merged(reply, unanswered.isEmpty() ? null : callModel(context(unanswered)), squadStatuses), squadStatuses);
    }

    /** The squads {@code reply} doesn't give exactly one objective: left out, or named twice. */
    static List<SquadStatus> unanswered(PlatoonLeaderReply reply, List<SquadStatus> squadStatuses) {
        List<PlatoonLeaderReply.ObjectiveReply> given = reply == null || reply.objectives() == null ? List.of() : reply.objectives();
        return squadStatuses.stream().filter(s -> given.stream().filter(o -> s.squadName().equals(o.squad())).count() != 1).toList();
    }

    /**
     * One reply of the two: each listed squad's single objective from the first, else from the re-ask - and nothing for a
     * squad not listed. A squad neither gives one to stays out, and fails alone, loud ({@link #toObjectives}).
     */
    static PlatoonLeaderReply merged(PlatoonLeaderReply first, PlatoonLeaderReply again, List<SquadStatus> squadStatuses) {
        List<PlatoonLeaderReply.ObjectiveReply> out = new ArrayList<>();
        for (SquadStatus squad : squadStatuses) {
            single(first, squad.squadName()).or(() -> single(again, squad.squadName())).ifPresent(out::add);
        }
        return new PlatoonLeaderReply(out);
    }

    private static Optional<PlatoonLeaderReply.ObjectiveReply> single(PlatoonLeaderReply reply, String squad) {
        List<PlatoonLeaderReply.ObjectiveReply> named = reply == null || reply.objectives() == null ? List.of()
                : reply.objectives().stream().filter(o -> squad.equals(o.squad())).toList();
        return named.size() == 1 ? Optional.of(named.getFirst()) : Optional.empty();
    }

    /** What it was shown at its last decision - the user prompt as the model read it - if it has decided yet. */
    public Optional<String> lastShown() {
        return Optional.ofNullable(lastShown);
    }

    /** The situation the next decision would be asked about: the user prompt, as the model sees it - for reading. */
    public String prompt(List<SquadStatus> squadStatuses) {
        return context(squadStatuses).prompt();
    }

    private PromptContext context(List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, Optional.ofNullable(time), supplyPoint, Optional.ofNullable(stockpile), List.copyOf(remarks),
                Optional.ofNullable(foothold), Optional.ofNullable(dayWork), squadStatuses);
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

    static PromptContext buildContext(PlatoonLeaderPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, time, supplyPoint, stockpile, List.of(), squadStatuses);
    }

    static PromptContext buildContext(PlatoonLeaderPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<String> remarks,
                                      List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, time, supplyPoint, stockpile, remarks, Optional.empty(), squadStatuses);
    }

    /**
     * The situation, as {@code prompts}' user template's parameters: what the operator has said
     * lately ({@link #receiveRemark}) and the camp's progress, when there's any, and each squad's
     * section filled in from the squad template.
     */
    static PromptContext buildContext(PlatoonLeaderPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<String> remarks,
                                      Optional<Foothold> foothold, List<SquadStatus> squadStatuses) {
        return buildContext(prompts, mission, time, supplyPoint, stockpile, remarks, foothold, Optional.empty(), squadStatuses);
    }

    /**
     * The reply asked for, counted and named: one objective a squad, in the order listed, the foothold's standards shared
     * out among them. Live (field427-428), told only "name each squad once", the platoon leader gave every squad an
     * objective for each unmet standard - lighting, arms, trees - twelve in all, and the reply was refused.
     */
    static String roster(List<SquadStatus> squadStatuses) {
        List<String> names = squadStatuses.stream().map(SquadStatus::squadName).toList();
        return names.size() + " objective(s), exactly one for each of " + String.join(", ", names) + ", in that order. Each squad gets"
                + " the single job that matters most for it; unmet standards are shared out among the squads, never one standard"
                + " given to every squad.";
    }

    /** As the overload above, with the day's work computed for it ({@link #receiveDayWork}). */
    static PromptContext buildContext(PlatoonLeaderPrompts prompts, String mission, Optional<TimeOfDay> time, Optional<Position> supplyPoint,
                                      Optional<Stockpile> stockpile, List<String> remarks,
                                      Optional<Foothold> foothold, Optional<io.github.jayessdeesea.minecraft.command.DayWork> dayWork,
                                      List<SquadStatus> squadStatuses) {
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
                            // FORAGE with wheat ripe round the camp - computed, said with the request it answers: live (field505-511),
                            // told FARM here, the platoon leader never once chose FORAGE with 500 ripe wheat in the day's work.
                            .map(c -> c.line() + dayWork.filter(w -> w.ripeWheat() > 0).map(w -> " Answer it with \"task\": \"FORAGE\" at the camp: "
                                    + w.ripeWheat() + " ripe wheat stand within 100 blocks of it - its soldiers get rations off the land today; but keep one squad"
                                    + " on \"FARM\" at the camp's farm, the farms being what there is to forage.")
                                    .orElse(" Answer it with \"task\": \"FARM\" at ground to farm by water."))
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
        params.put("time", time.<Object>map(SpringAiPlatoonLeader::describe).orElse(false));
        params.put("supplyPoint", supplyPoint.<Object>map(SpringAiSquadLeader::describe).orElse(false));
        params.put("stockpile", stockpile.map(s -> "Last seen holding " + s.logs() + " logs and " + s.stone() + " cobblestone.")
                .orElse("Nobody has looked inside yet."));
        params.put("foothold", foothold.<Object>map(SpringAiPlatoonLeader::describe).orElse(false));
        params.put("dayWork", dayWork.<Object>map(SpringAiPlatoonLeader::describe).orElse(false));
        params.put("squads", squads.toString());
        params.put("roster", roster(squadStatuses));
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
            case Objective.Task.Forage() -> "FORAGE";
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
        // Its ground and its dump, as the chain of command worked them out: the squad leader shares them out.
        standing.area().ifPresent(a -> line.append(" Worked ").append(a.line()).append('.'));
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
     * Where {@code point} is, as the three numbers an objective takes - its x and z, and for y the squad's own
     * height, not the point's: a resource's height is where it grows, and an old order's where that was, neither
     * ground to send the squad to (the objective is put on the ground there anyway). Its height is said against
     * the squad's. Live (field440-443), given "x=15 z=-68", the model wrote x=15, y=-68, z=0 - the z slid into the
     * y - four replies refused as outside the world.
     */
    static String relative(Position point, Position squad) {
        long dy = Math.round(point.y() - squad.y());
        String level = Math.abs(dy) < 3 ? "about level with the squad" : Math.abs(dy) + (dy < 0 ? " below the squad" : " above the squad");
        return String.format(Locale.ROOT, "x=%d, y=%d, z=%d, %s", Math.round(point.x()), Math.round(squad.y()), Math.round(point.z()), level);
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

    PlatoonLeaderReply callModel(PromptContext context) {
        LOG.debug("PL prompt:\n{}", context.prompt());
        PlatoonLeaderReply reply = chatClient.prompt()
                .templateRenderer(PromptTemplate.RENDERER)
                .system(prompts.system())
                .user(user -> user.text(context.template()).params(context.params()))
                .call()
                .entity(PlatoonLeaderReply.class, spec -> spec.useProviderStructuredOutput().validateSchema());
        LOG.debug("PL reply: {}", reply);
        if (reply != null && (reply.objectives() == null || reply.objectives().isEmpty())) {
            // Its prompt kept, for a PlatoonLeaderScenarioIT case: live (field557) one reply in 53, and its re-ask,
            // named no squad at all.
            LOG.warn("[PL] a reply naming no objective at all, to this prompt:\n{}", context.prompt());
        }
        return reply;
    }

    /**
     * One objective per listed squad, in the same order - anything else is a violated contract. A squad named twice, or
     * left out, fails alone, loud: it keeps its standing order, or with none holds where it is, the other squads'
     * objectives stand. Live (field495-510),
     * one squad named twice or left out in about one reply a run threw out every squad's decision with it.
     */
    /**
     * A squad's objective when the reply gave it none it can read and it has no standing order: hold where its first
     * soldier stands till the next decision - the day's guardrails give it work meanwhile. Live (field530), Alpha with no
     * standing order named twice threw out every squad's decision.
     */
    static Objective holdsWhereItIs(SquadStatus squad) {
        Position at = squad.soldiers().stream().findFirst().map(SoldierStatus::position)
                .orElseThrow(() -> new IllegalStateException("squad " + squad.squadName() + " has no soldier to hold with"));
        return new Objective("Hold where you are till the next decision", at, "No readable order this decision", new Objective.Task.None());
    }

    static List<Objective> toObjectives(PlatoonLeaderReply reply, List<SquadStatus> squadStatuses) {
        if (reply == null || reply.objectives() == null) {
            throw new IllegalStateException("platoon leader model returned no objectives");
        }
        Map<String, PlatoonLeaderReply.ObjectiveReply> bySquad = new HashMap<>();
        java.util.Set<String> twice = new java.util.HashSet<>();
        for (PlatoonLeaderReply.ObjectiveReply objective : reply.objectives()) {
            if (bySquad.put(objective.squad(), objective) != null) {
                twice.add(objective.squad());
            }
        }
        List<Objective> objectives = new ArrayList<>(squadStatuses.size());
        for (SquadStatus squad : squadStatuses) {
            if (twice.contains(squad.squadName())) {
                objectives.add(squad.standing().orElseGet(() -> holdsWhereItIs(squad)));
                LOG.error("[PL] two objectives for squad {} - it keeps its standing order, or with none holds where it is: {}", squad.squadName(), reply);
                continue;
            }
            PlatoonLeaderReply.ObjectiveReply objective = bySquad.get(squad.squadName());
            if (objective == null) {
                objectives.add(squad.standing().orElseGet(() -> holdsWhereItIs(squad)));
                LOG.error("[PL] no objective for squad {} - it keeps its standing order, or with none holds where it is: {}", squad.squadName(), reply);
                continue;
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
                LOG.info("[PL -> {}] changing the objective: {}", squad.squadName(), objective.reason());
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
                        case FORAGE -> new Objective.Task.Forage();
                        case TREE_FARM -> new Objective.Task.TreeFarm();
                        case SECURE -> new Objective.Task.Secure();
                        case FORTIFY -> new Objective.Task.Fortify();
                        case MINE -> new Objective.Task.Mine();
                        case PATROL -> new Objective.Task.Patrol();
                        case KEEP -> new Objective.Task.Keep();
                    }));
        }
        if (bySquad.keySet().stream().anyMatch(n -> squadStatuses.stream().noneMatch(q -> q.squadName().equals(n)))) {
            throw new IllegalStateException("objectives for squads that weren't listed: " + reply);
        }
        return objectives;
    }
}
