package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.SquadLeader;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A squad simulation's wiring, in one place: the chain of command with a scripted platoon leader and
 * squad leader, and the runner that joins the soldiers to a {@link SimServer} on a {@link SimClock}. A
 * scenario owns its clock, server and state directory, so each is passed in and nothing here is shared:
 * build one {@code SimSquad} per scenario.
 */
final class SimSquad {

    /** Where a kit-only squad spawns: the camp's cell centre on {@link SquadSimulationTest#GROUND}'s top. */
    static final PlayerPosition SPAWN = new PlayerPosition(0.5, SquadSimulationTest.GROUND + 1, 0.5);

    /** The camp's pond: one block of water at sea level ten east of the camp, the water a farm's first soil is hydrated from. */
    static boolean isCampPond(int x, int y, int z) {
        return y == SquadSimulationTest.GROUND && x == 10 && z == 0;
    }

    /** Flat, with the camp's pond. */
    static int pond(int x, int y, int z) {
        return isCampPond(x, y, z) ? SimServer.WATER : SquadSimulationTest.flat(x, y, z);
    }

    /** Alpha's four on a farm, the fourth its baker. */
    static final List<String> FARM_SQUAD = List.of("Alice", "Bob", "Carl", "Dana");

    /** {@link #FARM_SQUAD} ordered to the camp's farm, their squad leader keeping them there; the kit and the rest still to give. */
    static Builder farmSquad(SimClock clock, SimServer server) {
        Position farm = new Position(10.5, SquadSimulationTest.GROUND + 1, 2.5);
        Objective toFarm = new Objective("Farm", farm, "Bread for the depot", new Objective.Task.Farm());
        return of(clock, server, new Roster("Alpha:" + String.join(",", FARM_SQUAD))).orders(s -> toFarm).leaderAt(farm);
    }

    final SimClock clock;
    final SimServer server;
    final Roster roster;
    final ChainOfCommand chain;
    final SoldierRunner runner;

    private SimSquad(SimClock clock, SimServer server, Roster roster, ChainOfCommand chain, SoldierRunner runner) {
        this.clock = clock;
        this.server = server;
        this.roster = roster;
        this.chain = chain;
        this.runner = runner;
    }

    /** Joins the soldiers: for a squad {@link Builder#build built} and not yet run. */
    void run() {
        runner.run();
    }

    /** The squad {@code roster} names, on {@code server}'s ground and {@code clock}'s time. */
    static Builder of(SimClock clock, SimServer server, Roster roster) {
        return new Builder(clock, server, roster);
    }

    /** A squad leader that moves the squad on {@code at} in a wedge, at low risk, free to rearm. */
    static ChatClient squadLeader(Position at) {
        String reply = String.format(Locale.ROOT, """
                {"x": %.1f, "y": %.1f, "z": %.1f, "formation": "WEDGE", "interval": "NORMAL",
                 "verticalTolerance": 4, "risk": "LOW", "soldierRisks": [], "rearm": true,
                 "intent": "work the objective", "cantco": "", "roleChanges": [], "detached": []}
                """, at.x(), at.y(), at.z());
        return ChatClient.create((ChatModel) prompt ->
                new ChatResponse(List.of(new Generation(new AssistantMessage(reply)))));
    }

    /** A platoon leader that gives every squad the same objective, every time it's asked. */
    static PlatoonLeader scripted(Function<SquadStatus, Objective> objective) {
        return new PlatoonLeader() {
            @Override
            public void receiveMission(String mission) {
            }

            @Override
            public void receiveStockpile(Stockpile stockpile) {
            }

            @Override
            public void receiveRemark(String remark) {
            }

            @Override
            public void receiveFoothold(Foothold foothold) {
            }

            @Override
            public void receiveDayWork(io.github.jayessdeesea.minecraft.command.DayWork work) {
            }

            @Override
            public void receiveTime(TimeOfDay time) {
            }

            @Override
            public List<Objective> decide(List<SquadStatus> squads) {
                return squads.stream().map(objective).toList();
            }
        };
    }

    /**
     * What a squad is wired from. The platoon leader and squad leader are the scripted ones and the
     * home is "0,0" unless set; the soldiers join at {@link #SPAWN} with the kit given, or by the
     * connector given. {@code build} constructs the chain, then the runner, in that order.
     */
    static final class Builder {
        private final SimClock clock;
        private final SimServer server;
        private final Roster roster;
        private PlatoonLeader platoon;
        private ChatClient leader;
        private Function<String, SquadLeader> leaders;
        private boolean shelterAtNight;
        private Consumer<ChainOfCommand> beforeRunner = chain -> { };
        private String home = "0,0";
        private SoldierRunner.Connector connector;

        private Builder(SimClock clock, SimServer server, Roster roster) {
            this.clock = clock;
            this.server = server;
            this.roster = roster;
        }

        /** Every squad is given {@code objective}, however it is asked. */
        Builder orders(Function<SquadStatus, Objective> objective) {
            return platoon(scripted(objective));
        }

        Builder platoon(PlatoonLeader platoonLeader) {
            this.platoon = platoonLeader;
            return this;
        }

        /** The squad leader keeps the squad at {@code at}. */
        Builder leaderAt(Position at) {
            return leader(squadLeader(at));
        }

        Builder leader(ChatClient squadLeaderClient) {
            this.leader = squadLeaderClient;
            return this;
        }

        /** Each squad is led by the standing rules, built by {@code leaders}, not by a model's reply. */
        Builder leaders(Function<String, SquadLeader> leaders) {
            this.leaders = leaders;
            return this;
        }

        Builder shelterAtNight(boolean shelterAtNight) {
            this.shelterAtNight = shelterAtNight;
            return this;
        }

        /** Sets the chain up before the runner is built on it: the runner restores from the chain as it is made. */
        Builder beforeRunner(Consumer<ChainOfCommand> setup) {
            this.beforeRunner = setup;
            return this;
        }

        /** The runner's camp setting: "" for none, else the camp's x,z. */
        Builder home(String camp) {
            this.home = camp;
            return this;
        }

        /** Every soldier joins at {@link #SPAWN} carrying {@code kit} ([slot, item, count] each). */
        Builder kit(List<int[]> kit) {
            return connector(SPAWN, kit);
        }

        /** Every soldier joins at {@code spawn} carrying {@code kit}. */
        Builder connector(PlayerPosition spawn, List<int[]> kit) {
            return connector(server.connector(spawn, kit));
        }

        /** Each soldier joins at its own spawn, with its own kit. */
        Builder connector(Function<String, PlayerPosition> spawns, Function<String, List<int[]>> kits) {
            return connector(server.connector(spawns, kits));
        }

        Builder connector(SoldierRunner.Connector connector) {
            this.connector = connector;
            return this;
        }

        /** Wires the squad and joins its soldiers. */
        SimSquad start(Path state) {
            SimSquad squad = build(state);
            squad.run();
            return squad;
        }

        /** Wires the squad, its soldiers not yet joined. */
        SimSquad build(Path state) {
            ChainOfCommand chain = leaders != null
                    ? new ChainOfCommand(platoon, leaders, roster, shelterAtNight, clock)
                    : new ChainOfCommand(platoon, leader,
                            SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                                    new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, shelterAtNight, clock);
            beforeRunner.accept(chain);
            SoldierRunner runner = new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, home, state.toString())
                    .simulated(clock, connector);
            return new SimSquad(clock, server, roster, chain, runner);
        }
    }
}
