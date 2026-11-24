package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.TrackedEntity;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.DrillSquadLeader;
import io.github.jayessdeesea.minecraft.command.Flatten;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Subsistence;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import io.github.jayessdeesea.minecraft.protocol.PlayerPosition;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static io.github.jayessdeesea.minecraft.app.SquadSimulationTest.*;

/** The keep's ground in the squad simulation: dug down to the plan, its depot and workshop, its crew, the builders' steps and dirt. */
@Tag("slow")
class KeepSimulationTest {

    @TempDir
    Path state;

    @BeforeEach
    void nameTheScenario(TestInfo test) {
        ThreadContext.put("scenario", test.getTestMethod().map(Method::getName).orElse("unnamed"));
    }

    @AfterEach
    void endTheScenario() {
        ThreadContext.remove("scenario");
    }

/** Emerald ore, which only an iron pickaxe gets the drop from (the block registry's state id). */
    static final int EMERALD_ORE = 11315;

    static int indexOf(List<String> log, String suffix) {
        for (int i = 0; i < log.size(); i++) {
            if (log.get(i).endsWith(suffix)) {
                return i;
            }
        }
        return -1;
    }

    /** {@link #deepPocketBelow}, with a mound of dirt on the keep over the gallery: work in the stranded builder's strip. */
    static int deepPocketBelowAMound(int x, int y, int z) {
        return x >= 11 && x <= 13 && z >= -1 && z <= 1 && y > GROUND && y <= GROUND + 3 ? SimServer.DIRT : deepPocketBelow(x, y, z);
    }

    /**
     * The keep (command.Keep), taken down to y=63 and not built up (operator, 2026-11-15): a squad
     * sent to build it digs a mound on the site away to the ground's top, and leaves a pit in it as
     * it is - nothing laid, dirt or cobblestone.
     */
    @Test
    void aSquadOnTheKeepDigsItsSiteDownAndBuildsNothingUp() {
        SimClock clock = new SimClock();
        java.util.function.IntPredicate pitX = x -> x >= -6 && x <= -4;
        SimServer server = new SimServer(clock, (x, y, z) -> x >= 4 && x <= 5 && z >= 4 && z <= 5 && (y == GROUND + 1 || y == GROUND + 2)
                ? SimServer.DIRT
                : pitX.test(x) && pitX.test(z) && y >= GROUND - 1 && y <= GROUND ? SimServer.AIR // a pit in the ground's top
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{9, SimServer.ITEM_COBBLESTONE, 64}, new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(10 * 60_000);

        String events = "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        for (int x = 4; x <= 5; x++) {
            for (int z = 4; z <= 5; z++) {
                for (int y = Keep.BOTTOM; y <= GROUND + 2; y++) {
                    assertEquals(SimServer.AIR, server.block(x, y, z), "the mound dug away at " + x + "," + y + "," + z + events);
                }
            }
        }
        for (int x = -6; x <= -4; x++) {
            for (int z = -6; z <= -4; z++) {
                for (int y = GROUND - 1; y <= Keep.TOP; y++) {
                    assertEquals(SimServer.AIR, server.block(x, y, z), "the pit left as it is at " + x + "," + y + "," + z + events);
                }
            }
        }
    }

    /**
     * Ore set aside for a better pickaxe is taken by the keep's hand who carries one, whatever its
     * squad: live, the emerald was in one squad's share and the one iron pickaxe in the other's.
     */
    @Test
    void theKeepsEmeraldIsTakenByTheOneWithTheIronPickaxeFromAnotherSquad() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 12 && z == 0 && y == GROUND + 1 ? SimServer.EMERALD_ORE
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice;Bravo:Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        Map<String, PlayerPosition> spawns = Map.of(
                "Alice", new PlayerPosition(-8.5, GROUND + 1, 0.5), "Bob", new PlayerPosition(8.5, GROUND + 1, 0.5));
        Map<String, List<int[]>> kits = Map.of(
                "Alice", List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("iron_pickaxe"), 1}),
                "Bob", List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}));
        SoldierRunner.Connector connector = server.connector(spawns::get, kits::get);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString()).simulated(clock, connector).run();

        clock.advance(5 * 60_000);

        assertEquals(SimServer.AIR, server.block(12, GROUND + 1, 0), "the emerald taken; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * Any block that breaks is the keep's to dig, not a list of ground: a pane of glass over a mound
     * is dug, and the mound under it with it. Live, glass high over the keep stood, and every block
     * under it was never counted - the dig takes ground only from an open top.
     */
    @Test
    void glassOverTheKeepsSiteIsDugAndTheGroundUnderIt() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 4 && z == 4 && y == GROUND + 3 ? SimServer.GLASS
                : x >= 4 && x <= 5 && z >= 4 && z <= 5 && (y == GROUND + 1 || y == GROUND + 2) ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(4 * 60_000);

        assertEquals(SimServer.AIR, server.block(4, GROUND + 3, 4), "the glass; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        assertEquals(SimServer.AIR, server.block(4, GROUND + 2, 4), "the mound under the glass; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * A table and a chest left on the keep's site - a hasty workshop's, moved on from - are dug like
     * any block, and the ground under them with them. Live, six tables and two chests held eight
     * columns of the keep, and the strips round them waited at their height (operator, 2026-11-13).
     */
    @Test
    void aTableAndAChestLeftOnTheKeepAreDugWithTheGroundUnderThem() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 1 ? SimServer.DIRT
                : x == 3 && z == 3 && y == GROUND + 2 ? SimServer.CRAFTING_TABLE
                : x == -5 && z == -5 && y == GROUND + 1 ? SimServer.CHEST : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(6 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(SimServer.AIR, server.block(3, GROUND + 2, 3), "the table; " + events);
        assertEquals(SimServer.AIR, server.block(3, GROUND + 1, 3), "the ground the table stood on; " + events);
        assertEquals(SimServer.AIR, server.block(-5, GROUND + 1, -5), "the chest; " + events);
    }

    /**
     * Salvage (backlog/salvage-and-pack.md): a chest standing on the ground to flatten, iron in it, is emptied
     * before it's dug - broken, a chest spills what it holds on the ground.
     */
    @Test
    void aChestOnTheGroundToFlattenIsEmptiedBeforeItsDug() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> flat(x, y, z), 1000);
        server.stockedChest(-5, GROUND + 1, -5, SimServer.ITEM_RAW_IRON, 16);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(8 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(SimServer.AIR, server.block(-5, GROUND + 1, -5), "the chest dug; " + events);
        assertTrue(server.log.contains("Alice broke a chest at -5," + (GROUND + 1) + ",-5 holding 0 item(s)"),
                "emptied before it was dug; " + server.log.stream().filter(l -> l.contains("chest")).toList());
    }

    /**
     * The camp moved (backlog/camp-to-sea-level.md): its old camp's chest, a stack of cobblestone in it, is emptied
     * into the new camp's stockpile - by a squad on no flatten at all - what its salvager doesn't keep for itself.
     */
    @Test
    void anOldCampsChestIsEmptiedIntoTheNewCampsStockpile() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> flat(x, y, z), 1000);
        server.stockedChest(70, GROUND + 1, 2, SimServer.ITEM_COBBLESTONE, 64);
        new WallStore(state, "chests").save(SiteGeometry.CAMPS, List.of(new BlockPos(70, 0, 0)));
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(40.5, GROUND + 1, 0.5); // both off the keep, the default ground to flatten
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "40,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(40.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.ITEM_OAK_LOG, 16})))
                .run();

        clock.advance(8 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(0, held(server, 70, GROUND + 1, 2), "the old camp's chest emptied; " + events);
        int atCamp = server.chests.entrySet().stream().filter(e -> e.getKey() != SimServer.key(70, GROUND + 1, 2))
                .mapToInt(e -> Arrays.stream(e.getValue()).filter(st -> st != null && st[0] == SimServer.ITEM_COBBLESTONE).mapToInt(st -> st[1]).sum()).sum();
        assertTrue(atCamp >= 32, "its cobblestone in the new camp's chest: " + atCamp + "; " + events);
    }

    /** The old camp's nearest chest walled in, out of reach: passed over, the next one emptied. Live (field510), one all day. */
    @Test
    void aChestOutOfReachIsPassedOverForTheNext() {
        SimClock clock = new SimClock();
        // The near chest at (64, 1, 0) boxed in bedrock two high round it; the far one at (74, 1, 0) in the open.
        SimServer server = new SimServer(clock, (x, y, z) -> Math.abs(x - 64) <= 1 && Math.abs(z) <= 1 && !(x == 64 && z == 0)
                && y > GROUND && y <= GROUND + 3 ? SimServer.BEDROCK : x == 64 && z == 0 && y == GROUND + 2 ? SimServer.BEDROCK : flat(x, y, z), 1000);
        server.stockedChest(64, GROUND + 1, 0, SimServer.ITEM_COBBLESTONE, 64);
        server.stockedChest(74, GROUND + 1, 0, SimServer.ITEM_COBBLESTONE, 64);
        new WallStore(state, "chests").save(SiteGeometry.CAMPS, List.of(new BlockPos(70, 0, 0)));
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(40.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "40,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(40.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.ITEM_OAK_LOG, 16})))
                .run();

        clock.advance(30 * 60_000); // its tries at the near one backed off first, and the night between - salvage is by day

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(0, held(server, 74, GROUND + 1, 0), "the far chest emptied, the near one passed over; " + events);
    }

    /** The old camp's chest under a block of cobblestone, which no chest opens under: the block broken, the chest emptied. */
    @Test
    void aChestUnderABlockHasTheBlockBrokenAndIsEmptied() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 70 && y == GROUND + 2 && z == 2 ? SimServer.COBBLESTONE : flat(x, y, z), 1000);
        server.stockedChest(70, GROUND + 1, 2, SimServer.ITEM_COBBLESTONE, 64);
        new WallStore(state, "chests").save(SiteGeometry.CAMPS, List.of(new BlockPos(70, 0, 0)));
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(40.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "40,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(40.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.ITEM_OAK_LOG, 16})))
                .run();

        clock.advance(10 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(SimServer.AIR, server.block(70, GROUND + 2, 2), "the block over the chest broken; " + events);
        assertEquals(0, held(server, 70, GROUND + 1, 2), "the chest emptied; " + events);
    }

    /**
     * A furnace over the chest, as a workshop's stood over a stockpile's live (field704-707): four of a squad stood in
     * reach of it for hours, their trips never begun - the block over a chest is broken first, and a furnace was left
     * standing as a workshop's, so nothing was, every tick, and nothing said so. A furnace over a chest is broken as any block is.
     */
    @Test
    void aChestUnderAFurnaceHasTheFurnaceBrokenAndIsEmptied() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 70 && y == GROUND + 2 && z == 2 ? SimServer.FURNACE : flat(x, y, z), 1000);
        server.stockedChest(70, GROUND + 1, 2, SimServer.ITEM_COBBLESTONE, 64);
        new WallStore(state, "chests").save(SiteGeometry.CAMPS, List.of(new BlockPos(70, 0, 0)));
        Roster roster = new Roster("Alpha:Alice");
        Position camp = new Position(40.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "40,0", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(40.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.ITEM_OAK_LOG, 16})))
                .run();

        clock.advance(10 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(SimServer.AIR, server.block(70, GROUND + 2, 2), "the furnace over the chest broken; " + events);
        assertEquals(0, held(server, 70, GROUND + 1, 2), "the chest emptied; " + events);
    }

    /** The nearest soldier's pack full, another salvages the old camp's chest: live (field503-504), a full pack held it all day. */
    @Test
    void aSalvagerWithAFullPackHandsItOn() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> flat(x, y, z), 1000);
        server.stockedChest(70, GROUND + 1, 2, SimServer.ITEM_COBBLESTONE, 64);
        new WallStore(state, "chests").save(SiteGeometry.CAMPS, List.of(new BlockPos(70, 0, 0)));
        Roster roster = new Roster("Alpha:Alice,Bob");
        Position camp = new Position(40.5, GROUND + 1, 0.5);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Hold the camp", camp, "A foothold",
                new Objective.Task.None())), squadLeader(camp),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        List<int[]> full = new ArrayList<>(List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                new int[]{38, SimServer.ITEM_OAK_LOG, 16}));
        for (int slot = 9; slot <= 35; slot++) {
            full.add(new int[]{slot, SimServer.ITEM_DIRT, 64});
        }
        for (int slot = 39; slot <= 44; slot++) {
            full.add(new int[]{slot, SimServer.ITEM_DIRT, 64});
        }
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "40,0", state.toString())
                .simulated(clock, server.connector(name -> new PlayerPosition(name.equals("Alice") ? 60.5 : 40.5, GROUND + 1, 0.5),
                        name -> name.equals("Alice") ? full : List.of(new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                                new int[]{37, SimServer.ITEM_BREAD, 64}, new int[]{38, SimServer.ITEM_OAK_LOG, 16})))
                .run();

        clock.advance(10 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(0, held(server, 70, GROUND + 1, 2), "the old camp's chest emptied, by Bob; " + events);
    }

    private static int held(SimServer server, int x, int y, int z) {
        return Arrays.stream(server.chests.get(SimServer.key(x, y, z))).filter(java.util.Objects::nonNull).mapToInt(st -> st[1]).sum();
    }

    /**
     * Emerald ore standing two blocks over the keep, its builder carrying a stone pickaxe: the ore is
     * set aside, with the dirt it stands on, and the site still comes down past it - the mound a level
     * lower elsewhere is dug. Left in the dig, the ore would be its strip's top for good, and in lock
     * step hold the whole site at its level (operator, 2026-11-14: an emerald the workers ignore).
     */
    @Test
    void oreItsBuildersPickaxeWontGetIsSetAsideAndTheSiteGoesOnDown() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 3 && z == 3 && y == GROUND + 1 ? SimServer.DIRT
                : x == 3 && z == 3 && y == GROUND + 2 ? EMERALD_ORE
                : x == -5 && z == -5 && y == GROUND + 1 ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(6 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        assertEquals(EMERALD_ORE, server.block(3, GROUND + 2, 3), "the emerald ore left for an iron pickaxe; " + events);
        assertEquals(SimServer.AIR, server.block(-5, GROUND + 1, -5), "the mound a level lower, dug all the same; " + events);
    }

    /**
     * A hill of dirt over the keep's depot, with a one-wide tunnel cut into it at the slab's height
     * - as live, west of the keep. The keep's workshop goes on the hill's surface, not in the tunnel,
     * where no stand has a free cell beside it and both squads said "nowhere" every second for hours.
     */
    @Test
    void theKeepsWorkshopIsSitedOnTheSurfaceOverItsDepotNotInATunnelUnderTheHill() {
        int dx = (int) Math.floor(Keep.DEPOT.x()), dz = (int) Math.floor(Keep.DEPOT.z());
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> {
            boolean hill = x >= dx - 6 && x <= dx + 6 && z >= dz - 6 && z <= dz + 6 && y > GROUND && y <= GROUND + 9;
            boolean tunnel = z == dz && x >= dx - 6 && x <= dx && (y == GROUND + 1 || y == GROUND + 2);
            return hill && !tunnel ? SimServer.DIRT : flat(x, y, z);
        }, 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1})))
                .run();

        clock.advance(30_000);

        List<String> sited;
        try {
            sited = Files.readAllLines(Path.of("target/sim/theKeepsWorkshopIsSitedOnTheSurfaceOverItsDepotNotInATunnelUnderTheHill.log")).stream()
                    .filter(l -> l.contains("the keep's workshop: stand") || l.contains("nowhere at the keep's depot")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(!sited.isEmpty() && sited.stream().allMatch(l -> l.contains("y=" + (GROUND + 10))),
                "the workshop on the hill's top, said once: " + sited);
    }

    /**
     * The keep's wood party (operator, 2026-11-14: trees are everywhere; a sapling every four
     * blocks): three builders, no logs between them, a dirt mound on the keep and a row of spruce
     * well west of the depot. The foreman sends one to cut trees and one to plant the keep's grove,
     * and the third digs. Live, eight builders with one chest's logs between them stood a depot at
     * two chests, both full.
     */
    @Test
    void theKeepsWoodPartyCutsTreesAndPlantsAGroveWhileTheRestDig() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -6 && x <= 5 && z >= -6 && z <= 5 && y > GROUND && y <= GROUND + 3
                ? SimServer.DIRT
                : x == -64 && Math.floorMod(z, 5) == 0 && Math.abs(z) <= 20 && y > GROUND && y <= GROUND + 5 ? SimServer.SPRUCE_LOG
                : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(-8.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{39, SimServer.itemNamedOrMinus("stone_shovel"), 1},
                        new int[]{40, SimServer.itemNamedOrMinus("cobblestone"), 16},
                        new int[]{42, SimServer.itemNamedOrMinus("oak_log"), 1},
                        new int[]{41, SimServer.itemNamedOrMinus("spruce_sapling"), 8},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(12 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        long saplings = 0;
        for (int x = -80; x <= -20; x++) {
            for (int z = -30; z <= 30; z++) {
                saplings += server.block(x, GROUND + 1, z) == SimServer.SPRUCE_SAPLING ? 1 : 0;
            }
        }
        assertTrue(saplings >= 4, "saplings planted in the keep's grove: " + saplings + "; " + events);
        assertTrue(server.log.stream().anyMatch(e -> e.matches(".* dug " + SimServer.SPRUCE_LOG + " at .*")), "a tree cut; " + events);
        assertTrue(Files.exists(Path.of("target/sim/theKeepsWoodPartyCutsTreesAndPlantsAGroveWhileTheRestDig.log")));
        List<String> made;
        try {
            made = Files.readAllLines(Path.of("target/sim/theKeepsWoodPartyCutsTreesAndPlantsAGroveWhileTheRestDig.log")).stream()
                    .filter(l -> l.contains("made 1 stone axe")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(!made.isEmpty(), "the cutter made itself a stone axe first");
        int left = 0;
        for (int x = -6; x <= 5; x++) {
            for (int z = -6; z <= 5; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    left += server.block(x, y, z) != SimServer.AIR ? 1 : 0;
                }
            }
        }
        assertTrue(left < 432 / 2, "and the mound dug meanwhile: " + left + " of 432 left; " + events);
    }

    /**
     * A depot is chests, not a chest (operator, 2026-11-14): the keep's depot with its first chest
     * full, and a builder with a full pack and the logs for another. It finds the first full, fits
     * the next of the depot's row, and its load goes into that. Live, the depot's one chest filled
     * with the first builder's load and every builder after walked to it, failed, and walked back.
     */
    @Test
    void aDepotsNextChestGoesUpWhenItsFirstIsFull() {
        int dx = (int) Math.floor(Keep.DEPOT.x()), dz = (int) Math.floor(Keep.DEPOT.z());
        SimClock clock = new SimClock();
        // The depot's table standing, east of its stand, and its first chest behind the table.
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -6 && x <= 5 && z >= -6 && z <= 5 && y > GROUND && y <= GROUND + 3
                ? SimServer.DIRT : x == dx + 1 && y == GROUND + 1 && z == dz ? SimServer.CRAFTING_TABLE : flat(x, y, z), 1000);
        // Full of logs, which have no most (StockLevels): full of cobblestone, the depot would have its most
        // of it, and the load would go to the dump.
        server.fullChest(dx + 2, GROUND + 1, dz, SimServer.itemNamedOrMinus("oak_log"));
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        List<int[]> pack = new ArrayList<>(List.of(new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_sword"), 1},
                new int[]{40, SimServer.itemNamedOrMinus("stone_shovel"), 1}, new int[]{41, SimServer.itemNamedOrMinus("oak_log"), 16}));
        // Five slots free: the first dirt it digs leaves four, a keep builder's full load.
        for (int slot = 9; slot <= 32; slot++) {
            pack.add(new int[]{slot, SimServer.itemNamedOrMinus("cobblestone"), 64});
        }
        pack.add(new int[]{42, SimServer.itemNamedOrMinus("stone_pickaxe"), 1});
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(Keep.DEPOT.x(), GROUND + 1, Keep.DEPOT.z() + 3), pack))
                .run();

        clock.advance(8 * 60_000);

        String events = "last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size());
        // Two at least: the dirt it digs after goes in too (StockLevels), and the row grows on as each fills.
        assertTrue(server.chests.size() >= 2, "a second chest at the depot: " + server.chests.size() + "; " + events);
        assertTrue(server.chests.values().stream().allMatch(c -> java.util.Arrays.stream(c).anyMatch(java.util.Objects::nonNull)),
                "and the builder's load in it; " + events);
    }

    /**
     * A level wide enough for everyone is everyone's: four builders, a slab of dirt eight by eight on
     * the keep, and the foreman's plan puts all four on it, a rectangle each, and it's gone. Live,
     * with a fixed strip each and the high ground in two of eight, six builders stood by while two dug.
     */
    @Test
    void aWideLevelIsSharedOutAmongTheWholeCrew() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -4 && x <= 3 && z >= -4 && z <= 3 && y == GROUND + 1
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(-8.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(3 * 60_000);

        int left = 0;
        for (int x = -4; x <= 3; x++) {
            for (int z = -4; z <= 3; z++) {
                left += server.block(x, GROUND + 1, z) != SimServer.AIR ? 1 : 0;
            }
        }
        List<String> plans;
        try {
            plans = Files.readAllLines(Path.of("target/sim/aWideLevelIsSharedOutAmongTheWholeCrew.log")).stream()
                    .filter(l -> l.contains("[Keep] level y=")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(plans.stream().anyMatch(l -> l.contains("4 of 4 builder(s) on it")), "all four on the level: " + plans);
        assertEquals(0, left, "the slab dug; the plans: " + plans);
    }

    /**
     * The squad led by its drills, no model (backlog/fleet-command.md): from 8 blocks west of the keep,
     * the drills move the squad onto it and its four builders dig the level, as the scripted model's
     * order did.
     */
    @Test
    void theSquadsDrillsLeadItOntoTheKeepWithNoModel() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= -4 && x <= 3 && z >= -4 && z <= 3 && y == GROUND + 1
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob,Carol,Dana");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), DrillSquadLeader::new, roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(-8.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        clock.advance(3 * 60_000);

        int left = 0;
        for (int x = -4; x <= 3; x++) {
            for (int z = -4; z <= 3; z++) {
                left += server.block(x, GROUND + 1, z) != SimServer.AIR ? 1 : 0;
            }
        }
        assertEquals(0, left, "the slab dug under the drills' lead; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 10), server.log.size()));
    }

    /**
     * One builder in contact at its end of the keep, zombie after zombie, for three minutes: the
     * other digs on. A spire at the far end is one builder's by the foreman's plan - the nearer one's -
     * and the fight at the other end, under which the ground is nobody's, doesn't hold it. Live, a
     * single zombie at the keep's edge held eight builders off it for a quarter of an hour, every one
     * of them answering the squad leader's "move to the keep" instead - contact takes the soldiers
     * in it off the work, not the squad.
     */
    @Test
    void aBuilderInContactDoesntTakeTheOthersOffTheKeep() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == -12 && z == 0 && y > GROUND && y <= GROUND + 3
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(name -> name.equals("Alice") ? new PlayerPosition(-8.5, GROUND + 1, 0.5)
                        : new PlayerPosition(8.5, GROUND + 1, 0.5), name -> List.of(
                        new int[]{36, SimServer.itemNamedOrMinus("stone_sword"), 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 64})))
                .run();

        // A zombie at Bob's end of the keep as soon as the last is down: Bob in contact the whole while.
        for (int i = 0; i < 90; i++) {
            if (server.zombiesLeft() == 0) {
                server.spawnZombie(14.5, GROUND + 1, 0.5);
            }
            clock.advance(2000);
        }

        List<String> left = new ArrayList<>();
        for (int y = GROUND + 1; y <= GROUND + 3; y++) {
            if (server.block(-12, y, 0) != SimServer.AIR) {
                left.add("-12," + y + ",0");
            }
        }
        assertEquals(List.of(), left, "Alice's spire, dug while Bob fought; Alice at " + server.position("Alice")
                + "; last server events: " + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * The keep's haul road (docs/military-doctrine.md § 17, rule 5; operator, 2026-11-15): the depot
     * on a hill off the keep's west edge, eight blocks over its floor, gets one way down carved into
     * the hill - so a walk from beside the depot to the floor needs nothing dug and nothing placed.
     */
    @Test
    void theKeepsDepotOnAHillGetsAHaulRoadCarvedDownToTheFloor() {
        int dx = (int) Math.floor(Keep.DEPOT.x()), dz = (int) Math.floor(Keep.DEPOT.z());
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x < Keep.MIN && x >= dx - 8 && z >= dz - 10 && z <= dz + 10
                && y > GROUND && y <= GROUND + 8 ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_shovel"), 1})))
                .run();

        clock.advance(8 * 60_000);

        io.github.jayessdeesea.minecraft.bot.world.WorldView seen = new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
            @Override
            public boolean isSolid(int x, int y, int z) {
                int b = server.block(x, y, z);
                return b != SimServer.AIR && b != SimServer.WATER;
            }

            @Override
            public boolean isHazard(int x, int y, int z) {
                return server.block(x, y, z) == SimServer.WATER;
            }

            @Override
            public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(Position center, double radius) {
                return List.of();
            }
        };
        io.github.jayessdeesea.minecraft.bot.nav.BlockPos top = new io.github.jayessdeesea.minecraft.bot.nav.BlockPos(dx, GROUND + 9, dz);
        boolean walk = java.util.stream.IntStream.rangeClosed(dz - 10, dz + 10).anyMatch(z -> io.github.jayessdeesea.minecraft.bot.nav.Pathfinder
                .find(seen, top, new io.github.jayessdeesea.minecraft.bot.nav.Goal(Keep.MIN + 0.5, GROUND + 1, z + 0.5, 0.5, 0.5), 20_000).reachesGoal());
        assertTrue(walk, "a walk from the hilltop by the depot down to the keep's floor, nothing dug or placed; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * Flattening is a primitive of its own, not the keep's (operator, 2026-11-15): the operator names
     * a rectangle and a level, and by day the squad takes it down - here a mound well off the keep.
     */
    @Test
    void theOperatorsGroundIsFlattenedWhereverItIs() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= 40 && x <= 43 && z >= 40 && z <= 43 && y > GROUND && y <= GROUND + 2
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        Flatten site = new Flatten(38, 38, 45, 45, GROUND);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Flatten it", site.centre(), "The operator's ground",
                new Objective.Task.Keep())), squadLeader(site.centre()),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        chain.onOperatorFlatten("Pat", site);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(36.5, GROUND + 1, 36.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{10, SimServer.ITEM_DIRT, 16})))
                .run();

        clock.advance(6 * 60_000);

        List<String> left = new ArrayList<>();
        for (int x = 40; x <= 43; x++) {
            for (int z = 40; z <= 43; z++) {
                for (int y = GROUND + 1; y <= GROUND + 2; y++) {
                    if (server.block(x, y, z) == SimServer.DIRT) {
                        left.add(x + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(List.of(), left, "the mound taken down to the level; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

    /**
     * The benched edge (clearing rule 6; 29 CFR 1926.652's 1:1): a site cut against a hill six high
     * leaves no cliff at its edge - the ring d out is down to d over the floor, a walkable slope, and
     * the hill past the bench stands. Live, soldiers put steps up the edge's wall, each a level.
     */
    @Test
    void aSiteCutIntoAHillLeavesItsEdgeBenched() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= 40 && x <= 45 + Flatten.BENCH + 2 && z >= 36 && z <= 47 && y > GROUND && y <= GROUND + 6
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        Flatten site = new Flatten(38, 38, 45, 45, GROUND);
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Flatten it", site.centre(), "The operator's ground",
                new Objective.Task.Keep())), squadLeader(site.centre()),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        chain.onOperatorFlatten("Pat", site);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(36.5, GROUND + 1, 41.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{39, SimServer.itemNamedOrMinus("stone_shovel"), 1},
                        new int[]{10, SimServer.ITEM_DIRT, 32})))
                .run();

        clock.advance(20 * 60_000);

        List<String> high = new ArrayList<>();
        for (int d = 1; d <= Flatten.BENCH; d++) {
            for (int z = 40; z <= 43; z++) {
                for (int y = GROUND + d + 1; y <= GROUND + 6; y++) {
                    if (server.block(45 + d, y, z) == SimServer.DIRT) {
                        high.add((45 + d) + "," + y + "," + z);
                    }
                }
            }
        }
        assertEquals(List.of(), high, "the bench east of the site down to d over the floor; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        assertEquals(SimServer.DIRT, server.block(45 + Flatten.BENCH + 1, GROUND + 6, 41), "the hill past the bench stands");
    }

    /**
     * The keep's foreman says its plan for each level over the net (operator, 2026-11-14), point to
     * point: each builder its rectangle, or to wait when the level has less work than builders.
     */
    @Test
    void theKeepsForemanSaysEachLevelsPlanOverTheNet() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x == 5 && z == 5 && y > GROUND && y <= GROUND + 2 ? SimServer.DIRT
                : x == -5 && z == -5 && y > GROUND && y <= GROUND + 2 ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice,Bob");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1}, new int[]{37, SimServer.ITEM_BREAD, 64},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1}, new int[]{10, SimServer.ITEM_DIRT, 32})))
                .run();

        clock.advance(4 * 60_000);

        assertTrue(server.log.stream().anyMatch(e -> e.startsWith("Alice /msg Bob KEEP DIG ") || e.startsWith("Alice /msg Bob KEEP WAIT ")),
                "Bob told the level's plan by the foreman: " + server.log.stream().filter(e -> e.contains("/msg")).toList());
        assertEquals(SimServer.AIR, server.block(5, GROUND + 2, 5), "a mound dug; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
        assertEquals(SimServer.AIR, server.block(-5, GROUND + 2, -5), "and the other");
    }

    /**
     * Food first: a soldier hungry with nothing to eat is left off the keep, to feed itself - its mound
     * left standing. One merely short of rations works on (`Subsistence.mustFeed`).
     */
    @Test
    void aStarvingSoldierIsLeftOffTheKeepToFeedItself() {
        SimClock clock = new SimClock();
        SimServer server = new SimServer(clock, (x, y, z) -> x >= 4 && x <= 5 && z >= 4 && z <= 5 && (y == GROUND + 1 || y == GROUND + 2)
                ? SimServer.DIRT : flat(x, y, z), 1000);
        Roster roster = new Roster("Alpha:Alice");
        ChainOfCommand chain = new ChainOfCommand(scripted(s -> new Objective("Build the keep", Keep.CENTRE, "The main base's keep",
                new Objective.Task.Keep())), squadLeader(Keep.CENTRE),
                SquadLeaderPrompts.read(new ClassPathResource("prompts/squad-leader-system-prompt.md"),
                        new ClassPathResource("prompts/squad-leader-user-prompt.md")), roster, false, clock);
        new SoldierRunner(chain, new OperatorOrders(""), "", roster, false, "", state.toString())
                .simulated(clock, server.connector(new PlayerPosition(0.5, GROUND + 1, 0.5), List.of(
                        new int[]{36, SimServer.ITEM_WOODEN_SWORD, 1},
                        new int[]{38, SimServer.itemNamedOrMinus("stone_pickaxe"), 1})))
                .run();
        clock.advance(2_000);
        server.starve("Alice", 20); // hungry - food 0 - and nothing to eat

        clock.advance(4 * 60_000);

        assertEquals(SimServer.DIRT, server.block(4, GROUND + 2, 4), "the mound dug by a starving soldier; last server events: "
                + server.log.subList(Math.max(0, server.log.size() - 15), server.log.size()));
    }

}
