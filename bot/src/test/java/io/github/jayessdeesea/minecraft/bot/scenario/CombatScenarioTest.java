package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Report;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import io.github.jayessdeesea.minecraft.command.ReactToContact;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * One soldier on its way somewhere, met by one simulated hostile of each archetype ({@link Battle}),
 * across many seeds. Checks the contact reflex's contract in every variant - a hostile within
 * {@link DeterministicSoldier#CONTACT_RADIUS} means the soldier reports InContact - and prints how
 * each fight ends, so a change to the reflex shows up as a change in outcomes.
 *
 * <p>{@code -Dcombat.seeds=200} for a wider sweep.
 */
class CombatScenarioTest {

    private static final int DEFAULT_SEEDS = 40;
    private static final int TICKS = 1_200;
    private static final double BROKE_CONTACT = 16;
    /** Food 20: vanilla regenerates roughly 1 health every 80 ticks when well fed. */
    private static final int REGEN_TICKS = 80;

    enum Ending { DIED, KILLED_IT, SURVIVED_BLAST, BROKE_CONTACT, STILL_ENGAGED }

    record Fight(Battle.Archetype archetype, long seed, Ending ending, double healthLeft) {
    }

    @Test
    void contactReflexAgainstEachArchetype() {
        int seeds = Integer.getInteger("combat.seeds", DEFAULT_SEEDS);
        Map<Battle.Archetype, Map<Ending, Integer>> tally = new EnumMap<>(Battle.Archetype.class);
        for (Battle.Archetype archetype : Battle.Archetype.values()) {
            Map<Ending, Integer> endings = new EnumMap<>(Ending.class);
            for (long seed = 1; seed <= seeds; seed++) {
                endings.merge(fight(archetype, seed).ending(), 1, Integer::sum);
            }
            tally.put(archetype, endings);
        }
        StringBuilder table = new StringBuilder("\nOne soldier vs one hostile, " + seeds + " seeds each:\n");
        tally.forEach((archetype, endings) -> table.append(String.format(Locale.ROOT, "  %-9s %s%n", archetype, endings)));
        System.out.println(table);
    }

    /** A squad fight's result: how many soldiers died, and whether every hostile did. */
    record SquadFight(int deaths, boolean won) {
    }

    /**
     * Three zombies jump the flank of a squad spread in a line at a 10-block interval - too far
     * apart for the others' reflexes to notice - with and without the react-to-contact drill, and a
     * lone soldier against the same three for reference.
     */
    @Test
    void reactToContactAgainstThreeZombies() {
        int seeds = Integer.getInteger("combat.seeds", DEFAULT_SEEDS);
        StringBuilder table = new StringBuilder("\nThree zombies on one soldier's flank, " + seeds + " seeds each:\n");
        for (String setup : new String[]{"alone", "squad, no support", "squad, react to contact"}) {
            int deaths = 0;
            int wins = 0;
            for (long seed = 1; seed <= seeds; seed++) {
                SquadFight fight = squadFight(seed, setup.equals("alone") ? 1 : 3, setup.endsWith("contact"));
                deaths += fight.deaths();
                wins += fight.won() ? 1 : 0;
            }
            table.append(String.format(Locale.ROOT, "  %-24s %3d wins, %3d soldier deaths%n", setup, wins, deaths));
        }
        System.out.println(table);
    }

    static SquadFight squadFight(long seed, int soldiers, boolean support) {
        SyntheticWorld world = Scenario.generate(Terrain.FLAT, seed).world();
        Battle battle = new Battle(world, seed);
        Random random = new Random(seed);
        List<DeterministicSoldier> squad = new ArrayList<>();
        List<Order> slots = new ArrayList<>();
        double[] health = new double[soldiers];
        for (int i = 0; i < soldiers; i++) {
            int x = i * 10 - 10;
            world.clearAbove(x, 20);
            DeterministicSoldier soldier = new DeterministicSoldier("S" + i, new Position(x + 0.5, world.ground(x, 20) + 1, 20.5));
            soldier.reportHealth(20, 20);
            squad.add(soldier);
            slots.add(new Order.MoveTo(x + 0.5, Scenario.BASE_GROUND + 1, -40.5, 3, 3, Risk.LOW));
            soldier.receiveOrder(slots.get(i));
            health[i] = 20;
        }
        // The pack comes in on the west flank, just inside the first soldier's contact radius.
        double packX = -10.5 - 6 - random.nextDouble() * 1.5;
        double packZ = 20.5 - 2 + random.nextDouble() * 4;
        for (int j = 0; j < 3; j++) {
            battle.spawn(Battle.Archetype.ZOMBIE, packX - j * 1.2, packZ + (j - 1) * 1.2);
        }

        int deaths = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            List<Integer> alive = new ArrayList<>();
            for (int i = 0; i < soldiers; i++) {
                if (health[i] > 0) {
                    alive.add(i);
                }
            }
            if (alive.isEmpty() || battle.hostiles().stream().noneMatch(Battle.Hostile::alive)) {
                break;
            }
            if (support && tick % 20 == 0) {
                List<SoldierStatus> statuses = alive.stream().map(i -> squad.get(i).status()).toList();
                List<Order> base = alive.stream().map(slots::get).toList();
                List<Order> orders = ReactToContact.orders(statuses, base);
                for (int k = 0; k < alive.size(); k++) {
                    squad.get(alive.get(k)).receiveOrder(orders.get(k));
                }
            }
            List<Position> positions = new ArrayList<>();
            for (int i : alive) {
                DeterministicSoldier soldier = squad.get(i);
                OptionalInt attack = soldier.tick(battle);
                Position now = soldier.status().position();
                attack.ifPresent(id -> battle.strike(id, now, DeterministicSoldier.ATTACK_REACH));
                positions.add(now);
            }
            double[] damage = battle.step(positions);
            for (int k = 0; k < alive.size(); k++) {
                int i = alive.get(k);
                double healed = tick % REGEN_TICKS == 0 && health[i] < 20 ? 1 : 0;
                if (damage[k] > 0 || healed > 0) {
                    health[i] = Math.min(20, health[i] - damage[k] + healed);
                    squad.get(i).reportHealth(Math.max(0, health[i]), 20);
                    if (health[i] <= 0) {
                        deaths++;
                    }
                }
            }
        }
        return new SquadFight(deaths, battle.hostiles().stream().noneMatch(Battle.Hostile::alive));
    }

    static Fight fight(Battle.Archetype archetype, long seed) {
        Scenario scenario = Scenario.generate(Terrain.FLAT, seed);
        Battle battle = new Battle(scenario.world(), seed);
        // Start mid-map: the synthetic world is a closed box, and a retreat that backs into its
        // edge is cornered in a way the open live world wouldn't corner it.
        BlockPos start = new BlockPos(0, scenario.world().ground(0, 0) + 1, 0);
        scenario.world().clearAbove(0, 0);
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2;
        double range = 10 + random.nextDouble() * 4;
        Battle.Hostile hostile = battle.spawn(archetype, start.x() + 0.5 + Math.cos(angle) * range,
                start.z() + 0.5 + Math.sin(angle) * range);

        DeterministicSoldier soldier = new DeterministicSoldier("Alice", new Position(start.x() + 0.5, start.y(), start.z() + 0.5));
        double health = 20;
        soldier.reportHealth(health, 20);
        BlockPos objective = scenario.objective();
        soldier.receiveOrder(new Order.MoveTo(objective.x() + 0.5, objective.y(), objective.z() + 0.5, 1.5, 1, Risk.LOW));

        for (int tick = 0; tick < TICKS; tick++) {
            Position before = soldier.status().position();
            boolean threatened = !battle.nearbyHostiles(before, DeterministicSoldier.CONTACT_RADIUS).isEmpty();
            OptionalInt attack = soldier.tick(battle);
            if (threatened && !(soldier.status().report() instanceof Report.InContact)) {
                fail(archetype + " seed=" + seed + " tick " + tick + ": hostile within contact radius but reported "
                        + soldier.status().report());
            }
            Position now = soldier.status().position();
            attack.ifPresent(id -> battle.strike(id, now, DeterministicSoldier.ATTACK_REACH));
            double damage = battle.step(now);
            double healed = tick % REGEN_TICKS == 0 && health < 20 ? 1 : 0;
            if (damage > 0 || healed > 0) {
                health = Math.min(20, health - damage + healed);
                soldier.reportHealth(Math.max(0, health), 20);
            }
            if (health <= 0) {
                return new Fight(archetype, seed, Ending.DIED, 0);
            }
            if (!hostile.alive()) {
                return new Fight(archetype, seed, hostile.exploded ? Ending.SURVIVED_BLAST : Ending.KILLED_IT, health);
            }
        }
        Position end = soldier.status().position();
        double apart = Math.hypot(end.x() - hostile.x, end.z() - hostile.z);
        return new Fight(archetype, seed, apart > BROKE_CONTACT ? Ending.BROKE_CONTACT : Ending.STILL_ENGAGED, health);
    }
}
