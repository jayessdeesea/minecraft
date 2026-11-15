package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.CrossLevel;

import java.util.Optional;

/**
 * The tools a role's work wears out, carried as a load (backlog/maturity-model.md, kit by role:
 * protection and work apart, decided by the operator 2026-11-14). The equipment ladder ranks by
 * survival gained per resource spent, which is right for protection and wrong for tools: there the
 * tool load came after iron armour and diamond, and a dig that wears out dozens of tools had a spare
 * each. So a builder's load - pickaxes for the stone, shovels for the soil - is sized from what it has
 * still to dig: those blocks over a stone tool's uses, halved between the two tools, plus one in hand,
 * and never more than {@link CrossLevel#TOOL_LOAD} of each. Made only from what's carried -
 * cobblestone and a log, or planks or sticks - never quarried or chopped for: the wood party brings
 * the logs (docs/military-doctrine.md § 4).
 */
public final class WorkLoad {

    /** A stone tool's uses before it breaks: vanilla's 131, counted as one more. */
    static final int STONE_USES = 132;

    private WorkLoad() {
    }

    /** Pickaxes or shovels a builder carries for {@code blocksAhead} still to dig: half the blocks each, over a stone tool's uses, plus one. */
    public static int builderTools(int blocksAhead) {
        int each = (int) Math.ceil(Math.max(0, blocksAhead) / 2.0 / STONE_USES);
        return Math.min(CrossLevel.TOOL_LOAD, each + 1);
    }

    /**
     * A builder's next tool, with {@code blocksAhead} still to dig: a stone pickaxe or a stone shovel,
     * whichever it's further short of, if it carries the makings - 3 cobblestone for a pickaxe's head
     * or 1 for a shovel's, and wood for the handle. Empty with its load met, or nothing to make it from.
     */
    public static Optional<Recipe> builder(Inventory inventory, int blocksAhead) {
        int wanted = builderTools(blocksAhead);
        Stock stock = Stock.of(inventory);
        int pickaxes = stock.classes().getOrDefault(ItemClass.PICKAXE, 0);
        int shovels = stock.classes().getOrDefault(ItemClass.SHOVEL, 0);
        boolean wood = inventory.count(ItemClass.LOG) + inventory.count(ItemClass.PLANKS) + inventory.count(ItemClass.STICK) > 0;
        int stone = inventory.count(ItemClass.COBBLESTONE);
        if (!wood) {
            return Optional.empty();
        }
        boolean wantsPickaxe = pickaxes < wanted && stone >= 3;
        boolean wantsShovel = shovels < wanted && stone >= 1;
        if (wantsPickaxe && (!wantsShovel || wanted - pickaxes >= wanted - shovels)) {
            return Optional.of(Recipe.STONE_PICKAXE);
        }
        return wantsShovel ? Optional.of(Recipe.STONE_SHOVEL) : Optional.empty();
    }
}
