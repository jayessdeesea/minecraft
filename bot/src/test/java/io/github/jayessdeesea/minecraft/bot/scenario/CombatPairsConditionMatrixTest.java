package io.github.jayessdeesea.minecraft.bot.scenario;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pairs matrix grown by the soldier's condition (backlog/engagement-doctrine.md: several hostiles at once): a zombie
 * and a skeleton together, met healthy or hurt, in each kit, on flat, hills and a ravine. Live (field663), Eli met
 * them at y=6 unarmoured at 8 health and was shot dead, his answer flipping every two seconds between the zombie
 * ("fights hand to hand") and the skeleton ("takes cover") in the one cell. A cell under the pass is {@link #KNOWN}:
 * reported, not failed - and failed once it passes, so it comes off the list with the fix.
 */
class CombatPairsConditionMatrixTest {

    private static final int SEEDS = Integer.getInteger("combat.matrix.seeds", 12);

    static final List<Battle.Archetype> PAIR = List.of(Battle.Archetype.ZOMBIE, Battle.Archetype.SKELETON);
    static final List<Double> HEALTHS = List.of(20.0, 8.0);
    static final List<Terrain> GROUNDS = List.of(Terrain.FLAT, Terrain.HILLS, Terrain.RAVINE, Terrain.CAVE);

    /** Cells that don't pass yet, as "health/kit/ground". */
    static final Set<String> KNOWN = Set.of(
            "20/bare hands/ravine", "8/bare hands/ravine", "20/bare hands/cave", "8/bare hands/cave", "8/bare hands/branch");

    @Test
    void aZombieAndASkeletonMetHealthyOrHurt() {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT, "%nZombie and skeleton by condition, %d seeds a cell - the share alive:%n  %-7s %-13s",
                SEEDS, "health", "kit"));
        GROUNDS.forEach(g -> table.append(String.format(Locale.ROOT, " %7s", g.name().toLowerCase(Locale.ROOT))));
        table.append(String.format(Locale.ROOT, " %7s", "branch")).append('\n');
        List<String> failing = new ArrayList<>();
        List<String> fixed = new ArrayList<>();
        for (double health : HEALTHS) {
            for (CombatMatrixTest.KitCase kit : CombatPairsMatrixTest.KITS) {
                table.append(String.format(Locale.ROOT, "  %-7.0f %-13s", health, kit.name()));
                for (int g = 0; g <= GROUNDS.size(); g++) {
                    boolean branch = g == GROUNDS.size(); // last, a miner's branch cut through rock on the flat
                    Terrain ground = branch ? Terrain.FLAT : GROUNDS.get(g);
                    int right = 0;
                    for (long seed = 1; seed <= SEEDS; seed++) {
                        right += CombatPairsMatrixTest.survives(PAIR, seed, kit, ground, health, branch) ? 1 : 0;
                    }
                    double share = (double) right / SEEDS;
                    String cell = Math.round(health) + "/" + kit.name() + "/" + (branch ? "branch" : ground.name().toLowerCase(Locale.ROOT));
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
