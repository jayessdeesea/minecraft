package io.github.jayessdeesea.minecraft.command.ai;

import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.OperatorCommands;
import io.github.jayessdeesea.minecraft.command.PlatoonRegisters;
import io.github.jayessdeesea.minecraft.command.RegistersSaid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * The platoon leader's tools for one of the operator's turns (docs/echelons/platoon-leader/decisions.md § Talking with the
 * operator): reads of its registers, each said as computed ({@link RegistersSaid}), and commands, each one of the
 * operator's own orders ({@link OperatorCommands}) - never a row written. One per turn: it holds the operator's words, for
 * the rule that a farm's level is the operator's to give, and what the turn's commands did.
 */
final class PlatoonLeaderTools {

    private static final Logger LOG = LogManager.getLogger(PlatoonLeaderTools.class);

    private final PlatoonRegisters registers;
    private final OperatorCommands commands;
    private final String message;
    private final List<String> done = new ArrayList<>();

    PlatoonLeaderTools(PlatoonRegisters registers, OperatorCommands commands, String message) {
        this.registers = registers;
        this.commands = commands;
        this.message = message;
    }

    /** What this turn's commands did, each as it said, in order. */
    synchronized List<String> done() {
        return List.copyOf(done);
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "missions", description = "The missions kept, each with its operation's phases under it - each phase a squad's task, with "
            + "its state, squad and why - then the tasks you gave by your own decisions under the platoon's mission. Use it when the "
            + "operator asks about missions or operations. A mission is what the operator gives ('a farm at chunk (1, 3)'); its "
            + "operation is carried out in phases, each a task.")
    String missions(@ToolParam(required = false, description = "OPEN (the default) for missions still being carried out, ALL for every one kept")
                    String which) {
        boolean all = Optional.ofNullable(which).map(String::strip).filter("ALL"::equalsIgnoreCase).isPresent();
        if (which != null && !which.isBlank() && !all && !which.strip().equalsIgnoreCase("OPEN")) {
            throw new IllegalArgumentException("missions takes OPEN or ALL, not '" + which + "'");
        }
        return read("missions " + (all ? "ALL" : "OPEN"), () -> RegistersSaid.missions(registers.tasks(), all, registers.now()));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "tasks", description = "The tasks kept - the rows of the task register, each a squad's task: a phase of a mission's "
            + "operation, or one you gave by your own decision - filtered. Use it when the operator asks about tasks, or about "
            + "'objectives' meaning the squads' assignments. Each says its number, mission, phase, squad, state, for how long, why, "
            + "and its objective: the chunk it's oriented on.")
    String tasks(@ToolParam(required = false, description = "only this squad's, e.g. Charlie") String squad,
                 @ToolParam(required = false, description = "OPEN (the default) for those still being carried out, ALL, or one state: "
                         + "PLANNED, UNDER_WAY, DONE, CANTCO, RELIEVED, SUPERSEDED") String state,
                 @ToolParam(required = false, description = "only a mission's whose name holds this, e.g. 'chunk (1, 3)'") String mission) {
        return read("tasks squad=" + squad + " state=" + state + " mission=" + mission, () -> RegistersSaid.tasks(registers.tasks(),
                Optional.ofNullable(squad), Optional.ofNullable(state), Optional.ofNullable(mission), registers.now()));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "task", description = "One task as it stands, by its number (task #12), and its mission.")
    String task(@ToolParam(description = "the task's number") long id) {
        return read("task " + id, () -> RegistersSaid.task(registers.tasks(), id, registers.now()));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "chunk", description = "What's kept of one chunk - what the force has made of it, its level and stage - and the tasks "
            + "open on it. A chunk is 16 by 16 blocks: chunk (1, 3) is x 16..31, z 48..63.")
    String chunk(@ToolParam(description = "the chunk's x: block x / 16, rounded down") int chunkX,
                 @ToolParam(description = "the chunk's z: block z / 16, rounded down") int chunkZ) {
        return read("chunk " + chunkX + " " + chunkZ, () -> RegistersSaid.chunk(registers.chunks(), registers.tasks(), chunkX, chunkZ, registers.now()));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "chunkOfBlock", description = "The chunk a block's x and z are in, worked out for you, and what's kept of it. Use it "
            + "whenever the operator gives block coordinates (x=20 z=50) and you need the chunk.")
    String chunkOfBlock(@ToolParam(description = "the block's x") int x, @ToolParam(description = "the block's z") int z) {
        return read("chunkOfBlock " + x + " " + z, () -> RegistersSaid.chunkOf(registers.chunks(), registers.tasks(), x, z, registers.now()));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "chunks", description = "Every chunk the force has made something of, with its kind, level and stage.")
    String chunks(@ToolParam(required = false, description = "only this kind: FLATTENED, FLOORED, WHEAT, TREES or SUPPLY_POINT") String kind) {
        return read("chunks " + kind, () -> RegistersSaid.chunks(registers.chunks(), Optional.ofNullable(kind), registers.now()));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "giveFarmMission", description = "A command - only when the operator asks for it in this turn. Gives the mission "
            + "'a farm at' the chunk a block is in, carried out in three phases: the chunk flattened a block below the level, floored "
            + "in cobblestone there, the farm template on it. Given again for the same chunk at another level, it supersedes the open "
            + "one. Quote its result.")
    String giveFarmMission(@ToolParam(description = "a block's x in the chunk") int x, @ToolParam(description = "a block's z in the chunk") int z,
                           @ToolParam(required = false, description = "the farm's level, only if the operator said one; leave it out for sea level")
                           Integer y) {
        OptionalInt level = levelAsSaid(y, message);
        if (y != null && level.isEmpty()) {
            LOG.warn("[Chat] the platoon leader's farm at y={} read as sea level: the operator said no level", y);
        }
        return command("giveFarmMission " + x + " " + z + (y == null ? "" : " " + y), () -> commands.giveFarmMission(x, z, level));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "cancelMission", description = "A command - only when the operator asks for it in this turn. Cancels the farm "
            + "mission open on a chunk: its phases not done are closed SUPERSEDED with the operator's reason kept, none deleted, and it "
            + "isn't taken up again. Quote its result.")
    String cancelMission(@ToolParam(description = "the mission's chunk x, as its name says it: 'a farm at chunk (1, 3)' is 1") int chunkX,
                         @ToolParam(description = "the mission's chunk z: 'a farm at chunk (1, 3)' is 3") int chunkZ,
                         @ToolParam(description = "the operator's reason, in their words") String reason) {
        return command("cancelMission " + chunkX + " " + chunkZ + " \"" + reason + "\"", () -> commands.cancelMission(chunkX, chunkZ, reason));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "replacePlatoonMission", description = "A command - only when the operator asks for it in this turn. Replaces the "
            + "platoon's mission, which your own decisions carry out, and ends any escort.")
    String replacePlatoonMission(@ToolParam(description = "the new mission in plain words, with absolute coordinates ('hold x=-40 z=0'), "
            + "never relative ones") String mission) {
        return command("replacePlatoonMission \"" + mission + "\"", () -> commands.replacePlatoonMission(mission));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "escort", description = "A command - only when the operator asks for it in this turn. Attaches the first squad to a "
            + "player as a protective detail, until the platoon's mission is replaced.")
    String escort(@ToolParam(description = "the player's name") String player) {
        return command("escort " + player, () -> commands.escort(player));
    }

    @Tool(resultConverter = PlatoonLeaderTools.PlainText.class, name = "setFlattenGround", description = "A command - only when the operator asks for it in this turn. Sets the ground the "
            + "squads flatten by day: a rectangle of blocks taken down to a level.")
    String setFlattenGround(@ToolParam(description = "one corner's x") int x1, @ToolParam(description = "one corner's z") int z1,
                            @ToolParam(description = "the other corner's x") int x2, @ToolParam(description = "the other corner's z") int z2,
                            @ToolParam(description = "the level it's taken down to") int y) {
        return command("setFlattenGround " + x1 + " " + z1 + " " + x2 + " " + z2 + " " + y, () -> commands.setFlattenGround(
                new Flatten(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), y)));
    }

    /** A tool's text handed to the model as it's written, not as a JSON string with its line breaks escaped. */
    public static final class PlainText implements ToolCallResultConverter {
        @Override
        public String convert(Object result, Type returnType) {
            return String.valueOf(result);
        }
    }

    private interface Said {
        String get();
    }

    private String read(String call, Said said) {
        String result = said.get();
        LOG.info("[Chat] the platoon leader reads {}:\n{}", call, result);
        return result;
    }

    /** A command: carried out, what it did logged and kept as done - or, failing, logged and thrown, failing the turn. */
    private String command(String call, Said said) {
        String result;
        try {
            result = said.get();
        } catch (RuntimeException e) {
            LOG.error("[Chat] the platoon leader's {} failed: {}", call, e.getMessage());
            throw e;
        }
        LOG.info("[Chat] the platoon leader, told \"{}\", commands {}: {}", message, call, result);
        synchronized (this) {
            done.add(result);
        }
        return result;
    }

    /**
     * {@code y} kept only if the operator's {@code message} says it, as a number of its own: the level is the operator's to
     * give, sea level unless given. Live (field668), told "at sea level", the model gave y=67, and the mission was to flatten
     * a chunk to 66.
     */
    static OptionalInt levelAsSaid(Integer y, String message) {
        if (y == null) {
            return OptionalInt.empty();
        }
        boolean said = Pattern.compile("(?<![0-9-])" + Pattern.quote(String.valueOf(y)) + "(?![0-9])").matcher(message).find();
        return said ? OptionalInt.of(y) : OptionalInt.empty();
    }
}
