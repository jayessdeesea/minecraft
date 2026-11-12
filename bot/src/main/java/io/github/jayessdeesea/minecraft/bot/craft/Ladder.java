package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The equipment ladder (docs/military-doctrine.md §11): what a soldier makes next to be harder to
 * kill, as a fixed table rather than a model's decision - the trade-offs are known, and a model
 * shouldn't spend calls rediscovering them. The squad leader decides <i>whether</i> there's time
 * (its order's {@code rearm}); this decides <i>what</i>.
 *
 * <p>Rungs are ranked by survival gained per resource spent, the cheapest gain first, as
 * {@code CombatScenarioTest} measures it:
 * <ol>
 *   <li><b>A weapon</b> - stone if the cobblestone is to hand, wooden if not. Bare-handed against a
 *       skeleton loses nearly every fight; any sword wins nearly all.
 *   <li><b>A stone sword</b> - 2 cobblestone for a hit of 5, not 4: cheap, though worth little
 *       against a skeleton, whose arrows on the way in do most of the harm.
 *   <li><b>A stone pickaxe</b> - 3 cobblestone, and it mines iron ore.
 *   <li><b>A shield</b> - 1 iron ingot and 6 planks, raised against arrows in flight: against a
 *       skeleton it's worth what a chestplate is, for an eighth of the iron.
 *   <li><b>Iron armour</b>, chestplate, leggings, boots, helmet - by protection per ingot (6 points
 *       for 8, 5 for 7, 2 for 4, 2 for 5). Each point takes 4% off a blow; against a skeleton a
 *       chestplate is worth six times what the stone sword is.
 *   <li><b>An iron sword</b> - 2 ingots for a hit of 6, not 5. Late, because against the common
 *       hostiles it rarely saves a blow: 20 health still takes 4.
 *   <li><b>An iron pickaxe</b> - 3 ingots, for durability.
 *   <li><b>An iron axe</b> - 3 ingots, and a log chops in a sixth of the bare-handed time.
 * </ol>
 * A rung is held once something as good is - an iron sword holds the stone-sword rung too. A rung
 * whose materials can't be had is passed over for the next, not waited on. Materials set aside for
 * something else - a patrol base's blocks - aren't spent on it.
 */
public final class Ladder {

    /** One step up: held when {@code held} says so, made from the first of {@code options} that can be. */
    record Rung(String name, Predicate<Inventory> held, List<Recipe> options) {
    }

    static final List<Rung> LADDER = List.of(
            new Rung("a weapon", i -> bestMelee(i) >= 0, List.of(Recipe.STONE_SWORD, Recipe.WOODEN_SWORD)),
            new Rung("a stone sword", i -> bestMelee(i) >= ItemClassRegistry.meleeRank("stone_sword"), List.of(Recipe.STONE_SWORD)),
            // Only with the string in hand: string is a spider's drop, not something to go and fetch.
            new Rung("a bow", i -> i.slotOfNamed("bow") >= 0, List.of(Recipe.BOW)),
            new Rung("a stone pickaxe", i -> bestPickaxe(i) >= 1, List.of(Recipe.STONE_PICKAXE)),
            new Rung("a shield", i -> Stock.of(i).classes().getOrDefault(ItemClass.SHIELD, 0) > 0, List.of(Recipe.SHIELD)),
            // Copper first, where there is copper: it's on the surface far more often than iron, and
            // a soldier in copper is better off than one in nothing while the iron is found.
            new Rung("a copper chestplate", i -> armour(i, Recipe.COPPER_CHESTPLATE), List.of(Recipe.COPPER_CHESTPLATE)),
            new Rung("copper leggings", i -> armour(i, Recipe.COPPER_LEGGINGS), List.of(Recipe.COPPER_LEGGINGS)),
            new Rung("copper boots", i -> armour(i, Recipe.COPPER_BOOTS), List.of(Recipe.COPPER_BOOTS)),
            new Rung("a copper helmet", i -> armour(i, Recipe.COPPER_HELMET), List.of(Recipe.COPPER_HELMET)),
            new Rung("an iron chestplate", i -> armour(i, Recipe.IRON_CHESTPLATE), List.of(Recipe.IRON_CHESTPLATE)),
            new Rung("iron leggings", i -> armour(i, Recipe.IRON_LEGGINGS), List.of(Recipe.IRON_LEGGINGS)),
            new Rung("iron boots", i -> armour(i, Recipe.IRON_BOOTS), List.of(Recipe.IRON_BOOTS)),
            new Rung("an iron helmet", i -> armour(i, Recipe.IRON_HELMET), List.of(Recipe.IRON_HELMET)),
            new Rung("an iron sword", i -> bestMelee(i) >= ItemClassRegistry.meleeRank("iron_sword"), List.of(Recipe.IRON_SWORD)),
            new Rung("an iron pickaxe", i -> bestPickaxe(i) >= 2, List.of(Recipe.IRON_PICKAXE)),
            new Rung("an iron axe", i -> bestAxeSpeed(i) >= 6, List.of(Recipe.IRON_AXE)),
            // Diamond only after the iron armour, and only with the diamonds carried: they're ore
            // the miner takes beside its stairs, not something a soldier goes and fetches.
            new Rung("a diamond sword", i -> bestMelee(i) >= ItemClassRegistry.meleeRank("diamond_sword"), List.of(Recipe.DIAMOND_SWORD)),
            new Rung("a diamond chestplate", i -> armour(i, Recipe.DIAMOND_CHESTPLATE), List.of(Recipe.DIAMOND_CHESTPLATE)),
            new Rung("diamond leggings", i -> armour(i, Recipe.DIAMOND_LEGGINGS), List.of(Recipe.DIAMOND_LEGGINGS)),
            new Rung("diamond boots", i -> armour(i, Recipe.DIAMOND_BOOTS), List.of(Recipe.DIAMOND_BOOTS)),
            new Rung("a diamond helmet", i -> armour(i, Recipe.DIAMOND_HELMET), List.of(Recipe.DIAMOND_HELMET)),
            new Rung("a diamond pickaxe", i -> bestPickaxe(i) >= 3, List.of(Recipe.DIAMOND_PICKAXE)));

    private Ladder() {
    }

    /**
     * What to make next: the first rung not held with an option whose plan, from what's carried
     * less what's {@code setAside}, is short only of what {@code canGather} says the soldier can
     * fetch itself (logs, ore within reach). Empty at the top, or when nothing left can be had.
     */
    public static Optional<Recipe> next(Inventory inventory, Map<ItemClass, Integer> setAside, Set<Workstation> inReach,
                                        Predicate<ItemClass> canGather) {
        return upTo(inventory, setAside, inReach, canGather, RUNGS - 1);
    }

    /** A weapon to fight with, if bare-handed: the first rung only. */
    public static Optional<Recipe> weapon(Inventory inventory, Map<ItemClass, Integer> setAside, Set<Workstation> inReach,
                                          Predicate<ItemClass> canGather) {
        return upTo(inventory, setAside, inReach, canGather, 0);
    }

    /** How many rungs there are. */
    public static final int RUNGS = LADDER.size();

    /**
     * The last rung of a soldier's basic load, its own to make unasked: through iron armour - a
     * weapon, a bow, a stone pickaxe, a shield, and copper or iron armour from the ore to hand.
     * Live, the camp's armoured standard stood at 3 of 32 slots for days with ore in the packs: in
     * 700 FRAGOs no squad leader said "rearm". Above it - an iron sword and up - the squad leader's leave.
     */
    public static final int BASIC_LOAD = rungOf(Recipe.IRON_HELMET);

    /** Which rung {@code recipe} is made for, counted from 0 - the first that offers it. */
    public static int rungOf(Recipe recipe) {
        for (int i = 0; i < LADDER.size(); i++) {
            if (LADDER.get(i).options().contains(recipe)) {
                return i;
            }
        }
        throw new IllegalArgumentException(recipe.name() + " is on no rung of the ladder");
    }

    /** {@link #next}, looking no higher than rung {@code highest} (from 0). */
    public static Optional<Recipe> upTo(Inventory inventory, Map<ItemClass, Integer> setAside, Set<Workstation> inReach,
                                        Predicate<ItemClass> canGather, int highest) {
        Stock stock = Stock.of(inventory).less(setAside);
        for (Rung rung : LADDER.subList(0, Math.max(0, Math.min(LADDER.size(), highest + 1)))) {
            if (rung.held().test(inventory)) {
                continue;
            }
            for (Recipe option : rung.options()) {
                CraftPlan plan = CraftPlan.of(Map.of(option, 1), stock, inReach);
                if (plan.short_().keySet().stream().allMatch(canGather)) {
                    return Optional.of(option);
                }
            }
        }
        return Optional.empty();
    }

    /** The best melee rank carried, or -1. */
    private static int bestMelee(Inventory inventory) {
        int best = -1;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            OptionalInt rank = ItemClassRegistry.meleeRank(inventory.itemAt(slot));
            if (rank.isPresent()) {
                best = Math.max(best, rank.getAsInt());
            }
        }
        return best;
    }

    /** The fastest axe's speed, or 0 with none. */
    private static double bestAxeSpeed(Inventory inventory) {
        double best = 0;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            best = Math.max(best, ItemClassRegistry.axeSpeed(inventory.itemAt(slot)).orElse(0));
        }
        return best;
    }

    /** The best pickaxe's mining tier, or -1 with none. */
    public static int bestPickaxe(Inventory inventory) {
        int best = -1;
        for (int slot = Inventory.MAIN_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            OptionalInt tier = ItemClassRegistry.miningTier(inventory.itemAt(slot));
            if (tier.isPresent()) {
                best = Math.max(best, tier.getAsInt());
            }
        }
        return best;
    }

    /** Whether the armour slot {@code piece} goes in holds - or a carried piece would fill it with - iron or better. */
    private static boolean armour(Inventory inventory, Recipe piece) {
        String made = piece.item().orElseThrow();
        String material = made.substring(0, made.indexOf('_'));
        int iron = ItemClassRegistry.armourGrade(material);
        String suffix = made.substring(material.length());
        for (int slot = Inventory.ARMOUR_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            int item = inventory.itemAt(slot);
            if (ItemClassRegistry.nameOf(item).filter(n -> n.endsWith(suffix)).isPresent()
                    && ItemClassRegistry.armourGrade(item).orElse(-1) >= iron) {
                return true;
            }
        }
        return false;
    }
}
