package io.github.jayessdeesea.minecraft.command;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Class I, subsistence (FM 4-0; ATP 4-41 field feeding), for one soldier and for its squad: what a
 * soldier does about food on its own, and when its squad has to ask for help (docs/military-doctrine.md § 4).
 *
 * <p>A soldier carries a <b>basic load</b> of {@link #BASIC_LOAD} rations. Hungry, it eats. Below
 * {@link #RESUPPLY} it restocks when that's convenient - bakes the wheat it carries, hunts game in
 * sight, draws bread from the stockpile near it, and a squadmate with rations to spare shares them
 * (cross-leveling, § 13) - and below {@link #FOOD_FIRST} food comes before its work: it hunts and
 * draws from the stockpile wherever it is. One still short with none of those means makes the squad's
 * Class I {@link Rating#RED}: the squad can't feed itself, and asks. Feeding a force is planned above
 * the squad, as field feeding is above the company, so the request goes to the platoon leader, whose answer
 * is a farm or a move to game. Bread beyond the basic load goes into the stockpile, for whoever's short.
 */
public final class Subsistence {

    /** Rations a soldier keeps: a stack, days of work - the operator's standard ration. */
    public static final int BASIC_LOAD = 64;
    /** Below this, a soldier restocks when it's convenient: bakes, hunts what's in sight, draws from a stockpile near, asks its squadmates. */
    public static final int RESUPPLY = 32;
    /**
     * Bread the stockpile keeps in reserve, for whoever's short (operator, 2026-11-17: 64 to 256):
     * under it, a squad the work doesn't need goes to the farm, once nothing graver is short.
     */
    public static final int STOCKPILE_RESERVE = ProductionLevels.STANDING.of(Renewable.BREAD).least();
    /**
     * Bread the stockpile is stocked to, and not past: the rest stays in the packs. Between the
     * reserve and this, a squad with nothing else to do farms - the lowest priority there is
     * (operator, 2026-11-17: low priority to get to 1024, but if the workers are idle, do it).
     */
    public static final int STOCKPILE_MOST = ProductionLevels.STANDING.of(Renewable.BREAD).most();
    /**
     * Rations a soldier carries before it puts the rest in the stockpile: two basic loads, the second
     * there to share (operator, 2026-11-17: carry up to 128, so they can share it with the others).
     */
    public static final int CARRY_MOST = 128;
    /** Below this, food comes first, and a soldier with no means of its own is short. */
    public static final int FOOD_FIRST = 10;

    private Subsistence() {
    }

    /** What a soldier does about food now. */
    public enum Need {
        /** Fed and carrying its basic load. */
        NONE,
        /** Hungry, with a ration to eat. */
        EAT,
        /** Below {@link #RESUPPLY}: restocks as it goes - bakes, hunts what's in sight, draws from a stockpile near, is shared some. */
        RESTOCK,
        /** Below {@link #FOOD_FIRST}: food before its work - hunts and draws from the stockpile wherever it is. */
        FOOD_FIRST
    }

    /** A squad's Class I rating, as a LOGSTAT gives it. */
    public enum Rating {
        /** Every soldier at {@link #FOOD_FIRST} rations or more. */
        GREEN,
        /** Some below, each with a means: wheat to bake, game in sight, a squadmate sharing. */
        AMBER,
        /** One or more below with no means: the squad asks. */
        RED
    }

    /**
     * The squad's Class I: its rating, who is short ({@code short_}, below {@link #FOOD_FIRST}), and who of
     * them has no means of getting food ({@code helpless}).
     */
    public record Status(Rating rating, List<String> short_, List<String> helpless) {
        public Status {
            short_ = List.copyOf(short_);
            helpless = List.copyOf(helpless);
        }

        /** The LOGSTAT's Class I line: said as the request it is, when RED. */
        public String line() {
            return switch (rating) {
                case GREEN -> "Class I GREEN: every soldier carries food.";
                case AMBER -> String.format(Locale.ROOT, "Class I AMBER: %s short of food, getting some (baking, hunting, farming or shared).",
                        String.join(", ", short_));
                case RED -> String.format(Locale.ROOT, "Class I RED - request: %s out of food with no way to get any: no wheat to bake,"
                        + " no game in sight, no squadmate with any to share. The squad can't feed itself.", String.join(", ", helpless));
            };
        }
    }

    /** What a soldier with {@code food} (0-20) and {@code rations} carried does about it. */
    public static Need need(int food, int rations) {
        if (food < Condition.REGENERATION_FOOD && rations > 0) {
            return Need.EAT;
        }
        return rations < FOOD_FIRST ? Need.FOOD_FIRST : rations < RESUPPLY ? Need.RESTOCK : Need.NONE;
    }

    /** Whether a soldier carrying {@code rations} is short: below {@link #FOOD_FIRST}. */
    public static boolean short_(int rations) {
        return rations < FOOD_FIRST;
    }

    /**
     * Whether a soldier at {@code food} with {@code rations} must go and feed before any work: hungry
     * - below {@link Condition#REGENERATION_FOOD} - with nothing to eat. One short of rations but fed
     * works on and eats what it has; live, two builders carrying 8 and 9 rations at food 20 were held
     * off the keep as short, and with two more keeping the farm the squad had nobody on it.
     */
    public static boolean mustFeed(int food, int rations) {
        return food < Condition.REGENERATION_FOOD && rations == 0;
    }

    /** Wheat to a loaf. */
    public static final int BREAD_WHEAT = 3;
    /**
     * Loaves' worth of wheat a soldier carries before it walks to a table to bake them: a trip a batch, not a loaf - live
     * (field617), Ned walked from the farm to the camp's table and back, some 50 blocks, to bake one (operator, 2026-11-21:
     * this is killing productivity).
     */
    public static final int BAKE_BATCH = 8;

    /**
     * Whether a soldier carrying {@code wheat} and {@code rations} should go and bake: a batch of loaves' worth, or a loaf's
     * when it's running out of rations (3 or fewer, CrossLevel's running out) and needs one now.
     */
    public static boolean bakeTrip(int wheat, int rations) {
        return wheat >= BAKE_BATCH * BREAD_WHEAT || wheat >= BREAD_WHEAT && rations <= 3;
    }

    /** Whether a soldier carrying {@code rations} restocks when it's convenient: below {@link #RESUPPLY}. */
    public static boolean low(int rations) {
        return rations < RESUPPLY;
    }

    /**
     * Whether {@code squad} carries rations enough between them for everyone to be at {@link #FOOD_FIRST}
     * - so a soldier short is fed by cross-leveling (§ 13), a hand-over of seconds, and nobody walks to
     * the farm for it. Live, a squad went AMBER on one builder's empty pack while three squadmates
     * carried plenty, and the builder walked 80 blocks to the camp's farm, baked four loaves for
     * itself, and walked 80 back.
     */
    public static boolean canShare(List<SoldierStatus> squad) {
        int rations = squad.stream().mapToInt(s -> s.condition().flatMap(Condition::kit).map(Kit::rations).orElse(0)).sum();
        return !squad.isEmpty() && rations >= FOOD_FIRST * squad.size();
    }

    /** Rations a soldier carrying {@code rations} has beyond its basic load, for the stockpile. */
    public static int surplus(int rations) {
        return Math.max(0, rations - BASIC_LOAD);
    }

    /**
     * The squad's Class I from its soldiers' kit: those short, and of them those in {@code noMeans} -
     * worked out by the soldiers' own shell, which sees the wheat, the game and the shares.
     */
    public static Status of(List<SoldierStatus> squad, Set<String> noMeans) {
        List<String> short_ = squad.stream()
                .filter(s -> s.condition().flatMap(Condition::kit).map(k -> short_(k.rations())).orElse(false))
                .map(SoldierStatus::name).toList();
        List<String> helpless = short_.stream().filter(noMeans::contains).toList();
        Rating rating = short_.isEmpty() ? Rating.GREEN : helpless.isEmpty() ? Rating.AMBER : Rating.RED;
        return new Status(rating, short_, helpless);
    }
}
