package io.github.jayessdeesea.minecraft.command;

import java.util.List;

/**
 * A squad's logistics status (doctrine's LOGSTAT, FM 4-0 classes of supply), summed from its
 * soldiers' LACE reports ({@link Condition}) - derived, never stored, so it can't drift from them.
 * docs/military-doctrine.md §4.
 *
 * <ul>
 *   <li>Class I, subsistence: soldiers {@code hungry}, below {@link Condition#REGENERATION_FOOD}, so
 *       not regenerating health;</li>
 *   <li>Class II, individual equipment: {@code armourEmpty} of {@code armourSlots} armour slots
 *       unfilled;</li>
 *   <li>Class V, ammunition: soldiers {@code unarmed} (nothing in hand) and {@code arrows} carried;</li>
 *   <li>Class IV, construction materials: {@code logs} and {@code stone} (cobblestone) carried;</li>
 *   <li>personnel: soldiers {@code wounded} below half health, and {@code deaths} so far.</li>
 * </ul>
 * Kit counts cover only the {@code kitReported} soldiers whose inventory has been reported.
 */
public record Logstat(int soldiers, int reporting, int hungry, int wounded, int deaths,
                      int kitReported, int unarmed, int armourEmpty, int armourSlots, int arrows, int logs, int stone) {

    public static Logstat of(List<SoldierStatus> squad) {
        int reporting = 0, hungry = 0, wounded = 0, deaths = 0;
        int kitReported = 0, unarmed = 0, armourEmpty = 0, arrows = 0, logs = 0, stone = 0;
        for (SoldierStatus soldier : squad) {
            if (soldier.condition().isEmpty()) {
                continue;
            }
            Condition c = soldier.condition().get();
            reporting++;
            hungry += c.food() < Condition.REGENERATION_FOOD ? 1 : 0;
            wounded += c.health() < Condition.MAX_HEALTH / 2 ? 1 : 0;
            deaths += c.deaths();
            if (c.kit().isPresent()) {
                Kit kit = c.kit().get();
                kitReported++;
                unarmed += kit.weapon().isEmpty() ? 1 : 0;
                armourEmpty += Kit.ARMOUR_SLOTS - kit.armour();
                arrows += kit.arrows();
                logs += kit.logs();
                stone += kit.stone();
            }
        }
        return new Logstat(squad.size(), reporting, hungry, wounded, deaths,
                kitReported, unarmed, armourEmpty, kitReported * Kit.ARMOUR_SLOTS, arrows, logs, stone);
    }
}
