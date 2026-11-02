package io.github.jayessdeesea.minecraft.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResupplyTest {

    private static final Position CHEST = new Position(10, 64, -20);
    private static final Order SLOT = new Order.MoveTo(0, 64, 0, 3, 3, Risk.LOW);
    private static final Kit ARMED = new Kit(Optional.of("iron_sword"), 0, 4);
    private static final Kit BARE = new Kit(Optional.empty(), 0, 0);

    private static SoldierStatus soldier(Report report, Optional<Kit> kit) {
        return new SoldierStatus("S", new Position(0, 64, 0), SLOT, report, Optional.of(new Condition(20, 20, 0, kit)));
    }

    @Test
    void soldiersShortOfKitGoToTheChestWhenTheSquadIsAtTheSupplyPoint() {
        List<SoldierStatus> squad = List.of(
                soldier(new Report.Moving(), Optional.of(ARMED)),
                soldier(new Report.Moving(), Optional.of(BARE)),
                soldier(new Report.InContact(1), Optional.of(BARE)),
                soldier(new Report.Moving(), Optional.empty()));

        List<Order> orders = Resupply.orders(squad, List.of(SLOT, SLOT, SLOT, SLOT), new Position(12, 64, -18), Optional.of(CHEST));

        assertEquals(List.of(SLOT, new Order.MoveTo(10.5, 64, -19.5, 2, 1, Risk.LOW), SLOT, SLOT), orders);
    }

    @Test
    void nobodyLeavesFormationWhenTheSquadWasSentElsewhereOrThereIsNoSupplyPoint() {
        List<SoldierStatus> squad = List.of(soldier(new Report.Moving(), Optional.of(BARE)));

        assertEquals(List.of(SLOT), Resupply.orders(squad, List.of(SLOT), new Position(80, 64, 0), Optional.of(CHEST)));
        assertEquals(List.of(SLOT), Resupply.orders(squad, List.of(SLOT), CHEST, Optional.empty()));
    }

    @Test
    void theSupplyPointIsConfiguredAsXyzOrNotAtAll() {
        assertEquals(Optional.of(new Position(1, 150, -3)), Resupply.supplyPoint(" 1, 150 ,-3"));
        assertEquals(Optional.empty(), Resupply.supplyPoint(""));
        assertThrows(IllegalArgumentException.class, () -> Resupply.supplyPoint("1,150"));
    }
}
