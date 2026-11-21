package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
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
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat eval matrix's several-at-once cells (backlog/scenario-matrix.md: several hostiles at once): one soldier
 * against two hostiles together, by the pair, the soldier's kit and the ground. Correct: alive at the end. Live
 * (field595), Olga fought a skeleton 0.8 off with a creeper 1.3 off, the blast took her to 2 health, and a second
 * creeper killed her. A cell passes at {@link CombatMatrixTest#PASS} of its seeds; one that doesn't yet is in
 * {@link #KNOWN}, reported, not failed - and failed once it passes, so it comes off the list with the fix.
 */
class CombatPairsMatrixTest {

    private static final int SEEDS = Integer.getInteger("combat.matrix.seeds", 12);
    private static final int TICKS = 1_200;

    static final List<List<Battle.Archetype>> PAIRS = List.of(
            List.of(Battle.Archetype.CREEPER, Battle.Archetype.SKELETON),
            List.of(Battle.Archetype.CREEPER, Battle.Archetype.ZOMBIE),
            List.of(Battle.Archetype.ZOMBIE, Battle.Archetype.ZOMBIE),
            List.of(Battle.Archetype.ZOMBIE, Battle.Archetype.SKELETON));

    static final List<CombatMatrixTest.KitCase> KITS = List.of(CombatMatrixTest.KITS.get(0), CombatMatrixTest.KITS.get(2),
            CombatMatrixTest.KITS.get(4));

    static final List<Terrain> GROUNDS = List.of(Terrain.FLAT, Terrain.HILLS);

    /** Cells that don't pass yet, as "pair/kit/ground". */
    static final Set<String> KNOWN = Set.of(
            "creeper+zombie/bare hands/hills",
            "zombie+skeleton/stone sword/hills");

    /** Whether the soldier came through alive, against {@code pair} with {@code kit} on {@code ground}. */
    static boolean survives(List<Battle.Archetype> pair, long seed, CombatMatrixTest.KitCase kit, Terrain ground) {
        Scenario scenario = Scenario.generate(ground, seed);
        scenario.world().open(CombatScenarioTest.OPEN); // no wall at 48 to corner a flight
        Battle battle = new Battle(scenario.world(), seed);
        BlockPos start = new BlockPos(0, scenario.world().ground(0, 0) + 1, 0);
        scenario.world().clearAbove(0, 0);
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2;
        List<Battle.Hostile> hostiles = new ArrayList<>();
        for (int i = 0; i < pair.size(); i++) {
            // Together, a few blocks apart round one bearing: as a pair comes on.
            double a = angle + (i - 0.5) * 0.35, range = 10 + random.nextDouble() * 4;
            hostiles.add(battle.spawn(pair.get(i), start.x() + 0.5 + Math.cos(a) * range, start.z() + 0.5 + Math.sin(a) * range));
        }
        DeterministicSoldier soldier = new DeterministicSoldier("Alice", new Position(start.x() + 0.5, start.y(), start.z() + 0.5));
        double health = 20;
        Body body = new Body(kit.armour()); // the server's food, regeneration and armour wear
        soldier.reportHealth(health, 20);
        soldier.reportKit(new Kit(kit.weapon(), 0, kit.armour().pieces, 0, 0, 0, 0, kit.shield()));
        double blow = WeaponStats.melee(kit.weapon()).damage();
        BlockPos objective = scenario.objective();
        soldier.receiveOrder(new Order.MoveTo(objective.x() + 0.5, objective.y(), objective.z() + 0.5, 1.5, 1, Risk.LOW));
        int shieldUp = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            OptionalInt attack = soldier.tick(battle);
            Position now = soldier.status().position();
            attack.ifPresent(id -> {
                battle.strike(id, now, DeterministicSoldier.ATTACK_REACH, blow);
                body.attacked();
            });
            shieldUp = soldier.shieldRaised() ? shieldUp + 1 : 0;
            double damage = body.hit(battle.step(now, shieldUp > Battle.SHIELD_DELAY_TICKS
                    ? OptionalDouble.of(soldier.yaw()) : OptionalDouble.empty()));
            boolean healed = body.tick();
            health = body.health();
            if (damage > 0 || healed) {
                soldier.reportHealth(Math.max(0, health), 20);
            }
            if (health <= 0) {
                return false;
            }
            if (hostiles.stream().noneMatch(Battle.Hostile::alive)) {
                return true;
            }
        }
        return true;
    }

    @Test
    void everyPairAgainstWhatCorrectIs() {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%nCombat pairs, %d seeds a cell - the share alive:%n  %-18s %-13s", SEEDS, "pair", "kit"));
        GROUNDS.forEach(g -> table.append(String.format(Locale.ROOT, " %7s", g.name().toLowerCase(Locale.ROOT))));
        table.append('\n');
        List<String> failing = new ArrayList<>();
        List<String> fixed = new ArrayList<>();
        for (List<Battle.Archetype> pair : PAIRS) {
            String name = pair.stream().map(a -> a.name().toLowerCase(Locale.ROOT)).reduce((a, b) -> a + "+" + b).orElseThrow();
            for (CombatMatrixTest.KitCase kit : KITS) {
                table.append(String.format(Locale.ROOT, "  %-18s %-13s", name, kit.name()));
                for (Terrain ground : GROUNDS) {
                    int right = 0;
                    for (long seed = 1; seed <= SEEDS; seed++) {
                        right += survives(pair, seed, kit, ground) ? 1 : 0;
                    }
                    double share = (double) right / SEEDS;
                    String cell = name + "/" + kit.name() + "/" + ground.name().toLowerCase(Locale.ROOT);
                    boolean known = KNOWN.contains(cell);
                    table.append(String.format(Locale.ROOT, " %6.0f%%%s", 100 * share, share >= CombatMatrixTest.PASS ? " " : known ? "k" : "!"));
                    if (share < CombatMatrixTest.PASS && !known) {
                        failing.add(cell + " " + Math.round(100 * share) + "%");
                    } else if (share >= CombatMatrixTest.PASS && known) {
                        fixed.add(cell);
                    }
                }
                table.append('\n');
            }
        }
        table.append("  (! under ").append(Math.round(100 * CombatMatrixTest.PASS)).append("%, k known)\n");
        System.out.println(table);
        assertTrue(fixed.isEmpty(), "cells passing now - take them off KNOWN: " + fixed);
        assertTrue(failing.isEmpty(), "cells under " + Math.round(100 * CombatMatrixTest.PASS) + "%: " + failing);
    }
}
