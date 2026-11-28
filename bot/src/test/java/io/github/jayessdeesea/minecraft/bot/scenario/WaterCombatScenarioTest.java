package io.github.jayessdeesea.minecraft.bot.scenario;

import io.github.jayessdeesea.minecraft.bot.BodyMeasures;
import io.github.jayessdeesea.minecraft.bot.DeterministicSoldier;
import io.github.jayessdeesea.minecraft.bot.world.WeaponStats;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.command.Risk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pillager in deep water by the bank (operator, 2026-11-20: soldiers didn't engage pillagers in water more than a block
 * deep): a soldier with a stone sword on the bank, the pillager treading water in a pond 1 to 4 deep at several spots.
 * Correct is the pillager killed and the soldier alive - it swims out to it, or the pillager comes to it.
 */
class WaterCombatScenarioTest {

    static final int TICKS = 1_800;

    @Test
    void aPillagerInDeepWaterIsEngaged() {
        List<String> missed = new ArrayList<>();
        StringBuilder table = new StringBuilder("\nA pillager in a pond by the bank, a soldier with a stone sword:\n");
        for (int depth = 1; depth <= 4; depth++) {
            int kills = 0, runs = 0;
            for (int out = 3; out <= 7; out += 2) {
                for (int side = -2; side <= 2; side += 2) {
                    runs++;
                    String ending = fight(depth, out, side);
                    if (ending.equals("killed it")) {
                        kills++;
                    } else {
                        missed.add("depth " + depth + ", " + out + " out, " + side + " along: " + ending);
                    }
                }
            }
            table.append(String.format(Locale.ROOT, "  %d deep: %d of %d killed%n", depth, kills, runs));
        }
        System.out.println(table);
        assertTrue(missed.isEmpty(), "pillagers in water not engaged: " + missed);
    }

    /**
     * A pillager floating out in a pond, out of contact and holding - not shooting till a soldier comes within its range
     * (operator, 2026-11-20: soldiers neither worked nor fought pillagers in a pond). An archer in sight within
     * {@code DeterministicSoldier.ARCHER_ENGAGE} is closed on by one armed for melee: correct is the pillager killed.
     */
    @Test
    void aPillagerHoldingOutInAPondIsClosedOn() {
        List<String> missed = new ArrayList<>();
        for (int out = 9; out <= 13; out += 2) {
            for (int side = -3; side <= 3; side += 3) {
                String ending = fight(3, out, side, true);
                if (!ending.equals("killed it")) {
                    missed.add(out + " out, " + side + " along: " + ending);
                }
            }
        }
        assertTrue(missed.isEmpty(), "pillagers holding out in a pond not closed on: " + missed);
    }

    static String fight(int depth, int out, int side) {
        return fight(depth, out, side, false);
    }

    /** One fight: the pond from x=3 on, the soldier on the bank at x=0, the pillager {@code out} into it, holding or not. */
    static String fight(int depth, int out, int side, boolean holding) {
        SyntheticWorld world = new SyntheticWorld(63);
        for (int x = 3; x <= 20; x++) {
            for (int z = -6; z <= 6; z++) {
                world.setGround(x, z, 63 - depth);
                for (int y = 64 - depth; y <= 63; y++) {
                    world.water(x, y, z);
                }
            }
        }
        Battle battle = new Battle(world, depth * 100L + out * 10L + side);
        Battle.Hostile pillager = holding ? battle.spawnHolding(Battle.Archetype.PILLAGER, 2.5 + out, 0.5 + side)
                : battle.spawn(Battle.Archetype.PILLAGER, 2.5 + out, 0.5 + side);
        DeterministicSoldier soldier = new DeterministicSoldier("Liam", new Position(0.5, 64, 0.5));
        double health = 20;
        soldier.reportHealth(health, 20);
        Optional<String> sword = Optional.of("stone_sword");
        soldier.reportKit(new Kit(sword, 0, 0, 0, 0, 0, 0, false));
        soldier.receiveOrder(new Order.MoveTo(0.5, 64, 0.5, 1.5, 1, Risk.LOW));
        double blow = WeaponStats.melee(sword).damage();
        for (int tick = 0; tick < TICKS; tick++) {
            OptionalInt attack = soldier.tick(battle);
            Position now = soldier.status().position();
            attack.ifPresent(id -> battle.strike(id, now, BodyMeasures.ATTACK_REACH, blow));
            health -= battle.step(now);
            soldier.reportHealth(Math.max(0, health), 20);
            if (health <= 0) {
                return "died";
            }
            if (!pillager.alive()) {
                return "killed it";
            }
        }
        Position end = soldier.status().position();
        return String.format(Locale.ROOT, "alive after %d s, the soldier at (%.1f, %.1f, %.1f), %s", TICKS / 20,
                end.x(), end.y(), end.z(), soldier.status().report());
    }
}
