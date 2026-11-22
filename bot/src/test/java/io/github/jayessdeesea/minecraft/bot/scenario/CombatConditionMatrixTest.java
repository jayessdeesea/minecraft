package io.github.jayessdeesea.minecraft.bot.scenario;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.github.jayessdeesea.minecraft.bot.scenario.CombatScenarioTest.Ending;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The combat eval matrix grown by the soldier's condition (backlog/engagement-doctrine.md, the exchange): one soldier with
 * a stone sword against one hostile, by the health it meets it at, the iron it wears - a rung of the equipment ladder at a
 * time - and the ground, the ravine's and the cliff's drops among it. Each cell held to what correct is there, as
 * {@link CombatMatrixTest}'s: never killed; never in a creeper's blast; against a zombie, it's killed - or, met hurt, got
 * away from: a soldier that withdraws to heal carries on with its work, and meets it again only if it's in the way - or,
 * with no way between them, let be and whole. A
 * cell passes at {@link CombatMatrixTest#PASS} of its seeds; one that doesn't yet is in {@link #KNOWN}, reported, not
 * failed - and failed once it passes, so it comes off the list with the fix.
 */
@Tag("slow")
class CombatConditionMatrixTest {

    private static final int SEEDS = Integer.getInteger("combat.matrix.seeds", 12);

    static final List<Battle.Archetype> HOSTILES = List.of(Battle.Archetype.values());
    /** Whole, hurt, and badly hurt: a contact met after the last. */
    static final List<Double> HEALTHS = List.of(20.0, 12.0, 6.0);
    /** Iron by the ladder's rungs: none, the chestplate, the whole set. */
    static final List<CombatScenarioTest.Armour> ARMOURS = List.of(CombatScenarioTest.Armour.NONE, CombatScenarioTest.Armour.CHESTPLATE,
            CombatScenarioTest.Armour.FULL_IRON);
    static final List<Terrain> GROUNDS = List.of(Terrain.FLAT, Terrain.HILLS, Terrain.FOREST, Terrain.POND, Terrain.RAVINE, Terrain.CLIFF);

    /** Cells that don't pass yet, as "hostile/health/armour/ground". */
    static final Set<String> KNOWN = Set.of(
            // A creeper on any ground but the flat and the trees, whatever the soldier's condition: on hills, across a ravine or
            // under a cliff a sprint doesn't get clear of its blast, and afloat it can't sprint (as CombatMatrixTest's known).
            "creeper/20/none/hills",
            "creeper/20/none/pond",
            "creeper/20/none/ravine",
            "creeper/20/none/cliff",
            "creeper/20/chestplate/hills",
            "creeper/20/chestplate/pond",
            "creeper/20/chestplate/ravine",
            "creeper/20/chestplate/cliff",
            "creeper/20/full_iron/hills",
            "creeper/20/full_iron/pond",
            "creeper/20/full_iron/ravine",
            "creeper/20/full_iron/cliff",
            "creeper/12/none/hills",
            "creeper/12/none/pond",
            "creeper/12/none/ravine",
            "creeper/12/none/cliff",
            "creeper/12/chestplate/hills",
            "creeper/12/chestplate/pond",
            "creeper/12/chestplate/ravine",
            "creeper/12/chestplate/cliff",
            "creeper/12/full_iron/hills",
            "creeper/12/full_iron/pond",
            "creeper/12/full_iron/ravine",
            "creeper/12/full_iron/cliff",
            "creeper/6/none/hills",
            "creeper/6/none/pond",
            "creeper/6/none/ravine",
            "creeper/6/none/cliff",
            "creeper/6/chestplate/hills",
            "creeper/6/chestplate/pond",
            "creeper/6/chestplate/ravine",
            "creeper/6/chestplate/cliff",
            "creeper/6/full_iron/hills",
            "creeper/6/full_iron/pond",
            "creeper/6/full_iron/ravine",
            "creeper/6/full_iron/cliff",
            // Met badly hurt, unarmoured or nearly: in a pond, swimming, it can't get clear of a skeleton and fights it at 6; a
            // pillager keeps 6 off and follows at a walk, and on hills, among trees or afloat it isn't got clear of either.
            "skeleton/12/none/pond",
            "skeleton/6/none/pond",
            "skeleton/6/chestplate/pond",
            "pillager/6/none/hills",
            "pillager/6/none/forest",
            "pillager/6/none/pond",
            "pillager/6/chestplate/hills");

    static boolean correct(Battle.Archetype hostile, double health, CombatScenarioTest.Fight fight) {
        Ending ending = fight.ending();
        if (ending == Ending.DIED) {
            return false;
        }
        if (hostile == Battle.Archetype.CREEPER) {
            return ending != Ending.SURVIVED_BLAST || fight.lastTaken() == 0; // out of its blast
        }
        if (hostile == Battle.Archetype.ZOMBIE) {
            // Or let be with no way between them - across a ravine - and whole at the end: neither reaches the other.
            return ending == Ending.KILLED_IT || health < 20 && ending == Ending.BROKE_CONTACT
                    || ending == Ending.STILL_ENGAGED && fight.healthLeft() >= 20 - 1e-9;
        }
        return true;
    }

    @Test
    void everyConditionAgainstWhatCorrectIs() {
        StringBuilder table = new StringBuilder(String.format(Locale.ROOT,
                "%nCombat by condition, a stone sword, %d seeds a cell - the share right:%n  %-9s %-6s %-15s", SEEDS, "hostile", "health", "armour"));
        GROUNDS.forEach(g -> table.append(String.format(Locale.ROOT, " %7s", g.name().toLowerCase(Locale.ROOT))));
        table.append('\n');
        List<String> failing = new ArrayList<>();
        List<String> fixed = new ArrayList<>();
        for (Battle.Archetype hostile : HOSTILES) {
            for (double health : HEALTHS) {
                for (CombatScenarioTest.Armour armour : ARMOURS) {
                    table.append(String.format(Locale.ROOT, "  %-9s %-6.0f %-15s", hostile.name().toLowerCase(Locale.ROOT), health,
                            armour.name().toLowerCase(Locale.ROOT)));
                    for (Terrain ground : GROUNDS) {
                        Map<Ending, Integer> endings = new LinkedHashMap<>();
                        int right = 0;
                        for (long seed = 1; seed <= SEEDS; seed++) {
                            CombatScenarioTest.Fight fight = CombatScenarioTest.fight(hostile, seed, Optional.of("stone_sword"), ground, armour,
                                    false, false, health);
                            endings.merge(fight.ending(), 1, Integer::sum);
                            right += correct(hostile, health, fight) ? 1 : 0;
                        }
                        double share = (double) right / SEEDS;
                        String cell = String.format(Locale.ROOT, "%s/%.0f/%s/%s", hostile.name().toLowerCase(Locale.ROOT), health,
                                armour.name().toLowerCase(Locale.ROOT), ground.name().toLowerCase(Locale.ROOT));
                        boolean known = KNOWN.contains(cell);
                        table.append(String.format(Locale.ROOT, " %6.0f%%%s", 100 * share, share >= CombatMatrixTest.PASS ? " " : known ? "k" : "!"));
                        if (share < CombatMatrixTest.PASS && !known) {
                            failing.add(cell + " " + Math.round(100 * share) + "% " + endings);
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
        assertTrue(fixed.isEmpty(), "cells passing now - take them off KNOWN: " + fixed);
        assertTrue(failing.isEmpty(), "cells under " + Math.round(100 * CombatMatrixTest.PASS) + "%: " + failing);
    }
}
