package io.github.jayessdeesea.minecraft.bot.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * A soldier's body in the battle simulation, as the pinned server keeps a player's (26.3's {@code FoodData.tick},
 * {@code CombatRules.getDamageAfterAbsorb}, {@code LivingEntity.doHurtEquipment}): health, food, saturation and
 * exhaustion; regeneration at full food with saturation of up to a point every 10 ticks (min(saturation, 6) / 6, and as
 * much exhaustion), else at 18 food or more a point every 80 ticks (6 exhaustion); every 4 exhaustion a point of
 * saturation, or of food when none is left; a blow cut by the armour worn, and each piece worn down max(1, blow / 4) by
 * it, gone at none left. It eats as the runner has a soldier eat ({@code SoldierRunner}'s rations): below 18 food, and
 * only {@link #free} - out of contact, no hostile in sight within the contact radius - a loaf from its rations, 5 food and
 * 6 saturation, after the server's 1.6 s (32 ticks) of eating; one begun is put down when the soldier is no longer free,
 * as the runner's meal is. In place of a flat point every 80 ticks: the exchange a soldier withdraws and heals by is the
 * server's (backlog/engagement-doctrine.md).
 */
final class Body {

    /** A piece of armour: its points, and the durability left. */
    private static final class Piece {
        final int points;
        int durability;

        Piece(int points, int durability) {
            this.points = points;
            this.durability = durability;
        }
    }

    /** Exhaustion a blow taken, and a swing made, adds (vanilla's). */
    static final float HURT_EXHAUSTION = 0.1f;
    static final float ATTACK_EXHAUSTION = 0.1f;

    private double health;
    private int food;
    private float saturation;
    private float exhaustion;
    private int tickTimer;
    /** Loaves carried, and the ticks left of the one being eaten. */
    private int rations = RATIONS;
    /** Loaves a soldier carries into a fight, as its kit says. */
    static final int RATIONS = 16;
    private int eating;
    static final int EAT_TICKS = 32;
    static final int BREAD_FOOD = 5;
    static final float BREAD_SATURATION = 6;
    private final List<Piece> armour = new ArrayList<>();

    /** A body at {@code health}, fed to {@code food} with {@code saturation}, wearing {@code worn}'s iron. */
    Body(double health, int food, float saturation, CombatScenarioTest.Armour worn) {
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        // Iron's pieces, by what each set wears: points and durability as the server's items have them.
        if (worn.pieces >= 1) {
            armour.add(new Piece(6, 240)); // chestplate
        }
        if (worn.pieces >= 2) {
            armour.add(new Piece(5, 225)); // leggings
        }
        if (worn.pieces >= 4) {
            armour.add(new Piece(2, 165)); // helmet
            armour.add(new Piece(2, 195)); // boots
        }
    }

    /** A soldier as the matrices start one: whole, fed, a spawn's saturation. */
    Body(CombatScenarioTest.Armour worn) {
        this(20, 20, 5, worn);
    }

    double health() {
        return health;
    }

    int food() {
        return food;
    }

    /**
     * Whether {@code soldier} is free to eat in {@code world}, as the runner has it: not in contact, and - unless it lets
     * them be - no hostile it can see within its contact radius.
     */
    static boolean free(io.github.jayessdeesea.minecraft.bot.DeterministicSoldier soldier,
                        io.github.jayessdeesea.minecraft.bot.world.WorldView world) {
        if (soldier.status().report() instanceof io.github.jayessdeesea.minecraft.command.Report.InContact) {
            return false;
        }
        io.github.jayessdeesea.minecraft.command.Position at = soldier.status().position();
        io.github.jayessdeesea.minecraft.command.Position eyes = new io.github.jayessdeesea.minecraft.command.Position(at.x(),
                at.y() + io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.EYE_HEIGHT, at.z());
        return soldier.lettingBe() || world.nearbyHostiles(at, io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.CONTACT_RADIUS)
                .stream().noneMatch(h -> io.github.jayessdeesea.minecraft.bot.DeterministicSoldier.lineOfSight(world, eyes,
                        new io.github.jayessdeesea.minecraft.command.Position(h.position().x(), h.position().y() + 1, h.position().z())));
    }

    /** Whether it's eating: its hand is the loaf's, and it strikes nothing. */
    boolean eating() {
        return eating > 0;
    }

    /** The armour points still worn. */
    int armourPoints() {
        return armour.stream().mapToInt(p -> p.points).sum();
    }

    /** A blow of {@code raw} landed: what it took after the armour, the armour worn by it. */
    double hit(double raw) {
        if (raw <= 0) {
            return 0;
        }
        int points = armourPoints();
        double effective = Math.min(20, Math.max(points / 5.0, points - raw / 2));
        double taken = raw * (1 - effective / 25);
        int wear = (int) Math.max(1, raw / 4);
        armour.forEach(p -> p.durability -= wear);
        armour.removeIf(p -> p.durability <= 0);
        health -= taken;
        exhaustion += HURT_EXHAUSTION;
        return taken;
    }

    /** A swing made. */
    void attacked() {
        exhaustion += ATTACK_EXHAUSTION;
    }

    /** A tick of the server's food, the soldier {@code free} to eat or not: whether it healed. */
    boolean tick(boolean free) {
        if (eating > 0 && !free) {
            eating = 0; // put down: the runner's meal ends when the soldier's no longer free, the loaf kept
            rations++;
        } else if (eating > 0 && --eating == 0) {
            food = Math.min(20, food + BREAD_FOOD);
            saturation = Math.min(food, saturation + BREAD_SATURATION);
        } else if (free && eating == 0 && food < 18 && rations > 0) {
            rations--;
            eating = EAT_TICKS;
        }
        if (exhaustion > 4) {
            exhaustion -= 4;
            if (saturation > 0) {
                saturation = Math.max(0, saturation - 1);
            } else {
                food = Math.max(0, food - 1);
            }
        }
        boolean healed = false;
        if (food >= 20 && saturation > 0 && health < 20) {
            if (++tickTimer >= 10) {
                float spent = Math.min(saturation, 6);
                health = Math.min(20, health + spent / 6);
                exhaustion += spent;
                tickTimer = 0;
                healed = true;
            }
        } else if (food >= 18 && health < 20) {
            if (++tickTimer >= 80) {
                health = Math.min(20, health + 1);
                exhaustion += 6;
                tickTimer = 0;
                healed = true;
            }
        } else {
            tickTimer = 0;
        }
        return healed;
    }
}
