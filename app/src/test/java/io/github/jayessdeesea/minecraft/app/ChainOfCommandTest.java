package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Keep;
import io.github.jayessdeesea.minecraft.command.PlatoonSergeant;
import io.github.jayessdeesea.minecraft.command.Progress;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Resource;
import io.github.jayessdeesea.minecraft.command.ResourceSite;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;
import io.github.jayessdeesea.minecraft.command.SquadStatus;
import io.github.jayessdeesea.minecraft.command.TimeOfDay;
import io.github.jayessdeesea.minecraft.command.ai.SquadLeaderPrompts;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainOfCommandTest {

    private final ChainOfCommand chain = new ChainOfCommand(
            (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
            ChatClient.create((ChatModel) prompt -> {
                throw new AssertionError("no model call expected");
            }), new SquadLeaderPrompts("", ""),
            new Roster("Alpha:Alice"), true, "model");

    private static final Objective WALL = new Objective("Wall the camp in", new Position(10, 64, 10), "Nothing walks in",
            new Objective.Task.Fortify());

    /**
     * From a squad down a mine, the ground near its height at the objective's column is the shaft's
     * floor: the objective goes on the surface. Live, a wood-gathering objective at the keep mine's
     * head was put at the shaft's bottom, 40 blocks down, and the squad found no tree there.
     */
    @Test
    void anObjectiveOverAMineGoesOnTheSurfaceNotTheShaftsFloor() {
        chain.setGround(new ChainOfCommand.Ground() {
            @Override
            public OptionalInt heightAt(int x, int z, int nearY) {
                return OptionalInt.of(24); // the shaft's floor, near the squad's height
            }

            @Override
            public OptionalInt surfaceAt(int x, int z, int nearY) {
                return OptionalInt.of(70);
            }
        });
        Objective wood = new Objective("Logs for the keep", new Position(-27, 65, -23), "The keep's chest",
                new Objective.Task.Gather(Resource.WOOD));
        List<SoldierStatus> down = List.of(new SoldierStatus("Alice", new Position(-26.5, 24, -22.5), new Order.Idle(), new Report.Mining(1, 9), Optional.empty()));

        assertEquals(70, (int) chain.grounded(wood, down).location().y(), "the surface, not the shaft's floor");
        chain.setGround((x, z, nearY) -> OptionalInt.of(24));
        assertEquals(24, (int) chain.grounded(wood, down).location().y(), "with no surface known, the ground near the squad");
    }

    @Test
    void underTheKeepRuleEverySquadBuildsTheKeepByDayAndShelterAtNight() {
        chain.setKeepByDay(true);
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        SquadStatus alpha = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false);
        chain.onTime(TimeOfDay.of(1000, true));

        Objective byDay = chain.byDay(WALL, alpha);

        assertTrue(byDay.task() instanceof Objective.Task.Keep, byDay.toString());
        assertEquals(byDay, chain.byDay(byDay, alpha), "a squad on the keep is kept on it");
        // Each squad its own point round the ground's middle: four point men don't make for one cell.
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        assertTrue(Cohesion.distance(two.spread(new Position(0, 64, 0), "Alpha"), two.spread(new Position(0, 64, 0), "Bravo")) >= ChainOfCommand.SPREAD,
                "Alpha's and Bravo's points apart");
        chain.onTime(TimeOfDay.of(18000, true));
        assertEquals(WALL, chain.byDay(WALL, alpha), "the night is the night rule's");
    }

    /**
     * Only as many squads on the ground as its level wants (backlog/flatten-campaign.md): a level for
     * one builder keeps the first squad and releases the other to the camp's work; a full floor
     * calls it back.
     */
    @Test
    void theSquadsOnTheGroundAreSizedToItsLevel() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.setKeepByDay(true);
        two.setGround((x, z, nearY) -> OptionalInt.of(64));
        two.onTime(TimeOfDay.of(1000, true));
        two.onFlattenCrew(1);

        assertTrue(two.byDay(WALL, squad("Alpha", "Alice")).task() instanceof Objective.Task.Keep, "the first squad on the ground");
        Objective released = two.byDay(WALL, squad("Bravo", "Bob"));
        assertEquals(WALL, released, "the other to the platoon leader's own objective");
        Objective keep = two.byDay(WALL, squad("Alpha", "Alice"));
        assertTrue(two.byDay(keep, squad("Bravo", "Bob")).task() instanceof Objective.Task.Keep,
                "the ground given it by the platoon leader: its day's work stands (operator, 2026-11-17)");

        Objective toTheMine = two.byDay(WALL, squad("Bravo", "Bob"), Optional.of(PlatoonSergeant.Shortfall.IRON), List.of());
        assertTrue(toTheMine.task() instanceof Objective.Task.Mine, "released, to the force's shortfall: " + toTheMine);
        SquadStatus stalled = new SquadStatus("Bravo", null, squad("Bravo", "Bob").soldiers(), List.of(), Optional.of(toTheMine), false,
                java.util.OptionalInt.empty(), Optional.of(new Progress(Progress.State.STALLED, "no ore")), Optional.empty());
        assertEquals(WALL, two.byDay(WALL, stalled, Optional.of(PlatoonSergeant.Shortfall.IRON), List.of()),
                "stalled on it: the platoon leader's objective instead");

        two.onFlattenCrew(40);
        assertTrue(two.byDay(WALL, squad("Bravo", "Bob")).task() instanceof Objective.Task.Keep, "a full floor: called back");
    }

    /**
     * The farm squad keeps the camp's farm by day whatever it's given, the keep included; the others
     * flatten (operator, 2026-11-15: a dedicated squad for the farming).
     */
    @Test
    void underTheKeepRuleTheFarmSquadKeepsTheFarm() {
        chain.setKeepByDay(true);
        chain.setFarmSquad("Delta");
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(1000, true));
        Objective keep = chain.byDay(WALL, squad("Alpha", "Alice"));

        Objective farm = chain.byDay(keep, squad("Delta", "Mia"));

        assertTrue(keep.task() instanceof Objective.Task.Keep, keep.toString());
        assertTrue(farm.task() instanceof Objective.Task.None, farm.toString());
        assertEquals("Keep the camp's farm", farm.mission());
    }

    /**
     * A request outranks the standing rule, or it's never answered: under the keep rule the platoon leader's
     * WOOD for a squad whose Class IV is RED stands; the same order for one with no request is put
     * on the keep. Live, the keep's builders asked for logs all day and the rule put every squad back.
     */
    @Test
    void underTheKeepRuleTheGeneralsAnswerToARequestStands() {
        chain.setKeepByDay(true);
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(1000, true));
        Objective wood = new Objective("Logs for the keep", new Position(40, 64, 40), "The keep's chest and pickaxes",
                new Objective.Task.Gather(Resource.WOOD));
        SquadStatus asking = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false, OptionalInt.empty(), Optional.empty(), Optional.empty(),
                PlatoonSergeant.classFour(1, 0, false));

        assertEquals(wood, chain.byDay(wood, asking), "its request answered: the order stands");
        assertEquals(wood, chain.byDay(wood, squad("Bravo", "Bob")), "no request: the platoon leader's day's work stands all the same");
        assertTrue(chain.byDay(WALL, squad("Bravo", "Bob")).task() instanceof Objective.Task.Keep, "work that isn't the day's: the keep");
    }

    /**
     * Food and wood both short, the squads asking for food are dealt across both, food first: of three,
     * two farm and one cuts (operator, 2026-11-17). Live, every squad farmed and nobody cut at 0 logs.
     */
    @Test
    void withFoodAndWoodBothShortTheHungrySquadsAreDealtAcrossBoth() {
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));
        Objective farm = new Objective("Farm", new Position(-48, 64, 44), "Food", new Objective.Task.Farm());
        List<PlatoonSergeant.Shortfall> alarms = List.of(PlatoonSergeant.Shortfall.FOOD, PlatoonSergeant.Shortfall.WOOD);
        List<ResourceSite> woods = List.of(new ResourceSite(Resource.WOOD, new Position(30, 64, 30), 10));

        List<SquadStatus> squads = List.of(hungry("Alpha", "Alice"), hungry("Bravo", "Bob"), hungry("Charlie", "Carol"));
        // The platoon leader sends all three to the farm: its orders stand, and the guardrails put one on the
        // flatten and one on the wood nobody was sent to - one flattens, one farms, one cuts (operator, 2026-11-17).
        List<Objective> day = squads.stream().map(s -> three.byDay(farm, s, alarms, woods)).toList();
        List<Objective> guarded = three.guarded(day, squads, alarms, woods);

        assertEquals(1, guarded.stream().filter(o -> o.task() instanceof Objective.Task.Keep).count(), "one on the flatten: " + guarded);
        assertEquals(1, guarded.stream().filter(o -> o.task() instanceof Objective.Task.Farm).count(), "one farms: " + guarded);
        assertEquals(1, guarded.stream().filter(o -> o.task().equals(new Objective.Task.Gather(Resource.WOOD))).count(), "one cuts: " + guarded);
        List<Objective> idle = three.guarded(day, squads, List.of(), woods);
        assertEquals(1, idle.stream().filter(o -> o.task() instanceof Objective.Task.Keep).count(), "short of nothing: one onto the flatten");
        assertEquals(2, idle.stream().filter(o -> o.task() instanceof Objective.Task.Farm).count(), "and the platoon leader's farms stand");
    }

    /**
     * Short of anything, one squad flattens and the rest go to what's short, however wide the level
     * (operator, 2026-11-17); short of nothing, the level's sizing stands.
     */
    @Test
    void shortOfAnythingOneSquadFlattensAndTheRestCollect() {
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));
        three.onFlattenCrew(40);
        List<PlatoonSergeant.Shortfall> food = List.of(PlatoonSergeant.Shortfall.FOOD);

        assertTrue(three.byDay(WALL, squad("Alpha", "Alice"), food, List.of()).task() instanceof Objective.Task.Keep, "the first flattens");
        assertTrue(three.byDay(WALL, squad("Bravo", "Bob"), food, List.of()).task() instanceof Objective.Task.Farm, "the second farms");
        assertTrue(three.byDay(WALL, squad("Bravo", "Bob"), List.of(), List.of()).task() instanceof Objective.Task.Keep,
                "short of nothing: the level's sizing, every squad on it");
    }

    private static SquadStatus hungry(String name, String soldier) {
        return new SquadStatus(name, new Report.InPosition(),
                List.of(new SoldierStatus(soldier, new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false, OptionalInt.empty(), Optional.empty(),
                Optional.of(new io.github.jayessdeesea.minecraft.command.Subsistence.Status(
                        io.github.jayessdeesea.minecraft.command.Subsistence.Rating.RED, List.of(soldier), List.of(soldier))));
    }

    @Test
    void atNightASquadSealedInIsKeptInNotOrderedOut() {
        chain.setGround((x, z, nearY) -> OptionalInt.of(64));
        chain.onTime(TimeOfDay.of(18000, true));
        SquadStatus sealed = new SquadStatus("Alpha", new Report.InPosition(),
                List.of(new SoldierStatus("Alice", new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), true);

        Objective given = chain.sheltered(WALL, sealed);

        assertTrue(given.patrolBase(), given.toString());
        assertEquals(new Position(0, 64, 0), given.location());
    }

    @Test
    void atNightEverySquadGoesDownTheCampsMineAndComesUpBeforeSunrise() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.setGround((x, z, nearY) -> OptionalInt.of(64));
        two.onTime(TimeOfDay.of(18000, true));
        two.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));

        Objective alpha = two.sheltered(WALL, squad("Alpha", "Alice"));
        Objective bravo = two.sheltered(WALL, squad("Bravo", "Bob"));

        assertTrue(alpha.mine(), alpha.toString());
        assertTrue(bravo.mine(), bravo.toString());
        assertEquals(new Position(-44, ChainOfCommand.MINE_DEPTH, 40), bravo.location());
        assertEquals(bravo, two.sheltered(bravo, squad("Bravo", "Bob")), "a squad down the mine stays there");
        Objective hut = new Objective("Build a patrol base", new Position(0, 64, 0), "Safe", new Objective.Task.PatrolBase());
        assertEquals(bravo, two.sheltered(hut, squad("Bravo", "Bob")), "the platoon leader's patrol base doesn't take it off the night shift");

        two.setKeepByDay(true);
        assertEquals(ChainOfCommand.KEEP_MINE, two.sheltered(WALL, squad("Bravo", "Bob")).location(),
                "building the keep, the night's mine is the keep's own, beside it");
        two.onTime(TimeOfDay.of(TimeOfDay.DAWN - ChainOfCommand.SURFACE_LEAD_TICKS + 1, true));
        Objective climbingOut = two.sheltered(two.byDay(WALL, squad("Bravo", "Bob")), squad("Bravo", "Bob"));
        assertTrue(climbingOut.task() instanceof Objective.Task.Keep, "just before sunrise it climbs out to the day's work: " + climbingOut);
    }

    @Test
    void aDriedUpKeepMineMovesToTheNearestUntriedSiteClearOfTheFlatten() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice"), true, "model");
        Position first = ChainOfCommand.KEEP_MINE;
        assertEquals(Optional.empty(), two.onMineDriedUp(100, 100), "another mine's drying up leaves the keep's where it is");
        assertEquals(first, two.keepMine());

        Position next = two.onMineDriedUp((int) Math.floor(first.x()), (int) Math.floor(first.z())).orElseThrow();
        assertEquals(0, Math.floorMod((int) (next.x() - first.x()), ChainOfCommand.MINE_SITE_STEP), "on the grid round the first");
        assertEquals(0, Math.floorMod((int) (next.z() - first.z()), ChainOfCommand.MINE_SITE_STEP), "on the grid round the first");
        assertEquals(next, two.keepMine());
        assertTrue(two.minesTried().contains(first), "the first is tried");
        Position after = two.onMineDriedUp((int) Math.floor(next.x()), (int) Math.floor(next.z())).orElseThrow();
        assertFalse(after.equals(first) || after.equals(next), "never back to a site tried: " + after);
        double centre = Math.hypot(next.x() - two.flatten().centre().x(), next.z() - two.flatten().centre().z());
        assertTrue(centre <= Math.hypot(after.x() - two.flatten().centre().x(), after.z() - two.flatten().centre().z()),
                "nearest the keep first");
        next = after;
        for (int dx = -ChainOfCommand.MINE_SITE_CLEAR; dx <= ChainOfCommand.MINE_SITE_CLEAR; dx++) {
            assertFalse(two.flatten().benchedContains((int) next.x() + dx, (int) next.z()), "clear of the flatten site and its bench");
        }
        assertEquals(Optional.empty(), two.onMineDriedUp((int) Math.floor(first.x()), (int) Math.floor(first.z())),
                "the old site's word, said again, moves it no further");
    }

    private static SquadStatus squad(String name, String soldier) {
        return new SquadStatus(name, new Report.InPosition(),
                List.of(new SoldierStatus(soldier, new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.empty(), false);
    }
}
