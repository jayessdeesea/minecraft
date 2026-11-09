package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier fitting out a workshop the engineer sited (backlog/sites.md): each fixture a recipe
 * needs that isn't standing - the table first, the furnace made at it - is made and put into the
 * cell the workshop names for it. Where it goes is the workshop's; the soldier only crafts the item
 * and places it, as it would any block of a {@code Place} order.
 */
public final class Fitter {

    private final Crafter crafter;
    private final Builder table;
    private final Builder furnace;
    private final Inventory inventory;
    private String failure;
    private boolean busy;
    /** The fixture item its crafter is making. */
    private Map<Recipe, Integer> making = Map.of();

    public Fitter(String name, PlaySession session, Inventory inventory, Map<BlockPos, String> furnaces) {
        this.inventory = inventory;
        this.crafter = new Crafter(name, session, inventory, furnaces);
        this.table = new Builder(name, session, inventory, List.of(ItemClass.CRAFTING_TABLE));
        this.furnace = new Builder(name, session, inventory, List.of(ItemClass.FURNACE));
        this.chest = new Builder(name, session, inventory, List.of(ItemClass.CHEST));
    }

    private final Builder chest;

    /**
     * Each tick, standing at {@code workshop}: fits its chest - the table first, the chest made at
     * it from 8 planks. True while there's fitting to do, false once the chest stands or it can't be
     * ({@link #failure} says why: none sited, or short of planks).
     */
    public synchronized boolean tickChest(Position soldier, WorldView world, Workshop workshop, boolean threatened) {
        if (workshop.chest().isEmpty()) {
            failure = "no room for a chest at the workshop at " + workshop.stand();
            return false;
        }
        if (workshop.chestStands(world)) {
            busy = false;
            return false;
        }
        if (!workshop.stands(world, Workstation.CRAFTING_TABLE)) {
            return tick(soldier, world, workshop, Set.of(Workstation.CRAFTING_TABLE), threatened) || failure == null && !workshop.chestStands(world);
        }
        failure = null;
        if (crafter.active()) {
            crafter.tick(soldier, world, making, threatened);
            crafter.failure().ifPresent(this::fail);
            return failure == null;
        }
        busy = true;
        if (inventory.count(ItemClass.CHEST) == 0) {
            making = Map.of(Recipe.CHEST, 1);
            CraftPlan plan = crafter.plan(making, world, soldier);
            if (!plan.feasible()) {
                return fail("short of " + plan.short_() + " for the workshop's chest");
            }
            crafter.tick(soldier, world, making, threatened);
            crafter.failure().ifPresent(this::fail);
            return failure == null;
        }
        if (threatened) {
            chest.stop();
            return true;
        }
        if (chest.tick(soldier, world, List.of(workshop.chest().get())) instanceof Builder.Progress.Failed(String why)) {
            return fail("can't put the workshop's chest at " + workshop.chest().get() + ": " + why);
        }
        return true;
    }

    /** Whether it's making or placing a fixture. */
    public synchronized boolean active() {
        return busy || crafter.active();
    }

    /** Why the last fixture couldn't be made or placed. */
    public synchronized Optional<String> failure() {
        return Optional.ofNullable(failure);
    }

    /**
     * Each tick, standing at {@code workshop}: fits the next of {@code wanted}'s fixtures that isn't
     * standing - a furnace wants the table too, to be made at. True while there's one to fit, false
     * once all of them stand or one can't be had ({@link #failure} says why).
     */
    public synchronized boolean tick(Position soldier, WorldView world, Workshop workshop, Set<Workstation> wanted, boolean threatened) {
        failure = null;
        // A fixture's item being made is made to the end: the run closes its windows on a tick of its own.
        if (crafter.active()) {
            crafter.tick(soldier, world, making, threatened);
            crafter.failure().ifPresent(this::fail);
            return failure == null;
        }
        for (Workstation station : List.of(Workstation.CRAFTING_TABLE, Workstation.FURNACE)) {
            boolean needed = wanted.contains(station) || station == Workstation.CRAFTING_TABLE && wanted.contains(Workstation.FURNACE);
            if (!needed || workshop.stands(world, station)) {
                continue;
            }
            busy = true;
            ItemClass item = station == Workstation.CRAFTING_TABLE ? ItemClass.CRAFTING_TABLE : ItemClass.FURNACE;
            Builder builder = station == Workstation.CRAFTING_TABLE ? table : furnace;
            if (inventory.count(item) == 0) {
                builder.stop();
                Map<Recipe, Integer> make = Map.of(station == Workstation.CRAFTING_TABLE ? Recipe.CRAFTING_TABLE : Recipe.FURNACE, 1);
                if (!crafter.active()) {
                    CraftPlan plan = crafter.plan(make, world, soldier);
                    if (!plan.feasible()) {
                        return fail("short of " + plan.short_() + (plan.missing().isEmpty() ? "" : ", and no " + plan.missing() + " in reach")
                                + " for the workshop's " + label(station));
                    }
                }
                making = make;
                crafter.tick(soldier, world, make, threatened);
                crafter.failure().ifPresent(this::fail);
                return failure == null;
            }
            if (threatened) {
                builder.stop();
                return true;
            }
            Builder.Progress placed = builder.tick(soldier, world, List.of(workshop.cell(station)));
            if (placed instanceof Builder.Progress.Failed(String why)) {
                return fail("can't put the workshop's " + label(station) + " at " + workshop.cell(station) + ": " + why);
            }
            return true;
        }
        busy = false;
        return false;
    }

    private boolean fail(String why) {
        failure = why;
        busy = false;
        table.stop();
        furnace.stop();
        chest.stop();
        return false;
    }

    /** Stops fitting: not ticked this tick, it holds nothing - a craft under way runs to its end first. */
    public synchronized void stop(Position soldier, WorldView world) {
        if (crafter.active()) {
            crafter.tick(soldier, world, making, false); // a run under way is ticked to its end
        }
        if (!crafter.active()) {
            busy = false;
            table.stop();
            furnace.stop();
        }
    }

    /** Where to look while placing, if it is. */
    public synchronized Optional<Position> lookAt() {
        return table.lookAt().or(furnace::lookAt).or(chest::lookAt);
    }

    public void onContainer(ContainerUpdate update) {
        crafter.onContainer(update);
    }

    private static String label(Workstation station) {
        return station == Workstation.CRAFTING_TABLE ? "crafting table" : "furnace";
    }
}
