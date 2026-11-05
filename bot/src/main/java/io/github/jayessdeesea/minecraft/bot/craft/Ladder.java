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
 *   <li><b>Iron armour</b>, chestplate, leggings, boots, helmet - by protection per ingot (6 points
 *       for 8, 5 for 7, 2 for 4, 2 for 5). Each point takes 4% off a blow; against a skeleton a
 *       chestplate is worth six times what the stone sword is.
 *   <li><b>An iron sword</b> - 2 ingots for a hit of 6, not 5. Late, because against the common
 *       hostiles it rarely saves a blow: 20 health still takes 4.
 *   <li><b>An iron pickaxe</b> - 3 ingots, for durability.
 * </ol>
 * A rung is held once something as good is - an iron sword holds the stone-sword rung too. A rung
 * whose materials can't be had is passed over for the next, not waited on. Materials set aside for
 * something else - a patrol base's blocks - aren't spent on it.
 */
public final class Ladder {

    /** One step up: held when {@code held} says so, made from the first of {@code options} that can be. */
    record Rung(String name, Predicate<Inventory> held, List<Recipe> options) {
    }

    static final List<Rung> RUNGS = List.of(
            new Rung("a weapon", i -> bestMelee(i) >= 0, List.of(Recipe.STONE_SWORD, Recipe.WOODEN_SWORD)),
            new Rung("a stone sword", i -> bestMelee(i) >= ItemClassRegistry.meleeRank("stone_sword"), List.of(Recipe.STONE_SWORD)),
            new Rung("a stone pickaxe", i -> bestPickaxe(i) >= 1, List.of(Recipe.STONE_PICKAXE)),
            new Rung("an iron chestplate", i -> armour(i, Recipe.IRON_CHESTPLATE), List.of(Recipe.IRON_CHESTPLATE)),
            new Rung("iron leggings", i -> armour(i, Recipe.IRON_LEGGINGS), List.of(Recipe.IRON_LEGGINGS)),
            new Rung("iron boots", i -> armour(i, Recipe.IRON_BOOTS), List.of(Recipe.IRON_BOOTS)),
            new Rung("an iron helmet", i -> armour(i, Recipe.IRON_HELMET), List.of(Recipe.IRON_HELMET)),
            new Rung("an iron sword", i -> bestMelee(i) >= ItemClassRegistry.meleeRank("iron_sword"), List.of(Recipe.IRON_SWORD)),
            new Rung("an iron pickaxe", i -> bestPickaxe(i) >= 2, List.of(Recipe.IRON_PICKAXE)));

    private Ladder() {
    }

    /**
     * What to make next: the first rung not held with an option whose plan, from what's carried
     * less what's {@code setAside}, is short only of what {@code canGather} says the soldier can
     * fetch itself (logs, ore within reach). Empty at the top, or when nothing left can be had.
     */
    public static Optional<Recipe> next(Inventory inventory, Map<ItemClass, Integer> setAside, Set<Workstation> inReach,
                                        Predicate<ItemClass> canGather) {
        return next(inventory, setAside, inReach, canGather, RUNGS.size());
    }

    /** A weapon to fight with, if bare-handed: the first rung only. */
    public static Optional<Recipe> weapon(Inventory inventory, Map<ItemClass, Integer> setAside, Set<Workstation> inReach,
                                          Predicate<ItemClass> canGather) {
        return next(inventory, setAside, inReach, canGather, 1);
    }

    private static Optional<Recipe> next(Inventory inventory, Map<ItemClass, Integer> setAside, Set<Workstation> inReach,
                                         Predicate<ItemClass> canGather, int rungs) {
        Stock stock = Stock.of(inventory).less(setAside);
        for (Rung rung : RUNGS.subList(0, rungs)) {
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
        int iron = ItemClassRegistry.armourGrade("iron");
        String suffix = piece.item().orElseThrow().substring("iron".length());
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
