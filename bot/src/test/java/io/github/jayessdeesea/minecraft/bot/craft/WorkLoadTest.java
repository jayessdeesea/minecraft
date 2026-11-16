package io.github.jayessdeesea.minecraft.bot.craft;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.CrossLevel;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkLoadTest {

    private final Inventory inventory = new Inventory();
    private int slot = Inventory.MAIN_FIRST;

    private WorkLoadTest carrying(String item, int count) {
        int id = IntStream.range(0, 4096).filter(i -> ItemClassRegistry.nameOf(i).equals(Optional.of(item))).findFirst().orElseThrow();
        inventory.setSlot(slot++, new int[]{id, count});
        return this;
    }

    @Test
    void theLoadIsSizedFromWhatsLeftToDig() {
        assertEquals(1, WorkLoad.builderTools(0), "one in hand, with nothing left");
        assertEquals(2, WorkLoad.builderTools(200), "100 blocks a tool: one wears out, one more");
        assertEquals(CrossLevel.TOOL_LOAD, WorkLoad.builderTools(5_000), "never past the tool load: a main and a spare");
    }

    @Test
    void aBuilderMakesTheToolItsFurthestShortOfFromWhatItCarries() {
        carrying("stone_pickaxe", 1).carrying("cobblestone", 64).carrying("oak_log", 2);

        assertEquals(Optional.of(Recipe.STONE_SHOVEL), WorkLoad.builder(inventory, 2_000), "no shovel yet: a shovel first");
        carrying("stone_shovel", 1);
        assertEquals(Optional.of(Recipe.STONE_PICKAXE), WorkLoad.builder(inventory, 2_000), "one each: a pickaxe, then the shovel");
    }

    @Test
    void withNoWoodOrHisLoadMetNothing() {
        carrying("cobblestone", 64);
        assertEquals(Optional.empty(), WorkLoad.builder(inventory, 2_000), "no wood for a handle: nothing, the wood party brings it");

        carrying("oak_log", 1).carrying("stone_pickaxe", 1).carrying("stone_shovel", 1);
        assertEquals(Optional.empty(), WorkLoad.builder(inventory, 0), "one of each in hand, nothing left to dig");
    }
}
