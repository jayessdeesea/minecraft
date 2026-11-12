package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetalPoolTest {

    private static int item(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    private static Inventory carrying(int rawCopper, int rawIron) {
        Inventory inventory = new Inventory();
        if (rawCopper > 0) {
            inventory.setSlot(Inventory.MAIN_FIRST, new int[]{item(ItemClass.RAW_COPPER), rawCopper});
        }
        if (rawIron > 0) {
            inventory.setSlot(Inventory.MAIN_FIRST + 1, new int[]{item(ItemClass.RAW_IRON), rawIron});
        }
        return inventory;
    }

    private static Optional<Stockpile> pooled(int rawIron, int rawCopper) {
        return Optional.of(new Stockpile(0, 0, 0, rawIron, rawCopper));
    }

    @Test
    void whatThePoolCompletesThePieceWithIsDrawn() {
        MetalPool.Plan plan = MetalPool.plan(carrying(3, 0), Optional.of(Recipe.COPPER_CHESTPLATE), pooled(0, 5));

        assertEquals(Optional.of(new MetalPool.Draw(ItemClass.RAW_COPPER, 8)), plan.draw(), "three carried and five pooled make the eight");
        assertEquals(Set.of(), plan.stock());
    }

    @Test
    void oreThatCantMakeThePieceGoesIn() {
        assertEquals(Set.of(ItemClass.RAW_COPPER), MetalPool.plan(carrying(3, 0), Optional.of(Recipe.COPPER_CHESTPLATE), pooled(0, 2)).stock(),
                "three and two pooled are short of eight: it pools its three for whoever can finish one");
        assertEquals(Set.of(ItemClass.RAW_COPPER), MetalPool.plan(carrying(4, 0), Optional.of(Recipe.SHIELD), Optional.empty()).stock(),
                "a shield takes iron: the copper is the force's");
    }

    @Test
    void oreThatMakesThePieceIsKept() {
        MetalPool.Plan plan = MetalPool.plan(carrying(8, 1), Optional.of(Recipe.COPPER_CHESTPLATE), pooled(4, 4));

        assertEquals(Set.of(ItemClass.RAW_IRON), plan.stock(), "the copper makes it; the iron isn't wanted for it");
        assertEquals(Optional.empty(), plan.draw());
    }
}
