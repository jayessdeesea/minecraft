package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe.Station;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * How to get from what a soldier carries to what it wants, as few trips to a station as it takes:
 * every craft of a kind batched into one, and every batch at a station done in one visit. Twenty
 * torches are 5 charcoal smelted in one furnace load and one round of torches in the grid, not
 * twenty trips; a sword and a pickaxe are made at one opening of the table.
 *
 * <p>Pure: worked out from counts, not the live inventory. Expanded backwards from what's wanted -
 * each thing short is made from its {@link Recipe}, whose own ingredients are taken from what's
 * carried first, then made in turn - so a batch always comes after the batches it uses. A station
 * not already in reach is made and placed: the table or furnace item becomes a need of its own.
 * What nothing carried can make (logs, cobblestone) is {@link #short_}: the plan can't start
 * until it's gathered.
 */
public record CraftPlan(List<Session> sessions, Map<ItemClass, Integer> short_) {

    /** {@code times} crafts of {@code recipe}: {@code times x yield} of what it makes. */
    public record Batch(Recipe recipe, int times) {
        public int yield() {
            return times * recipe.yield();
        }
    }

    /** One visit to a station, and the batches made there, in order. */
    public record Session(Station station, List<Batch> batches) {
    }

    public CraftPlan {
        sessions = List.copyOf(sessions);
        short_ = Map.copyOf(short_);
    }

    /** Whether everything it takes is carried: the plan can run. */
    public boolean feasible() {
        return short_.isEmpty();
    }

    /** Whether nothing needs making: all of it is already carried. */
    public boolean nothingToDo() {
        return sessions.isEmpty();
    }

    /** Whether it makes any of {@code itemClass}. */
    public boolean makes(ItemClass itemClass) {
        return sessions.stream().flatMap(s -> s.batches().stream()).anyMatch(b -> b.recipe().makes() == itemClass);
    }

    /**
     * The plan for having at least {@code wanted} of each class, from {@code carried} counts, with
     * {@code inReach} stations already standing within reach.
     */
    public static CraftPlan of(Map<ItemClass, Integer> wanted, Map<ItemClass, Integer> carried, Set<Workstation> inReach) {
        Planner planner = new Planner(carried, inReach);
        wanted.forEach(planner::need);
        return planner.plan();
    }

    private static final class Planner {
        /** Carried or made, and not yet spoken for. */
        private final Map<ItemClass, Integer> free = new EnumMap<>(ItemClass.class);
        private final Set<Workstation> stations;
        /** Batches in the order they were worked out - each after the ones it uses. */
        private final Map<Recipe, Integer> times = new LinkedHashMap<>();
        /** The batches each batch uses: what decides which visit it can be made on. */
        private final Map<Recipe, Set<Recipe>> uses = new LinkedHashMap<>();
        private final Map<ItemClass, Integer> short_ = new EnumMap<>(ItemClass.class);

        Planner(Map<ItemClass, Integer> carried, Set<Workstation> inReach) {
            free.putAll(carried);
            stations = new HashSet<>(inReach);
        }

        /** Has {@code count} more of {@code itemClass} spoken for; returns the batch that made any of it. */
        Recipe need(ItemClass itemClass, int count) {
            int taken = Math.min(count, free.getOrDefault(itemClass, 0));
            free.merge(itemClass, -taken, Integer::sum);
            int more = count - taken;
            if (more == 0) {
                return null;
            }
            Recipe recipe = Recipe.making(itemClass).orElse(null);
            if (recipe == null) {
                short_.merge(itemClass, more, Integer::sum);
                return null;
            }
            int crafts = (more + recipe.yield() - 1) / recipe.yield();
            Set<Recipe> used = new HashSet<>();
            recipe.ingredients().forEach((ingredient, each) -> addUse(used, need(ingredient, each * crafts)));
            if (recipe.station() == Station.FURNACE) {
                addUse(used, need(ItemClass.PLANKS, Recipe.fuelFor(crafts)));
            }
            station(recipe.station()).ifPresent(item -> addUse(used, item));
            times.merge(recipe, crafts, Integer::sum);
            uses.computeIfAbsent(recipe, r -> new HashSet<>()).addAll(used);
            free.merge(itemClass, crafts * recipe.yield() - more, Integer::sum);
            return recipe;
        }

        /** The station a recipe needs, made and placed if none is in reach: the batch that makes it, if one does. */
        private java.util.Optional<Recipe> station(Station station) {
            Workstation block = switch (station) {
                case GRID -> null;
                case TABLE -> Workstation.CRAFTING_TABLE;
                case FURNACE -> Workstation.FURNACE;
            };
            if (block == null || stations.contains(block)) {
                return java.util.Optional.empty();
            }
            stations.add(block); // placed once, used by everything after
            Recipe made = need(block == Workstation.CRAFTING_TABLE ? ItemClass.CRAFTING_TABLE : ItemClass.FURNACE, 1);
            return java.util.Optional.ofNullable(made);
        }

        private static void addUse(Set<Recipe> used, Recipe recipe) {
            if (recipe != null) {
                used.add(recipe);
            }
        }

        /**
         * Visits in order: grid, table, furnace, then round again for whatever needs what the last
         * round made. Each batch goes in the earliest visit to its station after every batch it uses.
         */
        CraftPlan plan() {
            // Visit v is round v / 3 at station v % 3 (grid, table, furnace). A batch can share a
            // visit with one it uses only at the same station, after it.
            Map<Recipe, Integer> visit = new LinkedHashMap<>();
            for (Recipe recipe : inOrder()) {
                int earliest = uses.get(recipe).stream().mapToInt(visit::get).max().orElse(-1);
                int slot = recipe.station().ordinal();
                int candidate = earliest < 0 ? slot : (earliest / 3) * 3 + slot;
                visit.put(recipe, candidate < earliest ? candidate + 3 : candidate);
            }
            Map<Integer, List<Batch>> byVisit = new TreeMap<>();
            visit.forEach((recipe, at) -> byVisit.computeIfAbsent(at, v -> new ArrayList<>()).add(new Batch(recipe, times.get(recipe))));
            List<Session> sessions = new ArrayList<>();
            byVisit.forEach((at, batches) -> sessions.add(new Session(Station.values()[at % 3], batches)));
            return new CraftPlan(sessions, short_);
        }

        /** The batches, each after every batch it uses. */
        private List<Recipe> inOrder() {
            List<Recipe> order = new ArrayList<>();
            times.keySet().forEach(r -> visit(r, order));
            return order;
        }

        private void visit(Recipe recipe, List<Recipe> order) {
            if (!order.contains(recipe)) {
                uses.get(recipe).forEach(used -> visit(used, order));
                order.add(recipe);
            }
        }
    }
}
