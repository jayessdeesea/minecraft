package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ArmourCheck.Piece;
import org.junit.jupiter.api.Test;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LadderOrdersTest {

    @Test
    void anArmourItemsNameIsTheSlotItGoesIn() {
        assertEquals(Optional.of(Piece.BOOTS), LadderOrders.piece("copper_boots"));
        assertEquals(Optional.of(Piece.CHESTPLATE), LadderOrders.piece("iron_chestplate"));
        assertEquals(Optional.of(Piece.HELMET), LadderOrders.piece("leather_helmet"));
        assertEquals(Optional.of(Piece.LEGGINGS), LadderOrders.piece("diamond_leggings"));
        assertEquals(Optional.empty(), LadderOrders.piece("elytra"));
        assertEquals(Optional.empty(), LadderOrders.piece("copper_ingot"));
    }

    /** command can't see bot's recipes: the check's ingots a piece, pinned against what each copper and iron piece takes. */
    @Test
    void theChecksIngotsAreTheRecipes() {
        Map<Piece, java.util.List<Recipe>> pieces = Map.of(Piece.CHESTPLATE, java.util.List.of(Recipe.COPPER_CHESTPLATE, Recipe.IRON_CHESTPLATE),
                Piece.LEGGINGS, java.util.List.of(Recipe.COPPER_LEGGINGS, Recipe.IRON_LEGGINGS),
                Piece.BOOTS, java.util.List.of(Recipe.COPPER_BOOTS, Recipe.IRON_BOOTS),
                Piece.HELMET, java.util.List.of(Recipe.COPPER_HELMET, Recipe.IRON_HELMET));
        pieces.forEach((piece, recipes) -> recipes.forEach(r -> assertEquals(piece.ingots, r.rows().stream().flatMapToInt(String::chars)
                .filter(c -> r.key().get((char) c) == ItemClass.COPPER_INGOT || r.key().get((char) c) == ItemClass.IRON_INGOT).count(), r.name())));
        pieces.forEach((piece, recipes) -> recipes.forEach(r -> assertEquals(Optional.of(piece), r.item().flatMap(LadderOrders::piece), r.name())));
    }
}
