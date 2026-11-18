package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.protocol.ItemSlot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.github.jayessdeesea.minecraft.bot.Hands;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The tick loop's small pure helpers: an inventory slot as the server sends it, a routine's turn at the hands. */
final class Slots {

    private Slots() {
    }

    /** One inventory slot, its enchantments named through this session's synced registry. */
    static Kit setSlot(Inventory inventory, int slot, Optional<ItemSlot> item, List<String> enchantmentNames) {
        if (item.isEmpty()) {
            return inventory.setSlot(slot, null);
        }
        Map<String, Integer> named = new HashMap<>();
        item.get().enchantments().forEach((id, level) -> {
            if (id < 0 || id >= enchantmentNames.size()) {
                throw new IllegalStateException("enchantment " + id + " isn't in the synced registry of " + enchantmentNames.size());
            }
            named.put(enchantmentNames.get(id), level);
        });
        return inventory.setSlot(slot, new int[]{item.get().itemId(), item.get().count()}, named);
    }

    /** One routine's turn at the soldier's hands. */
    static Hands.Turn turn(BooleanSupplier active, Consumer<Boolean> tick) {
        return new Hands.Turn() {
            @Override
            public boolean active() {
                return active.getAsBoolean();
            }

            @Override
            public void tick(boolean free) {
                tick.accept(free);
            }
        };
    }
}
