package io.github.jayessdeesea.minecraft.bot.supply;

import io.github.jayessdeesea.minecraft.bot.world.Inventory;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry;
import io.github.jayessdeesea.minecraft.bot.world.ItemClassRegistry.ItemClass;
import io.github.jayessdeesea.minecraft.protocol.PlaySession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against a fake server that answers as the real one does: a swap with a stale state id is answered
 * a tick later by the whole inventory, and a meal raises the food level 32 ticks after it starts.
 */
class RationsTest {

    /**
     * No meal with a creeper within 8, seen or not: live (field741), Ivy at 0 health began a meal with a creeper 7.8
     * off that her sight line then lost behind a pillar two blocks high - no contact - and it blew as she finished
     * eating. A zombie as near is the contact drill's, not this rule's.
     */
    @Test
    void noMealWithACreeperWithinEightSeenOrNot() {
        io.github.jayessdeesea.minecraft.command.Position at = new io.github.jayessdeesea.minecraft.command.Position(-11.1, 63, 8.5);
        java.util.function.Function<List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity>, io.github.jayessdeesea.minecraft.bot.world.WorldView> field = hostiles ->
                new io.github.jayessdeesea.minecraft.bot.world.WorldView() {
                    @Override
                    public boolean isSolid(int x, int y, int z) {
                        return y < 63 || x == -11 && z == 7; // the pillar between them: sight is not asked
                    }

                    @Override
                    public boolean isHazard(int x, int y, int z) {
                        return false;
                    }

                    @Override
                    public List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity> nearbyHostiles(io.github.jayessdeesea.minecraft.command.Position center, double radius) {
                        return hostiles.stream().filter(h -> h.position().distanceTo(center) <= radius).toList();
                    }
                };
        int creeper = io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry.typeIdOf("creeper");
        int zombie = io.github.jayessdeesea.minecraft.bot.world.HostileMobRegistry.typeIdOf("zombie");
        java.util.function.BiFunction<Integer, Double, List<io.github.jayessdeesea.minecraft.bot.world.TrackedEntity>> one = (type, z) ->
                List.of(new io.github.jayessdeesea.minecraft.bot.world.TrackedEntity(1, type, new io.github.jayessdeesea.minecraft.command.Position(-11.1, 63, z)));

        assertTrue(Rations.creeperNear(field.apply(one.apply(creeper, 5.5)), at), "3 off behind the pillar");
        assertTrue(Rations.creeperNear(field.apply(one.apply(creeper, 0.7)), at), "7.8 off");
        assertFalse(Rations.creeperNear(field.apply(one.apply(creeper, 0.0)), at), "8.5 off: clear");
        assertFalse(Rations.creeperNear(field.apply(one.apply(zombie, 5.5)), at), "a zombie isn't a creeper");
        assertFalse(Rations.creeperNear(field.apply(List.of()), at), "nothing near");
    }

    private static final int MEAL_TICKS = 32;

    private final List<String> calls = new ArrayList<>();
    private final Inventory inventory = new Inventory();
    /** What the server will resend next tick, or null. */
    private Runnable pending;
    private int mealStarted = -1;
    private int tick;
    private int food = 14;
    private boolean silent;
    private boolean noFood;
    private boolean hurt;

    private final PlaySession session = (PlaySession) Proxy.newProxyInstance(PlaySession.class.getClassLoader(),
            new Class<?>[]{PlaySession.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "swapWithHotbar" -> {
                        int slot = (int) args[2];
                        int hotbar = Inventory.HOTBAR_FIRST + (int) args[3];
                        calls.add("swap " + slot + " to hotbar " + args[3]);
                        if (!silent) {
                            int[] moving = stack(slot);
                            int[] staying = stack(hotbar);
                            pending = () -> {
                                inventory.setSlot(slot, staying);
                                inventory.setSlot(hotbar, moving);
                            };
                        }
                    }
                    case "selectHotbarSlot" -> calls.add("select " + args[0]);
                    case "useItem" -> {
                        calls.add("eat");
                        mealStarted = tick;
                    }
                    default -> throw new AssertionError("unexpected " + method.getName());
                }
                return null;
            });

    private int[] stack(int slot) {
        return inventory.itemAt(slot) < 0 ? null : new int[]{inventory.itemAt(slot), inventory.countAt(slot)};
    }

    private static int itemOf(ItemClass itemClass) {
        return IntStream.range(0, 4096).filter(id -> ItemClassRegistry.classOf(id).equals(Optional.of(itemClass))).findFirst().orElseThrow();
    }

    RationsTest() {
        inventory.setSlot(Inventory.HOTBAR_FIRST, new int[]{itemOf(ItemClass.WEAPON), 1});
        inventory.setSlot(20, new int[]{itemOf(ItemClass.FOOD), 5});
    }

    /** One server tick, then one soldier tick. */
    private void run(Rations rations, int ticks, boolean free) {
        for (int i = 0; i < ticks; i++, tick++) {
            if (pending != null) {
                pending.run();
                pending = null;
            }
            if (mealStarted >= 0 && tick - mealStarted >= MEAL_TICKS && !noFood) {
                food = 20;
                mealStarted = -1;
            }
            rations.tick(food, hurt, free, 0, 0);
        }
    }

    @Test
    void aWholeSoldierEatsOnlyOnceALoafFitsUnderTheBarAndAHurtOneBelowTheHealingLevel() {
        Rations rations = new Rations("Alice", session, inventory);

        food = Rations.EAT_AT + 1;
        run(rations, 1, true);
        assertTrue(calls.isEmpty(), "whole, at 16: a loaf of five would be lost over the top");
        hurt = true;
        run(rations, 1, true);
        assertFalse(calls.isEmpty(), "hurt: it eats where health comes back");
    }

    @Test
    void aHungrySoldierEatsAndTakesItsWeaponBack() {
        Rations rations = new Rations("Alice", session, inventory);

        run(rations, MEAL_TICKS + 5, true);

        assertEquals(List.of("swap 20 to hotbar 6", "select 6", "eat", "select 0"), calls);
        assertFalse(rations.active());
        assertEquals(0, inventory.heldHotbarSlot());
    }

    @Test
    void itEatsOnlyOnceTheFoodIsInHand() {
        Rations rations = new Rations("Alice", session, inventory);

        run(rations, 1, true); // the swap is sent; the server hasn't answered

        assertEquals(List.of("swap 20 to hotbar 6", "select 6"), calls);
        run(rations, 2, true); // the answer, then the meal
        assertEquals("eat", calls.getLast());
    }

    @Test
    void aFedSoldierOrOneThatIsntFreeDoesntEat() {
        Rations rations = new Rations("Alice", session, inventory);

        food = 19;
        run(rations, 1, true);
        food = 10;
        run(rations, 1, false);

        assertEquals(List.of(), calls);
    }

    @Test
    void aMealInterruptedPutsTheWeaponBackInHandAndResumesAtOnce() {
        Rations rations = new Rations("Alice", session, inventory);

        run(rations, 3, true);
        assertTrue(rations.active());
        run(rations, 1, false); // a hostile came near

        assertEquals(List.of("swap 20 to hotbar 6", "select 6", "eat", "select 0"), calls);
        assertFalse(rations.active());

        run(rations, 3, true); // no backoff, and the food is still in the hotbar: no swap this time
        assertEquals(List.of("select 6", "eat"), calls.subList(4, 6));
    }

    @Test
    void aServerThatNeverAnswersTheSwapFailsLoudlyAndBacksOff() {
        silent = true;
        Rations rations = new Rations("Alice", session, inventory);

        run(rations, Rations.ANSWER_TICKS + 2, true);

        assertFalse(rations.active());
        assertEquals(List.of("swap 20 to hotbar 6", "select 6", "select 0"), calls);
        run(rations, 10, true);
        assertEquals(3, calls.size()); // backing off, not retrying
    }

    @Test
    void aMealThatNeverRaisesTheFoodLevelFailsLoudly() {
        noFood = true;
        Rations rations = new Rations("Alice", session, inventory);

        run(rations, Rations.EAT_TICKS + 3, true);

        assertFalse(rations.active());
        assertEquals(List.of("swap 20 to hotbar 6", "select 6", "eat", "select 0"), calls);
    }
}
