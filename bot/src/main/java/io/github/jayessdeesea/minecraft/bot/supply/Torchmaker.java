package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.CraftPlan;
import io.github.jayessdeesea.minecraft.bot.craft.Crafter;
import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.craft.Stock;
import io.github.jayessdeesea.minecraft.bot.engineer.Workshop;
import io.github.jayessdeesea.minecraft.bot.nav.BlockPos;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.Workstation;
import io.github.jayessdeesea.minecraft.bot.world.WorldView;
import io.github.jayessdeesea.minecraft.command.Position;
import io.github.jayessdeesea.minecraft.protocol.ContainerUpdate;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One soldier making torches for its squad's patrol base (docs/military-doctrine.md §14), from what
 * a squad carries: logs and cobblestone. The making is a {@link Crafter} run: planks and sticks in
 * the grid; logs smelted into charcoal with planks for fuel, at a furnace; charcoal over sticks in
 * the grid again. The furnace is the workshop's (backlog/sites.md): with none to hand it goes there,
 * and fits it out if the furnace isn't standing yet ({@link Fitter}).
 */
public final class Torchmaker {

    /** Torches a patrol base's torchbearer makes: one charcoal's worth, enough to light a hasty hut. */
    public static final int TORCHES = 4;
    private static final Map<Recipe, Integer> WANTED = Map.of(Recipe.TORCHES, TORCHES);

    private final Crafter crafter;
    private final Fitter fitter;
    private final String name;
    /** Where it's walking to make them: the workshop, when the furnace for charcoal isn't to hand. */
    private Position standAt;
    private String failure;

    public Torchmaker(String name, PlaySession session, Inventory inventory,
                      Map<BlockPos, String> furnaces) {
        this.name = name;
        this.crafter = new Crafter(name, session, inventory, furnaces);
        this.fitter = new Fitter(name, session, inventory, furnaces);
    }

    public boolean active() {
        return crafter.active() || fitter.active();
    }

    /** Why the last attempt failed, while the soldier waits out its cooldown. */
    public Optional<String> failure() {
        return Optional.ofNullable(failure).or(crafter::failure);
    }

    /** Where to stand while it makes them, if somewhere: the workshop. */
    public Optional<Position> standAt() {
        return Optional.ofNullable(standAt);
    }

    /** Where to look while it fits out the workshop. */
    public Optional<Position> lookAt() {
        return fitter.lookAt();
    }

    /** Whether it carries what torches take, made at a workshop: the logs for charcoal and sticks. */
    public static boolean canMake(Inventory inventory) {
        CraftPlan plan = CraftPlan.of(WANTED, Stock.of(inventory), EnumSet.allOf(Workstation.class));
        return plan.feasible() && !plan.nothingToDo();
    }

    /** What {@code inventory} is short of to make {@code torches} at a workshop - empty when it has the makings. */
    public static Map<ItemClassRegistry.ItemClass, Integer> shortOf(Inventory inventory, int torches) {
        CraftPlan plan = CraftPlan.of(Map.of(Recipe.TORCHES, torches), Stock.of(inventory),
                EnumSet.allOf(Workstation.class));
        return plan.feasible() ? Map.of() : plan.short_();
    }

    /** Each tick: makes torches while {@code wanted}; a hostile near ({@code threatened}) interrupts it. */
    public void tick(Position soldier, WorldView world, boolean wanted, boolean threatened, Optional<Workshop> workshop) {
        tick(soldier, world, wanted ? TORCHES : 0, threatened, workshop);
    }

    /**
     * As {@link #tick(Position, WorldView, boolean, boolean, Optional)}, making {@code torches} - in
     * fours, one charcoal each. Charcoal wants a furnace: with none to hand it goes to
     * {@code workshop} and fits it out there, never putting one down where it stands.
     */
    public void tick(Position soldier, WorldView world, int torches, boolean threatened, Optional<Workshop> workshop) {
        Map<Recipe, Integer> wanted = torches > 0 ? Map.of(Recipe.TORCHES, torches) : Map.of();
        if (wanted.isEmpty() || crafter.active()) {
            fitter.stop(soldier, world);
            standAt = crafter.active() ? standAt : null;
            crafter.tick(soldier, world, wanted, threatened);
            return;
        }
        CraftPlan plan = crafter.plan(wanted, world, soldier);
        if (!plan.short_().isEmpty() || plan.missing().isEmpty()) {
            fitter.stop(soldier, world);
            failure = null;
            crafter.tick(soldier, world, wanted, threatened);
            return;
        }
        if (workshop.isEmpty()) {
            failure = "no " + plan.missing() + " to hand for torches, and no workshop sited to make them at";
            return;
        }
        Workshop w = workshop.get();
        Position stand = new Position(w.stand().x() + 0.5, w.stand().y(), w.stand().z() + 0.5);
        if (!stand.equals(standAt)) {
            LOG.info("{} goes to the workshop at {} for torches", name, w.stand());
        }
        standAt = stand;
        if (Math.hypot(soldier.x() - stand.x(), soldier.z() - stand.z()) > Armourer.WORKSHOP_TOLERANCE || Math.abs(soldier.y() - stand.y()) > 1) {
            return;
        }
        if (!fitter.tick(soldier, world, w, plan.missing(), threatened)) {
            failure = fitter.failure().orElse(null);
        }
    }

    private static final Logger LOG = LogManager.getLogger(Torchmaker.class);

    public void onContainer(ContainerUpdate update) {
        crafter.onContainer(update);
        fitter.onContainer(update);
    }
}
