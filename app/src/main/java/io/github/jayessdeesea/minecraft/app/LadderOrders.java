package io.github.jayessdeesea.minecraft.app;

import io.github.jayessdeesea.minecraft.bot.craft.Recipe;
import io.github.jayessdeesea.minecraft.bot.supply.Pack;
import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.command.ArmourCheck;
import io.github.jayessdeesea.minecraft.command.Stockpile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The equipment ladder's book for the runner: the rung each soldier is working toward, and the platoon leader's armour
 * check (command.ArmourCheck; docs/echelons/platoon-leader/decisions.md, "Armour") - each soldier's tick gives its
 * inventory, and every {@link #CHECK_MILLIS} the check is made over them all against the stores, what it orders or why
 * not said when it changes, and an order standing unmet {@link #UNMET_MILLIS} said once as an outcome missed. Ticks
 * write and read; the check runs on whichever tick finds it due.
 */
final class LadderOrders {

    private static final Logger LOG = LogManager.getLogger(LadderOrders.class);

    /** How often the check is made: a pre-combat check, not a tick's - the armourer is a minute's work. */
    static final long CHECK_MILLIS = 30_000;
    /** A soldier not heard from in this long - gone from the server - is left out of the check. */
    static final long STALE_MILLIS = 120_000;
    /** An order standing this long, the same pieces, is an outcome missed: five times the armourer's minute. */
    static final long UNMET_MILLIS = 300_000;

    private record Heard(ArmourCheck.Inventory inventory, long at) {
    }

    private final Map<String, Heard> heard = new ConcurrentHashMap<>();
    private final Map<String, ArmourCheck.Verdict> verdicts = new ConcurrentHashMap<>();
    /** What was last said of each soldier, and since when it has stood. */
    private final Map<String, Map.Entry<String, Long>> said = new ConcurrentHashMap<>();
    /** When the last check was made; the first a whole period after the first soldier is heard, so all are. */
    private Long lastCheck;

    /** The rung each soldier is working toward on the equipment ladder, while it is (SoldierRunner.nextRung's). */
    final Map<String, Integer> rungsWanted = new ConcurrentHashMap<>();

    /**
     * {@code soldier}'s inventory as {@code inventory} shows it, and the pieces it stands ordered to make and put on and
     * doesn't hold yet - none if not ordered - the check made first if it's due, over the stores as {@code stores} holds them (every chest a
     * draw is made from), iron counted only if {@code ironIssued}.
     */
    Set<ArmourCheck.Piece> ordered(String soldier, String squad, Inventory inventory, Optional<Stockpile> stores, boolean ironIssued, long now) {
        ArmourCheck.Inventory answered = answer(soldier, squad, inventory);
        heard.put(soldier, new Heard(answered, now));
        synchronized (this) {
            if (lastCheck == null) {
                lastCheck = now;
            } else if (now - lastCheck >= CHECK_MILLIS) {
                lastCheck = now;
                heard.values().removeIf(h -> now - h.at() > STALE_MILLIS);
                List<ArmourCheck.Inventory> inventories = heard.values().stream().map(Heard::inventory).toList();
                List<ArmourCheck.Verdict> checked = ArmourCheck.check(inventories,
                        stores.map(ArmourCheck.Stores::of).orElse(new ArmourCheck.Stores(0, 0)), ironIssued);
                verdicts.keySet().retainAll(heard.keySet());
                for (int i = 0; i < checked.size(); i++) {
                    verdicts.put(checked.get(i).soldier(), checked.get(i));
                    say(checked.get(i), inventories.get(i), now);
                }
            }
        }
        // Less what it holds now: a piece made since the check is done, and its slot's next metal isn't ordered.
        return verdicts.get(soldier) instanceof ArmourCheck.Ordered o
                ? o.pieces().stream().filter(p -> !answered.held().contains(p)).collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
    }

    /** Said when what's ordered or why not changes - not each check - and an order unmet said once when it's overdue. */
    private void say(ArmourCheck.Verdict verdict, ArmourCheck.Inventory inventory, long now) {
        String gist = switch (verdict) {
            case ArmourCheck.Armoured a -> "armoured";
            case ArmourCheck.Ordered o -> "ordered " + o.pieces() + " " + o.fromStores();
            case ArmourCheck.Unable u -> u.why();
        };
        Map.Entry<String, Long> was = said.get(verdict.soldier());
        if (was != null && was.getKey().equals(gist)) {
            if (verdict instanceof ArmourCheck.Ordered && was.getValue() >= 0 && now - was.getValue() >= UNMET_MILLIS) {
                LOG.warn("[Outcome] {}'s armour order unmet in {} s: {}", verdict.soldier(), (now - was.getValue()) / 1000,
                        ArmourCheck.said(verdict, inventory));
                said.put(verdict.soldier(), Map.entry(gist, -1L)); // said once
            }
            return;
        }
        said.put(verdict.soldier(), Map.entry(gist, now));
        if (was == null && verdict instanceof ArmourCheck.Armoured) {
            return;
        }
        if (verdict instanceof ArmourCheck.Unable) {
            LOG.warn("[Armour] {}", ArmourCheck.said(verdict, inventory));
        } else {
            LOG.info("[Armour] {}", ArmourCheck.said(verdict, inventory));
        }
    }

    /** What {@code inventory} answers: the slots with a piece for them on or carried, the copper and iron carried, a free slot or not. */
    static ArmourCheck.Inventory answer(String soldier, String squad, Inventory inventory) {
        Set<ArmourCheck.Piece> held = EnumSet.noneOf(ArmourCheck.Piece.class);
        for (int slot = Inventory.ARMOUR_FIRST; slot <= Inventory.HOTBAR_LAST; slot++) {
            int item = inventory.itemAt(slot);
            if (ItemClassRegistry.classOf(item).equals(Optional.of(ItemClass.ARMOUR))) {
                ItemClassRegistry.nameOf(item).flatMap(LadderOrders::piece).ifPresent(held::add);
            }
        }
        return new ArmourCheck.Inventory(soldier, squad, held,
                inventory.count(ItemClass.COPPER_INGOT) + inventory.count(ItemClass.RAW_COPPER),
                inventory.count(ItemClass.IRON_INGOT) + inventory.count(ItemClass.RAW_IRON), Pack.freeSlots(inventory) > 0);
    }

    /** The slot an armour item's name goes in ("copper_boots": BOOTS), if it's armour. */
    static Optional<ArmourCheck.Piece> piece(String item) {
        return EnumSet.allOf(ArmourCheck.Piece.class).stream()
                .filter(p -> item.endsWith("_" + p.name().toLowerCase(java.util.Locale.ROOT))).findFirst();
    }

    /** Whether {@code recipe} makes one of the pieces {@code ordered}. */
    static boolean makes(Set<ArmourCheck.Piece> ordered, Recipe recipe) {
        return recipe.makes() == ItemClass.ARMOUR && recipe.item().flatMap(LadderOrders::piece).filter(ordered::contains).isPresent();
    }

    /**
     * The metal of {@code material}'s kind a soldier can draw: for armour ordered, all {@code stores} holds of it, ingots and
     * raw, as the check dealt it and a draw takes it (bot.supply.MetalPool); otherwise the raw ore in {@code stockpile}.
     */
    static int pooled(ItemClass material, Optional<Stockpile> stockpile, Optional<Stockpile> stores, boolean ordered) {
        return ordered ? stores.map(st -> material == ItemClass.RAW_COPPER ? st.rawCopper() + st.copperIngots()
                : material == ItemClass.RAW_IRON ? st.rawIron() + st.ironIngots() : 0).orElse(0)
                : stockpile.map(st -> material == ItemClass.RAW_COPPER ? st.rawCopper() : material == ItemClass.RAW_IRON ? st.rawIron() : 0).orElse(0);
    }
}
