package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.Strike;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat eval matrix's squad cells (operator, 2026-11-21: the sword's sweep has its place, and it's not always): a
 * squad of two or three holding its ground against a pack, by the pack, the kit and the ground. Correct: every hostile
 * killed, no soldier killed, and no squadmate hit by a sweep. Each cell also says how many hostiles its sweeps took - the
 * sweep's worth, where it's safe. Live (field584), four in one cell of a slot each held fire for the others, and a
 * zombie killed two. A cell passes at {@link CombatMatrixTest#PASS} of its seeds; one short of it is in {@link #KNOWN}.
 */
class CombatSquadMatrixTest {

    private static final int SEEDS = Integer.getInteger("combat.matrix.seeds", 12);
    private static final int TICKS = 1_200;

    static final List<List<Battle.Archetype>> PACKS = List.of(
            List.of(Battle.Archetype.ZOMBIE, Battle.Archetype.ZOMBIE),
            List.of(Battle.Archetype.ZOMBIE, Battle.Archetype.ZOMBIE, Battle.Archetype.ZOMBIE),
            List.of(Battle.Archetype.ZOMBIE, Battle.Archetype.SKELETON));
    static final List<Integer> SQUADS = List.of(2, 3);
    static final List<CombatMatrixTest.KitCase> KITS = List.of(CombatMatrixTest.KITS.get(2), CombatMatrixTest.KITS.get(4));
    static final List<Terrain> GROUNDS = List.of(Terrain.FLAT, Terrain.HILLS);

    /** Cells that don't pass yet, as "squad/pack/kit/ground". */
    static final Set<String> KNOWN = Set.of(
            "2/zombie+skeleton/stone sword/hills",
            "2/zombie+skeleton/sword, iron/hills");

    /** How a squad's fight came out: every hostile killed, a soldier killed, and the sweeps' damage to squadmates and hostiles. */
    record Fight(boolean allKilled, boolean soldierKilled, double friendly, int swept) {
        boolean correct() {
            return allKilled && !soldierKilled && friendly == 0;
        }
    }

    static Fight fight(int size, List<Battle.Archetype> pack, long seed, CombatMatrixTest.KitCase kit, Terrain ground) {
        Scenario scenario = Scenario.generate(ground, seed);
        scenario.world().open(CombatScenarioTest.OPEN);
        Battle battle = new Battle(scenario.world(), seed);
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2;
        List<Battle.Hostile> hostiles = new ArrayList<>();
        for (int i = 0; i < pack.size(); i++) {
            double a = angle + (i - (pack.size() - 1) / 2.0) * 0.35;
            double range = 10 + random.nextDouble() * 3;
            hostiles.add(battle.spawn(pack.get(i), 0.5 + Math.cos(a) * range, 0.5 + Math.sin(a) * range));
        }
        // The squad stood together, a block and a half apart, as a squad holds its ground.
        double[][] places = {{0.5, 0.5}, {2.0, 0.5}, {0.5, 2.0}};
        List<DeterministicSoldier> squad = new ArrayList<>();
        double[] health = new double[size];
        Body[] bodies = new Body[size]; // each the server's food, regeneration and armour wear
        int[] since = new int[size];
        Strike.Weapon weapon = Strike.Weapon.of(kit.weapon());
        double blow = WeaponStats.melee(kit.weapon()).damage();
        for (int i = 0; i < size; i++) {
            int x = (int) Math.floor(places[i][0]);
            int z = (int) Math.floor(places[i][1]);
            scenario.world().clearAbove(x, z);
            BlockPos at = new BlockPos(x, scenario.world().ground(x, z) + 1, z);
            DeterministicSoldier s = new DeterministicSoldier("S" + i, new Position(places[i][0], at.y(), places[i][1]));
            s.entityId(Battle.PLAYER_ID_FIRST + i); // itself known by its entity, as live - not by where it stands
            health[i] = 20;
            bodies[i] = new Body(kit.armour());
            s.reportHealth(20, 20);
            s.reportKit(new Kit(kit.weapon(), 0, kit.armour().pieces, 0, 0, 0, 0, kit.shield()));
            s.receiveOrder(new Order.MoveTo(places[i][0], at.y(), places[i][1], 1.5, 3.0, Risk.LOW));
            squad.add(s);
            since[i] = 100;
        }
        double friendly = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            List<Position> at = squad.stream().map(s -> s.status().position()).toList();
            battle.players(at);
            for (int i = 0; i < size; i++) {
                if (health[i] <= 0) {
                    continue;
                }
                DeterministicSoldier s = squad.get(i);
                OptionalInt attack = s.tick(battle);
                Strike.Kind kind = s.takeSwingKind();
                s.takeKnockbackHit();
                s.takeArmSwing();
                Position now = s.status().position();
                since[i]++;
                if (attack.isPresent() && !bodies[i].eating()) { // no swing while it eats
                    double[] hit = battle.swing(attack.getAsInt(), now, DeterministicSoldier.ATTACK_REACH, kind, weapon, blow, since[i], at);
                    bodies[i].attacked();
                    for (int j = 0; j < size; j++) {
                        if (hit[j] > 0) {
                            friendly += hit[j];
                            bodies[j].hit(hit[j]);
                            health[j] = bodies[j].health();
                        }
                    }
                    since[i] = 0;
                } else if (kind == Strike.Kind.RESET) {
                    since[i] = 0;
                }
            }
            List<Position> alive = new ArrayList<>();
            List<Integer> who = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (health[i] > 0) {
                    alive.add(squad.get(i).status().position());
                    who.add(i);
                }
            }
            if (alive.size() < size) {
                return new Fight(false, true, friendly, battle.sweptHostiles);
            }
            double[] damage = battle.step(alive);
            for (int k = 0; k < who.size(); k++) {
                int i = who.get(k);
                double taken = bodies[i].hit(damage[k]);
                boolean healed = bodies[i].tick();
                health[i] = bodies[i].health();
                if (taken > 0 || healed) {
                    squad.get(i).reportHealth(Math.max(0, health[i]), 20);
                }
                if (health[i] <= 0) {
                    return new Fight(false, true, friendly, battle.sweptHostiles);
                }
            }
            if (hostiles.stream().noneMatch(Battle.Hostile::alive)) {
                return new Fight(true, false, friendly, battle.sweptHostiles);
            }
        }
        return new Fight(false, false, friendly, battle.sweptHostiles);
    }

    @Test
    void everySquadAgainstWhatCorrectIs() {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "%nCombat squads, %d seeds a cell - the share right (hostiles swept, squadmates' sweep damage):%n  %-5s %-24s %-13s",
                SEEDS, "squad", "pack", "kit"));
        GROUNDS.forEach(g -> table.append(String.format(Locale.ROOT, " %20s", g.name().toLowerCase(Locale.ROOT))));
        table.append('\n');
        List<String> failing = new ArrayList<>();
        List<String> fixed = new ArrayList<>();
        List<String> sweptSquadmates = new ArrayList<>();
        for (int size : SQUADS) {
            for (List<Battle.Archetype> pack : PACKS) {
                String name = pack.stream().map(a -> a.name().toLowerCase(Locale.ROOT)).reduce((a, b) -> a + "+" + b).orElseThrow();
                for (CombatMatrixTest.KitCase kit : KITS) {
                    table.append(String.format(Locale.ROOT, "  %-5d %-24s %-13s", size, name, kit.name()));
                    for (Terrain ground : GROUNDS) {
                        int right = 0;
                        int swept = 0;
                        double friendly = 0;
                        for (long seed = 1; seed <= SEEDS; seed++) {
                            Fight f = fight(size, pack, seed, kit, ground);
                            right += f.correct() ? 1 : 0;
                            swept += f.swept();
                            friendly += f.friendly();
                        }
                        double share = (double) right / SEEDS;
                        String cell = size + "/" + name + "/" + kit.name() + "/" + ground.name().toLowerCase(Locale.ROOT);
                        if (friendly > 0) {
                            sweptSquadmates.add(cell + " " + friendly);
                        }
                        boolean known = KNOWN.contains(cell);
                        table.append(String.format(Locale.ROOT, " %4.0f%%%s (%3d, %4.0f)", 100 * share,
                                share >= CombatMatrixTest.PASS ? " " : known ? "k" : "!", swept, friendly));
                        if (share < CombatMatrixTest.PASS && !known) {
                            failing.add(cell + " " + Math.round(100 * share) + "%, squadmates' sweep damage " + friendly);
                        } else if (share >= CombatMatrixTest.PASS && known) {
                            fixed.add(cell);
                        }
                    }
                    table.append('\n');
                }
            }
        }
        table.append("  (! under ").append(Math.round(100 * CombatMatrixTest.PASS)).append("%, k known)\n");
        System.out.println(table);
        // A squadmate swept is never right, whatever share a cell gets: in a fight a seed in twelve is all it takes to
        // show - the sweep at full charge whoever is in its box swept squadmates in five cells, each still at 92%.
        assertTrue(sweptSquadmates.isEmpty(), "squadmates hit by a sweep, known cell or not: " + sweptSquadmates);
        assertTrue(fixed.isEmpty(), "cells passing now - take them off KNOWN: " + fixed);
        assertTrue(failing.isEmpty(), "cells under " + Math.round(100 * CombatMatrixTest.PASS) + "%: " + failing);
    }
}
