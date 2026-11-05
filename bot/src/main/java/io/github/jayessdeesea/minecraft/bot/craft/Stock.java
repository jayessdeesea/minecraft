package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * What a soldier has, as a {@link CraftPlan} counts it: each class, and each item by name - what a
 * {@link Recipe} for one grade of a class (a stone sword, an iron chestplate) is counted by. Worn
 * armour and the off hand count: a soldier wearing a chestplate has one.
 */
public record Stock(Map<ItemClass, Integer> classes, Map<String, Integer> items) {

    public Stock {
        classes = Map.copyOf(classes);
        items = Map.copyOf(items);
    }

    /** Counts by class only: no graded items. */
    public static Stock of(Map<ItemClass, Integer> classes) {
        return new Stock(classes, Map.of());
    }

    /** Everything {@code inventory} holds, outside the crafting grid. */
    public static Stock of(Inventory inventory) {
        Map<ItemClass, Integer> classes = new EnumMap<>(ItemClass.class);
        Map<String, Integer> items = new HashMap<>();
        for (int slot = Inventory.ARMOUR_FIRST; slot < Inventory.SLOTS; slot++) {
            int item = inventory.itemAt(slot);
            int count = inventory.countAt(slot);
            ItemClassRegistry.classOf(item).ifPresent(c -> classes.merge(c, count, Integer::sum));
            ItemClassRegistry.nameOf(item).ifPresent(n -> items.merge(n, count, Integer::sum));
        }
        return new Stock(classes, items);
    }

    /** How many of what {@code recipe} makes are had. */
    public int count(Recipe recipe) {
        return recipe.item().map(i -> items.getOrDefault(i, 0)).orElseGet(() -> classes.getOrDefault(recipe.makes(), 0));
    }

    /** This, less what's {@code setAside} of each class - none below nothing. */
    public Stock less(Map<ItemClass, Integer> setAside) {
        Map<ItemClass, Integer> left = new EnumMap<>(ItemClass.class);
        left.putAll(classes);
        setAside.forEach((c, n) -> left.computeIfPresent(c, (k, had) -> Math.max(0, had - n)));
        return new Stock(left, items);
    }

    /** This, and {@code more} of each class besides. */
    public Stock plus(Map<ItemClass, Integer> more) {
        Map<ItemClass, Integer> sum = new EnumMap<>(ItemClass.class);
        sum.putAll(classes);
        more.forEach((c, n) -> sum.merge(c, n, Integer::sum));
        return new Stock(sum, items);
    }
}
