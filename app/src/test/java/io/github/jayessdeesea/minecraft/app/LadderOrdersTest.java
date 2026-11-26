package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.command.ArmourCheck;
import io.github.jayessdeesea.minecraft.command.ArmourCheck.Piece;
import io.github.jayessdeesea.minecraft.command.Site;
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
        assertEquals(Optional.of(Piece.SHIELD), LadderOrders.piece("shield"));
        assertEquals(Optional.empty(), LadderOrders.piece("elytra"));
        assertEquals(Optional.empty(), LadderOrders.piece("copper_ingot"));
    }

    /** The basis of issue, as the ladder authorises it: the shield and iron armour from BUILDUP; no camp, all. */
    @Test
    void whatTheCampsLevelIssues() {
        assertEquals(new ArmourCheck.Issue(false, false), LadderOrders.issued(Optional.of(Optional.of(Site.Level.SHELTER))));
        assertEquals(new ArmourCheck.Issue(true, true), LadderOrders.issued(Optional.of(Optional.of(Site.Level.BUILDUP))));
        assertEquals(new ArmourCheck.Issue(true, true), LadderOrders.issued(Optional.empty()));
    }

    /** command can't see bot's recipes: the check's ingots a piece, pinned against what each copper and iron piece takes. */
    @Test
    void theChecksIngotsAreTheRecipes() {
        Map<Piece, java.util.List<Recipe>> pieces = Map.of(Piece.SHIELD, java.util.List.of(Recipe.SHIELD), Piece.CHESTPLATE, java.util.List.of(Recipe.COPPER_CHESTPLATE, Recipe.IRON_CHESTPLATE),
                Piece.LEGGINGS, java.util.List.of(Recipe.COPPER_LEGGINGS, Recipe.IRON_LEGGINGS),
                Piece.BOOTS, java.util.List.of(Recipe.COPPER_BOOTS, Recipe.IRON_BOOTS),
                Piece.HELMET, java.util.List.of(Recipe.COPPER_HELMET, Recipe.IRON_HELMET));
        pieces.forEach((piece, recipes) -> recipes.forEach(r -> assertEquals(piece.ingots, r.rows().stream().flatMapToInt(String::chars)
                .filter(c -> r.key().get((char) c) == ItemClass.COPPER_INGOT || r.key().get((char) c) == ItemClass.IRON_INGOT).count(), r.name())));
        pieces.forEach((piece, recipes) -> recipes.forEach(r -> assertEquals(Optional.of(piece), LadderOrders.piece(r), r.name())));
    }
}
