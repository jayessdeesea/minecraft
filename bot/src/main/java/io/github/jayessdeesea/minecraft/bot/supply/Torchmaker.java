package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.craft.Stock;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier making torches for its squad's patrol base (docs/military-doctrine.md §14), from what
 * a squad carries: logs and cobblestone. The making is a {@link Crafter} run: planks, sticks and a
 * crafting table in the grid; a furnace made at the table and placed; logs smelted into charcoal
 * with planks for fuel; charcoal over sticks in the grid again. A table or furnace already
 * standing within reach - the camp's kit from an earlier run - is used, not made again.
 */
public final class Torchmaker {

    /** Torches a patrol base's torchbearer makes: one charcoal's worth, enough to light a hasty hut. */
    public static final int TORCHES = 4;
    private static final Map<Recipe, Integer> WANTED = Map.of(Recipe.TORCHES, TORCHES);

    private final Crafter crafter;

    public Torchmaker(String name, PlaySession session, Inventory inventory) {
        this.crafter = new Crafter(name, session, inventory);
    }

    public boolean active() {
        return crafter.active();
    }

    /** Why the last attempt failed, while the soldier waits out its cooldown. */
    public Optional<String> failure() {
        return crafter.failure();
    }

    /** Whether it carries what torches take, with no table or furnace to hand: the logs, and a furnace's cobblestone or a furnace. */
    public static boolean canMake(Inventory inventory) {
        CraftPlan plan = CraftPlan.of(WANTED, Stock.of(inventory), Set.of());
        return plan.feasible() && !plan.nothingToDo();
    }

    /** Each tick: makes torches while {@code wanted}; a hostile near ({@code threatened}) interrupts it. */
    public void tick(Position soldier, WorldView world, boolean wanted, boolean threatened) {
        crafter.tick(soldier, world, wanted ? WANTED : Map.of(), threatened);
    }

    public void onContainer(ContainerUpdate update) {
        crafter.onContainer(update);
    }
}
