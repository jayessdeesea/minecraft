package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.command.Kit;
import io.github.jayessdeesea.minecraft.command.Order;
import io.github.jayessdeesea.minecraft.command.SoldierStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A stuck soldier's case for the registry of stuck soldiers (StuckEvalsTest, docs/architecture.md), as drafted live
 * beside its recorded ground (WorldRecords): where it stood, its kit, the goal of its walk, and what correct is - the
 * draft's guess, standing within 1.5 of that goal in 5 minutes, which whoever files the case confirms or rewrites. Its
 * kit is rebuilt from the counts it reports (command.Kit): a pickaxe is taken for stone, logs for oak.
 */
record StuckCase(String why, String world, String soldier, double[] at, List<Item> kit, double[] goal, String correct,
                 double radius, int withinMinutes, boolean knownFailure) {

    /** A stack it carries: the inventory slot, the item's id, how many, and its name for the reader. */
    record Item(int slot, int id, int count, String item) {
    }

    /** The first slot of the main inventory, past the hotbar's own, where the case's blocks go. */
    private static final int MAIN_FIRST = 9;

    static StuckCase draft(SoldierStatus status, String world) {
        List<Item> kit = new ArrayList<>();
        Kit k = status.condition().flatMap(c -> c.kit()).orElse(null);
        if (k != null) {
            k.weapon().filter(w -> !w.equals("bow")).ifPresent(w -> add(kit, 36, w, 1));
            if (k.pickaxe()) {
                add(kit, 37, "stone_pickaxe", 1);
            }
            add(kit, 38, "bread", Math.min(64, k.rations()));
            int slot = MAIN_FIRST;
            for (String[] blocks : new String[][]{{"cobblestone", String.valueOf(k.stone())}, {"dirt", String.valueOf(k.dirt())},
                    {"oak_log", String.valueOf(k.logs())}}) {
                for (int left = Integer.parseInt(blocks[1]); left > 0 && slot < 36; left -= 64) {
                    add(kit, slot++, blocks[0], Math.min(64, left));
                }
            }
        }
        double[] goal = status.order() instanceof Order.MoveTo m ? new double[]{m.x(), m.y(), m.z()}
                : new double[]{status.position().x(), status.position().y(), status.position().z()};
        return new StuckCase(status.name() + " stuck at its post: " + status.report() + " - order " + status.order(), world, status.name(),
                new double[]{status.position().x(), status.position().y(), status.position().z()}, kit, goal,
                "stands within 1.5 of its walk's goal (draft: confirm or rewrite what correct is here)", 1.5, 5, true);
    }

    private static void add(List<Item> kit, int slot, String name, int count) {
        OptionalInt id = ItemClassRegistry.idOf(name);
        if (count > 0 && id.isPresent()) {
            kit.add(new Item(slot, id.getAsInt(), count, name));
        }
    }
}
