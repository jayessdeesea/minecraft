package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.Foothold;
import io.github.jayessdeesea.minecraft.command.PlatoonLeader;
import io.github.jayessdeesea.minecraft.command.Cohesion;
import io.github.jayessdeesea.minecraft.command.Objective;
import io.github.jayessdeesea.minecraft.command.Phase;
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
import io.github.jayessdeesea.minecraft.command.Stockpile;
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
        chain.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
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
        two.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
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
        chain.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
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
        chain.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
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
     * The groves short of trees with nobody planting, past the SEIZURE: a squad spare of the shortfalls is put on
     * TREE_FARM (operator, 2026-11-20: grow more trees). Live (field565), two squads on the farm, 36 of 48 planted.
     */
    @Test
    void theGrovesShortOfTreesGetASquadPlanting() {
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.restorePhase(Phase.CONSOLIDATION, 0);
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        Position bareGrove = new Position(40.5, 64, 100.5);
        three.onFoothold(new Foothold(new Position(24, 64, 89), List.of(new Foothold.Standard(3, "wood that grows back", false,
                "36 of 48 saplings or trees planted by the camp and the keep's grove", "TREE_FARM", Optional.of(bareGrove)))));
        Objective farm = new Objective("Farm", new Position(-4, 64, 93), "Food", new Objective.Task.Farm());
        java.util.function.Function<String, SquadStatus> farming = name -> new SquadStatus(name, new Report.Farming(),
                squad(name, name.substring(0, 1) + "x").soldiers(), List.of(), Optional.of(farm), false);
        List<SquadStatus> squads = List.of(farming.apply("Alpha"), farming.apply("Bravo"), farming.apply("Charlie"));

        List<Objective> guarded = three.guarded(List.of(farm, farm, farm), squads, List.of(), List.of());

        assertEquals(1, guarded.stream().filter(o -> o.task() instanceof Objective.Task.TreeFarm).count(), "one squad planting: " + guarded);
        assertEquals(bareGrove, guarded.stream().filter(o -> o.task() instanceof Objective.Task.TreeFarm).findFirst().orElseThrow().location(),
                "at the grove still bare, not the camp's centre - live, dug, \"no open dirt or grass\" there");
    }

    /** The platoon leader's FORAGE for squads standing on FARM stands: food work, not put back on the farm. */
    @Test
    void aForageOrderForASquadOnTheFarmStands() {
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.restorePhase(Phase.SEIZURE, 0); // no flatten dealt: the food rules alone
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(24, 64, 89), List.of()));
        Objective farm = new Objective("Farm", new Position(-4, 64, 93), "Food", new Objective.Task.Farm());
        Objective forage = new Objective("Forage", new Position(24, 64, 89), "Rations off the land", new Objective.Task.Forage());
        java.util.function.Function<String, SquadStatus> farming = name -> new SquadStatus(name, new Report.Farming(),
                hungry(name, name.substring(0, 1)).soldiers(), List.of(), Optional.of(farm), false);
        List<SquadStatus> squads = List.of(farming.apply("Alpha"), farming.apply("Bravo"), farming.apply("Charlie"));

        List<Objective> guarded = three.guarded(List.of(farm, forage, forage), squads, List.of(PlatoonSergeant.Shortfall.FOOD), List.of());

        assertEquals(2, guarded.stream().filter(o -> o.task() instanceof Objective.Task.Forage).count(), "the foragers stand: " + guarded);
    }

    /**
     * A starving squad the platoon leader sent to FORAGE stays on it: not spare to the wall's or the wood's guardrails.
     * Live (field513), hungry Charlie's FORAGE was taken for the wall, then the wood.
     */
    @Test
    void aStarvingSquadOnItsForageIsNotTakenForTheWall() {
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.restorePhase(Phase.SEIZURE, 0);
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(24, 64, 89), List.of(new Foothold.Standard(2, "the camp's ground lit", false, "31 cells dark", "SECURE"))));
        Objective farm = new Objective("Farm", new Position(-4, 64, 93), "Food", new Objective.Task.Farm());
        Objective forage = new Objective("Forage", new Position(24, 64, 89), "Rations off the land", new Objective.Task.Forage());
        Objective wall = new Objective("Wall the camp in", new Position(24, 64, 89), "Safety", new Objective.Task.Fortify());
        List<SquadStatus> squads = List.of(hungry("Alpha", "Alice"), hungry("Bravo", "Bob"), hungry("Charlie", "Carol"));

        List<Objective> guarded = three.guarded(List.of(farm, forage, wall), squads, List.of(PlatoonSergeant.Shortfall.FOOD), List.of());

        assertEquals(forage, guarded.get(1), "Bravo forages, its hunger its own: " + guarded);
    }

    /**
     * In the seizure, a camp unwalled takes nobody off the food: the wall is no standard of the camp's (operator,
     * 2026-11-20) - its masons' pits and lips littered the camp, and it kept off no killer the night's huts and the
     * lights don't (field498-515: phantoms, friendly fire, drowning).
     */
    @Test
    void inTheSeizureAnUnwalledCampTakesNoSquadOffItsFood() {
        // The camp lit: only its wall open, and the wall no standard.
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.restorePhase(Phase.SEIZURE, 0);
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(24, 64, 89), List.of(new Foothold.Standard(2, "the camp's ground lit", true, "", "SECURE"))));
        Objective farm = new Objective("Farm", new Position(-4, 64, 93), "Food", new Objective.Task.Farm());
        List<SquadStatus> squads = List.of(hungry("Alpha", "Alice"), hungry("Bravo", "Bob"), hungry("Charlie", "Carol"));

        assertEquals(List.of(farm, farm, farm), three.guarded(List.of(farm, farm, farm), squads, List.of(PlatoonSergeant.Shortfall.FOOD), List.of()));
    }

    /**
     * In the seizure, the camp unlit and every squad on the food: one doubled up on the farm is put on SECURE - the
     * standard the seizure ends on. Live (field517), every squad farmed all day and 52 dark cells stood.
     */
    @Test
    void inTheSeizureAnUnlitCampHasASpareSquadPutOnItsLights() {
        ChainOfCommand three = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob;Charlie:Carol"), true, "model");
        three.setKeepByDay(true);
        three.restorePhase(Phase.SEIZURE, 0);
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(24, 64, 89), List.of(new Foothold.Standard(2, "the camp's ground lit", false, "52 cells dark", "SECURE"))));
        Objective farm = new Objective("Farm", new Position(-4, 64, 93), "Food", new Objective.Task.Farm());
        List<PlatoonSergeant.Shortfall> alarms = List.of(PlatoonSergeant.Shortfall.FOOD);
        List<SquadStatus> squads = List.of(hungry("Alpha", "Alice"), hungry("Bravo", "Bob"), hungry("Charlie", "Carol"));

        List<Objective> guarded = three.guarded(List.of(farm, farm, farm), squads, alarms, List.of());

        assertEquals(1, guarded.stream().filter(o -> o.task() instanceof Objective.Task.Secure).count(), "one lights the camp: " + guarded);
        assertEquals(new Position(24, 64, 89), guarded.stream().filter(o -> o.task() instanceof Objective.Task.Secure).findFirst().orElseThrow().location());
        three.onFoothold(new Foothold(new Position(24, 64, 89), List.of(new Foothold.Standard(2, "the camp's ground lit", true, "", "SECURE"))));
        assertEquals(List.of(farm, farm, farm), three.guarded(List.of(farm, farm, farm), squads, alarms, List.of()), "lit: the farms stand");
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
        three.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));
        Objective farm = new Objective("Farm", new Position(-48, 64, 44), "Food", new Objective.Task.Farm());
        List<PlatoonSergeant.Shortfall> alarms = List.of(PlatoonSergeant.Shortfall.FOOD, PlatoonSergeant.Shortfall.WOOD);
        List<ResourceSite> woods = List.of(new ResourceSite(Resource.WOOD, new Position(30, 64, 30), 10));

        List<SquadStatus> squads = List.of(hungry("Alpha", "Alice"), hungry("Bravo", "Bob"), hungry("Charlie", "Carol"));
        // The platoon leader sends all three to the farm: its orders stand, and the guardrails put one on the flatten
        // and one on the wood nobody was sent to - one flattens, one farms, one cuts (operator, 2026-11-18: the
        // flatten goes on in foothold, the food and wood each covered).
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
     * Short of wood, one squad flattens and the rest go to what's short, however wide the level (operator,
     * 2026-11-17); short of food, nobody flattens (2026-11-18: food and arms first); short of nothing, the
     * level's sizing stands.
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
        three.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
        three.setGround((x, z, nearY) -> OptionalInt.of(64));
        three.onTime(TimeOfDay.of(1000, true));
        three.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));
        three.onFlattenCrew(40);
        List<PlatoonSergeant.Shortfall> food = List.of(PlatoonSergeant.Shortfall.FOOD);
        List<PlatoonSergeant.Shortfall> wood = List.of(PlatoonSergeant.Shortfall.WOOD);
        List<ResourceSite> trees = List.of(new ResourceSite(Resource.WOOD, new Position(30, 64, 30), 10));

        assertTrue(three.byDay(WALL, squad("Alpha", "Alice"), wood, trees).task() instanceof Objective.Task.Keep, "short of wood, the first flattens");
        assertTrue(three.byDay(WALL, squad("Bravo", "Bob"), wood, trees).task().equals(new Objective.Task.Gather(Resource.WOOD)), "the second cuts");
        assertTrue(three.byDay(WALL, squad("Alpha", "Alice"), food, List.of()).task() instanceof Objective.Task.Keep, "short of food, the first flattens");
        assertTrue(three.byDay(WALL, squad("Bravo", "Bob"), food, List.of()).task() instanceof Objective.Task.Farm, "and the second farms");
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
        two.restorePhase(Phase.BUILDUP, 0); // the buildup: the night shift mines

        Objective alpha = two.sheltered(WALL, squad("Alpha", "Alice"));
        Objective bravo = two.sheltered(WALL, squad("Bravo", "Bob"));

        assertTrue(alpha.mine(), alpha.toString());
        assertTrue(bravo.mine(), bravo.toString());
        assertEquals(new Position(-44, ChainOfCommand.MINE_DEPTH, 40), bravo.location());
        assertEquals(bravo, two.sheltered(bravo, squad("Bravo", "Bob")), "a squad down the mine stays there");
        Objective hut = new Objective("Build a patrol base", new Position(0, 64, 0), "Safe", new Objective.Task.PatrolBase());
        assertEquals(bravo, two.sheltered(hut, squad("Bravo", "Bob")), "the platoon leader's patrol base doesn't take it off the night shift");

        two.setKeepByDay(true);

        two.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
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

    /** At dusk or dawn the platoon leader is shown no standing order to keep: the last phase's work lapses. */
    @Test
    void atADuskOrDawnWakeNoStandingOrderIsOfferedToKeep() {
        Objective night = new Objective("Cut the keep's mine down to the iron through the night", new Position(0, 64, 0), "ore",
                new Objective.Task.Mine());
        SquadStatus alpha = new SquadStatus("Alpha", new Report.InPosition(), List.of(), List.of(), Optional.of(night), false,
                java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(Optional.empty(), DayWorkRules.forThePlatoonLeader(List.of(alpha), true).getFirst().standing());
        assertEquals(Optional.of(night), DayWorkRules.forThePlatoonLeader(List.of(alpha), false).getFirst().standing());
    }

    @Test
    void buildersWantedBeforeTheFirstPlanSaysSo() {
        assertEquals("its level not planned yet", DayWorkRules.buildersWanted(Integer.MAX_VALUE));
        assertEquals("its level wants 40 builder(s)", DayWorkRules.buildersWanted(40));
    }

    /** The flatten's standing squad, sent by the platoon leader to a shortfall, keeps that order: another flattens. */
    @Test
    void theFlattenTakesASquadNotOnAShortfallBeforeItsStandingOne() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.setKeepByDay(true);
        two.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
        two.setGround((x, z, nearY) -> OptionalInt.of(64));
        two.onTime(TimeOfDay.of(1000, true));
        Objective keep = new Objective("Flatten", new Position(0, 64, 0), "The ground", new Objective.Task.Keep());
        Objective wood = new Objective("Gather wood", new Position(30, 64, 30), "Logs", new Objective.Task.Gather(Resource.WOOD));
        Objective mine = new Objective("Mine", new Position(40, 64, 0), "Ore", new Objective.Task.Mine());
        List<SquadStatus> squads = List.of(
                new SquadStatus("Alpha", new Report.InPosition(), squad("Alpha", "Alice").soldiers(), List.of(), Optional.of(keep), false,
                        OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                new SquadStatus("Bravo", new Report.InPosition(), squad("Bravo", "Bob").soldiers(), List.of(), Optional.of(mine), false,
                        OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        List<Objective> guarded = two.guarded(List.of(wood, mine), squads, List.of(PlatoonSergeant.Shortfall.WOOD),
                List.of(new ResourceSite(Resource.WOOD, new Position(30, 64, 30), 10)));

        assertEquals(wood, guarded.get(0), "Alpha keeps the wood it was sent to: " + guarded);
        assertTrue(guarded.get(1).task() instanceof Objective.Task.Keep, "Bravo flattens: " + guarded);
    }

    /**
     * A squad farming stays on the farm while the stockpile's bread is under its most, though the platoon leader
     * sends it elsewhere; at its most, the platoon leader's order stands (operator, 2026-11-18: in between, a squad
     * on it stays on it).
     */
    @Test
    void aSquadProducingStaysOnItToTheStocksMost() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.setKeepByDay(true);
        two.restorePhase(Phase.BUILDUP, 0); // the buildup: the mission and the night shift
        two.setGround((x, z, nearY) -> OptionalInt.of(64));
        two.onTime(TimeOfDay.of(1000, true));
        Objective farm = new Objective("Farm", new Position(-48, 64, 44), "Food", new Objective.Task.Farm());
        Objective mine = new Objective("Mine", new Position(10, 20, 10), "Iron", new Objective.Task.Mine());
        List<SquadStatus> squads = List.of(standing("Alpha", "Alice", farm), standing("Bravo", "Bob", WALL));

        two.onStores(new Stockpile(100, 0, 100, 50, 0, 0, 0, 0));
        assertEquals(farm, two.guarded(List.of(mine, WALL), squads, List.of(), List.of()).getFirst(), "100 bread, under 1024: still farming");
        two.onStores(new Stockpile(100, 0, 1024, 50, 0, 0, 0, 0));
        assertEquals(mine, two.guarded(List.of(mine, WALL), squads, List.of(), List.of()).getFirst(), "at its most: the platoon leader's order stands");
    }

    private static SquadStatus standing(String squad, String soldier, Objective objective) {
        return new SquadStatus(squad, new Report.InPosition(),
                List.of(new SoldierStatus(soldier, new Position(0.5, 64, 0.5), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.of(objective), false, OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** In the consolidation nobody goes down the mine by night: the squad shelters in place in the camp. */
    @Test
    void inTheConsolidationNobodyMinesAtNight() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.setGround((x, z, nearY) -> OptionalInt.of(64));
        two.onTime(TimeOfDay.of(18000, true));
        two.onFoothold(new Foothold(new Position(-48, 64, 44), List.of()));
        two.restorePhase(Phase.CONSOLIDATION, 0);

        Objective night = two.sheltered(WALL, squad("Alpha", "Alice"));
        assertTrue(Cohesion.distance(new Position(-48, 64, 44), night.location()) <= ChainOfCommand.SPREAD * Math.sqrt(2) + 1e-9,
                "in the camp, at its own point round its centre: " + night);
        assertTrue(night.task() instanceof Objective.Task.PatrolBase, "sheltered in place under a roof, not mining: " + night);
    }

    /**
     * A squad producing is kept on it to the stock's most - but not once its task is done: live (field478), Charlie held
     * a wood every strip of which was cut out.
     */
    @Test
    void aSquadIsKeptOnItsProductionUntilItsTaskIsDone() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.setKeepByDay(true);
        two.restorePhase(Phase.BUILDUP, 0);
        two.onTime(TimeOfDay.of(1000, true));
        two.onStores(new io.github.jayessdeesea.minecraft.command.Stockpile(0, 0, 0, 0, 0, 0, 0, 0));
        Objective wood = new Objective("Gather wood", new Position(-26, 65, 78), "logs", new Objective.Task.Gather(Resource.WOOD));
        Objective keep = new Objective("Flatten", new Position(0, 64, 0), "flat", new Objective.Task.Keep());
        java.util.function.Function<io.github.jayessdeesea.minecraft.command.Progress.State, SquadStatus> alpha = state -> new SquadStatus("Alpha",
                new Report.InPosition(), List.of(new SoldierStatus("Alice", new Position(-26, 65, 78), new Order.Idle(), new Report.InPosition(), Optional.empty())),
                List.of(), Optional.of(wood), false, OptionalInt.empty(), Optional.of(new io.github.jayessdeesea.minecraft.command.Progress(state, "")));
        SquadStatus bravo = new SquadStatus("Bravo", new Report.InPosition(),
                List.of(new SoldierStatus("Bob", new Position(0, 64, 0), new Order.Idle(), new Report.InPosition(), Optional.empty())), List.of(),
                Optional.of(keep), false);

        assertEquals(wood, two.guarded(List.of(WALL, keep), List.of(alpha.apply(io.github.jayessdeesea.minecraft.command.Progress.State.UNDER_WAY), bravo),
                List.of(), List.of()).getFirst(), "under way: kept on the wood");
        assertEquals(WALL, two.guarded(List.of(WALL, keep), List.of(alpha.apply(io.github.jayessdeesea.minecraft.command.Progress.State.DONE), bravo),
                List.of(), List.of()).getFirst(), "the wood cut out: given its next task");
    }

    /**
     * Before the buildup each squad shelters in place in the camp at a point of its own round the camp's: live (field482),
     * all four on the camp's centre crowded each other 69 times a night.
     */
    @Test
    void beforeTheBuildupEachSquadSheltersInTheCampAtItsOwnPoint() {
        ChainOfCommand two = new ChainOfCommand(
                (PlatoonLeader) Proxy.newProxyInstance(PlatoonLeader.class.getClassLoader(), new Class<?>[]{PlatoonLeader.class}, (proxy, method, args) -> null),
                ChatClient.create((ChatModel) prompt -> {
                    throw new AssertionError("no model call expected");
                }), new SquadLeaderPrompts("", ""),
                new Roster("Alpha:Alice;Bravo:Bob"), true, "model");
        two.onFoothold(new io.github.jayessdeesea.minecraft.command.Foothold(new Position(-49, 75, 44), List.of()));
        two.onTime(TimeOfDay.of(18000, true));
        java.util.function.Function<String, SquadStatus> squad = name -> new SquadStatus(name, new Report.InPosition(),
                List.of(new SoldierStatus(name.equals("Alpha") ? "Alice" : "Bob", new Position(-40, 64, 40), new Order.Idle(), new Report.InPosition(),
                        Optional.empty())), List.of(), Optional.empty(), false);

        Objective alpha = two.sheltered(WALL, squad.apply("Alpha"));
        Objective bravo = two.sheltered(WALL, squad.apply("Bravo"));

        assertEquals("Shelter in place in the camp through the night", alpha.mission());
        assertTrue(Cohesion.distance(alpha.location(), bravo.location()) >= ChainOfCommand.SPREAD, alpha.location() + " and " + bravo.location());
        assertTrue(alpha.task() instanceof Objective.Task.PatrolBase, "under a roof, its hut: phantoms come to the sky (field498-507)");
    }
}
